package team.kitemc.verifymc.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.db.UserDao;
import team.kitemc.verifymc.registration.RegistrationOutcome;
import team.kitemc.verifymc.service.AuthmeService;
import team.kitemc.verifymc.service.CaptchaService;
import team.kitemc.verifymc.service.DiscordService;
import team.kitemc.verifymc.service.QuestionnaireService;
import team.kitemc.verifymc.service.RegistrationApplicationService;
import team.kitemc.verifymc.service.VerifyCodeService;
import team.kitemc.verifymc.util.PluginScheduler;

public class RegistrationProcessingHandler implements HttpHandler {
    private static final long QUESTIONNAIRE_SUBMISSION_TTL_MS = 10 * 60 * 1000;

    private final Plugin plugin;
    private final VerifyCodeService codeService;
    private final UserDao userDao;
    private final AuthmeService authmeService;
    private final CaptchaService captchaService;
    private final QuestionnaireService questionnaireService;
    private final DiscordService discordService;
    private final RegistrationApplicationService registrationApplicationService;
    private final Map<String, QuestionnaireSubmissionRecord> questionnaireSubmissionStore;
    private final Supplier<List<String>> emailDomainWhitelistProvider;
    private final BiFunction<String, String, String> messageResolver;
    private final BiFunction<String, String, String> usernameRegexResolver;
    private final BiPredicate<String, String> usernameValidator;
    private final Function<String, Boolean> usernameCaseConflictChecker;
    private final Supplier<Boolean> usernameCaseSensitiveProvider;
    private final BiFunction<String, String, String> usernameNormalizer;
    private final Function<String, Boolean> emailValidator;
    private final Consumer<String> debugLogger;

    public RegistrationProcessingHandler(
            Plugin plugin,
            VerifyCodeService codeService,
            UserDao userDao,
            AuthmeService authmeService,
            CaptchaService captchaService,
            QuestionnaireService questionnaireService,
            DiscordService discordService,
            RegistrationApplicationService registrationApplicationService,
            Map<String, QuestionnaireSubmissionRecord> questionnaireSubmissionStore,
            Supplier<List<String>> emailDomainWhitelistProvider,
            BiFunction<String, String, String> messageResolver,
            BiFunction<String, String, String> usernameRegexResolver,
            BiPredicate<String, String> usernameValidator,
            Function<String, Boolean> usernameCaseConflictChecker,
            Supplier<Boolean> usernameCaseSensitiveProvider,
            BiFunction<String, String, String> usernameNormalizer,
            Function<String, Boolean> emailValidator,
            Consumer<String> debugLogger
    ) {
        this.plugin = plugin;
        this.codeService = codeService;
        this.userDao = userDao;
        this.authmeService = authmeService;
        this.captchaService = captchaService;
        this.questionnaireService = questionnaireService;
        this.discordService = discordService;
        this.registrationApplicationService = registrationApplicationService;
        this.questionnaireSubmissionStore = questionnaireSubmissionStore;
        this.emailDomainWhitelistProvider = emailDomainWhitelistProvider;
        this.messageResolver = messageResolver;
        this.usernameRegexResolver = usernameRegexResolver;
        this.usernameValidator = usernameValidator;
        this.usernameCaseConflictChecker = usernameCaseConflictChecker;
        this.usernameCaseSensitiveProvider = usernameCaseSensitiveProvider;
        this.usernameNormalizer = usernameNormalizer;
        this.emailValidator = emailValidator;
        this.debugLogger = debugLogger;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestId = java.util.UUID.randomUUID().toString();
        logRegistrationStage(requestId, "start", null);

        if (!WebResponseHelper.requireMethod(exchange, "POST")) {
            return;
        }

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    messageResolver.apply("error.invalid_json", "zh")), 400);
            return;
        }
        RegistrationRequest request = RegistrationRequest.fromJson(req, usernameNormalizer);

        RegistrationValidationResult basicResult = validateBasicInput(request, requestId);
        if (!basicResult.passed()) {
            reject(exchange, basicResult, request.language());
            return;
        }

        QuestionnaireSubmissionRecord questionnaireSubmissionRecord = validateQuestionnaireSubmission(exchange, request, requestId);
        if (questionnaireSubmissionRecord == null && questionnaireService.isEnabled()) {
            return;
        }

        RegistrationValidationResult verificationResult = validateVerificationMethod(request, requestId);
        if (!verificationResult.passed()) {
            reject(exchange, verificationResult, request.language());
            return;
        }

        RegistrationValidationResult discordResult = validateDiscordRequirement(request, requestId);
        if (!discordResult.passed()) {
            reject(exchange, discordResult, request.language());
            return;
        }

        JSONObject response = executeRegistration(request, questionnaireSubmissionRecord, requestId);
        WebResponseHelper.sendJson(exchange, response);
    }

    private RegistrationValidationResult validateBasicInput(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_basic_input", null);

        // 1. Null/empty checks first to prevent NPE downstream
        if (request.normalizedUsername() == null || request.normalizedUsername().trim().isEmpty()) {
            return RegistrationValidationResult.reject("register.invalid_username");
        }
        if (!emailValidator.apply(request.email())) {
            return RegistrationValidationResult.reject("register.invalid_email");
        }

        // 2. Format validation
        if (!usernameValidator.test(request.normalizedUsername(), request.platform())) {
            String usernameRegex = usernameRegexResolver.apply(request.normalizedUsername(), request.platform());
            return RegistrationValidationResult.reject("username.invalid", new JSONObject().put("regex", usernameRegex));
        }

        if (authmeService.isAuthmeEnabled() && authmeService.isPasswordRequired()) {
            if (request.password() == null || request.password().trim().isEmpty()) {
                return RegistrationValidationResult.reject("register.password_required");
            }
            if (!authmeService.isValidPassword(request.password())) {
                String passwordRegex = plugin.getConfig().getString("authme.password_regex", "^[a-zA-Z0-9_]{8,26}$");
                return RegistrationValidationResult.reject("register.invalid_password", new JSONObject().put("regex", passwordRegex));
            }
        }

        // 3. Email policy checks
        if (plugin.getConfig().getBoolean("enable_email_alias_limit", false) && request.email().contains("+")) {
            return RegistrationValidationResult.reject("register.alias_not_allowed");
        }
        if (plugin.getConfig().getBoolean("enable_email_domain_whitelist", true)) {
            String domain = request.email().contains("@") ? request.email().substring(request.email().indexOf('@') + 1) : "";
            if (!emailDomainWhitelistProvider.get().contains(domain)) {
                return RegistrationValidationResult.reject("register.domain_not_allowed");
            }
        }

        // 4. Uniqueness checks (require DB access)
        boolean caseSensitive = usernameCaseSensitiveProvider.get();
        var existingUser = userDao.getUserByUsername(request.normalizedUsername(), caseSensitive);
        if (existingUser != null) {
            return RegistrationValidationResult.reject("register.username_exists");
        }
        if (usernameCaseConflictChecker.apply(request.normalizedUsername())) {
            return RegistrationValidationResult.reject("username.case_conflict");
        }

        int maxAccounts = plugin.getConfig().getInt("max_accounts_per_email", 2);
        int emailCount = userDao.countUsersByEmail(request.email());
        if (emailCount >= maxAccounts) {
            return RegistrationValidationResult.reject("register.email_limit");
        }
        return RegistrationValidationResult.pass();
    }

    private QuestionnaireSubmissionRecord validateQuestionnaireSubmission(HttpExchange exchange, RegistrationRequest request, String requestId) throws IOException {
        logRegistrationStage(requestId, "validate_questionnaire_submission", null);
        if (!questionnaireService.isEnabled()) {
            return null;
        }

        JSONObject questionnaire = request.questionnaire();
        if (questionnaire == null) {
            reject(exchange, RegistrationValidationResult.reject("register.questionnaire_required"), request.language());
            return null;
        }

        String questionnaireToken = questionnaire.optString("token", "");
        long submittedAt = questionnaire.optLong("submittedAt", questionnaire.optLong("submitted_at", 0L));
        long expiresAt = questionnaire.optLong("expiresAt", questionnaire.optLong("expires_at", 0L));
        JSONObject answers = questionnaire.optJSONObject("answers");

        if (questionnaireToken.isEmpty() || answers == null) {
            reject(exchange, RegistrationValidationResult.reject("register.questionnaire_required"), request.language());
            return null;
        }

        QuestionnaireSubmissionRecord record = questionnaireSubmissionStore.remove(questionnaireToken);
        if (record == null) {
            reject(exchange, RegistrationValidationResult.reject("register.questionnaire_missing"), request.language());
            return null;
        }

        if (record.isExpired() || System.currentTimeMillis() > expiresAt || submittedAt <= 0 || expiresAt <= submittedAt) {
            reject(exchange, RegistrationValidationResult.reject("register.questionnaire_expired"), request.language());
            return null;
        }

        if (!record.answers().similar(answers) || record.submittedAt() != submittedAt || record.expiresAt() != expiresAt) {
            reject(exchange, RegistrationValidationResult.reject("register.questionnaire_invalid"), request.language());
            return null;
        }

        if (!record.passed() && !record.manualReviewRequired()) {
            reject(exchange, RegistrationValidationResult.reject("register.questionnaire_required"), request.language());
            return null;
        }
        return record;
    }

    private RegistrationValidationResult validateVerificationMethod(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_verification_method", null);
        List<String> authMethods = plugin.getConfig().getStringList("auth_methods");
        boolean useCaptcha = authMethods.contains("captcha");
        boolean useEmail = authMethods.contains("email");

        // If no auth methods configured, skip verification
        if (!useCaptcha && !useEmail) {
            return RegistrationValidationResult.pass();
        }

        if (useCaptcha) {
            if (request.captchaToken().isEmpty() || request.captchaAnswer().isEmpty()) {
                return RegistrationValidationResult.reject("captcha.required");
            }
            if (!captchaService.validateCaptcha(request.captchaToken(), request.captchaAnswer())) {
                return RegistrationValidationResult.reject("captcha.invalid");
            }
        }

        if (useEmail) {
            if (!codeService.checkCode(request.email(), request.code())) {
                return RegistrationValidationResult.reject("verify.wrong_code");
            }
        }

        return RegistrationValidationResult.pass();
    }

    private RegistrationValidationResult validateDiscordRequirement(RegistrationRequest request, String requestId) {
        logRegistrationStage(requestId, "validate_discord_requirement", null);
        if (discordService.isRequired() && !discordService.isLinked(request.normalizedUsername())) {
            return RegistrationValidationResult.reject("discord.required", new JSONObject().put("discordRequired", true));
        }
        return RegistrationValidationResult.pass();
    }

    private JSONObject executeRegistration(RegistrationRequest request, QuestionnaireSubmissionRecord submissionRecord, String requestId) {
        logRegistrationStage(requestId, "execute_registration", null);
        boolean manualReviewRequired = submissionRecord != null && submissionRecord.manualReviewRequired();
        boolean questionnairePassed = submissionRecord != null && submissionRecord.passed();
        boolean scoringServiceUnavailable = submissionRecord != null && submissionRecord.scoringServiceUnavailable();
        boolean registerAutoApprove = plugin.getConfig().getBoolean("register.auto_approve", false);

        RegistrationApplicationService.RegistrationDecision preDecision =
                registrationApplicationService.resolveDecision(true, manualReviewRequired, questionnairePassed, registerAutoApprove, scoringServiceUnavailable);
        String status = registrationApplicationService.resolveStatus(preDecision);

        Integer questionnaireScore = submissionRecord != null ? submissionRecord.score() : null;
        Boolean questionnairePassedValue = submissionRecord != null ? submissionRecord.passed() : null;
        String questionnaireReviewSummary = submissionRecord != null ? buildQuestionnaireReviewSummary(submissionRecord.details()) : null;
        Long questionnaireScoredAt = submissionRecord != null ? submissionRecord.submittedAt() : null;

        boolean ok;
        if (request.password() != null && !request.password().trim().isEmpty()) {
            ok = userDao.registerUser(request.normalizedUsername(), request.email(), status, request.password(),
                    questionnaireScore, questionnairePassedValue, questionnaireReviewSummary, questionnaireScoredAt);
        } else {
            ok = userDao.registerUser(request.normalizedUsername(), request.email(), status,
                    questionnaireScore, questionnairePassedValue, questionnaireReviewSummary, questionnaireScoredAt);
        }
        if (ok && request.language() != null && !request.language().isEmpty()) {
            userDao.setUserLanguage(request.normalizedUsername(), request.language());
        }

        RegistrationApplicationService.RegistrationDecision decision =
                registrationApplicationService.resolveDecision(ok, manualReviewRequired, questionnairePassed, registerAutoApprove, scoringServiceUnavailable);
        if (decision.outcome() == RegistrationOutcome.SUCCESS_WHITELISTED) {
            PluginScheduler.runGlobal(plugin, () ->
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "whitelist add " + request.normalizedUsername()));
            if (authmeService.isAuthmeEnabled() && request.password() != null && !request.password().trim().isEmpty()) {
                authmeService.registerToAuthme(request.normalizedUsername(), request.password());
            }
        }

        return registrationApplicationService.buildRegistrationResponse(decision, ok, key -> messageResolver.apply(key, request.language()));
    }

    private String buildQuestionnaireReviewSummary(JSONArray details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (int i = 0; i < details.length(); i++) {
            JSONObject detail = details.optJSONObject(i);
            if (detail == null || !"text".equalsIgnoreCase(detail.optString("type", ""))) {
                continue;
            }
            int questionId = detail.optInt("questionId", detail.optInt("question_id", -1));
            int score = detail.optInt("score", 0);
            int maxScore = detail.optInt("maxScore", detail.optInt("max_score", 0));
            String reason = detail.optString("reason", "").trim();
            if (reason.isEmpty()) {
                reason = "N/A";
            }
            parts.add("Q" + questionId + "(" + score + "/" + maxScore + "): " + reason);
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    private void reject(HttpExchange exchange, RegistrationValidationResult result, String language) throws IOException {
        String message = messageResolver.apply(result.messageKey(), language);
        JSONObject responseFields = result.responseFields();
        if (responseFields != null && responseFields.has("regex")) {
            message = message.replace("{regex}", responseFields.optString("regex", ""));
        }
        JSONObject resp = ApiResponseFactory.failure(message);
        if (responseFields != null) {
            for (String key : responseFields.keySet()) {
                if (!"regex".equals(key)) {
                    resp.put(key, responseFields.get(key));
                }
            }
        }
        WebResponseHelper.sendJson(exchange, resp);
    }

    private void logRegistrationStage(String requestId, String stage, JSONObject extra) {
        JSONObject payload = new JSONObject();
        payload.put("requestId", requestId);
        payload.put("stage", stage);
        if (extra != null) {
            payload.put("extra", extra);
        }
        debugLogger.accept("registration_stage=" + payload);
    }

    public record QuestionnaireSubmissionRecord(
            boolean passed,
            int score,
            int passScore,
            JSONArray details,
            boolean manualReviewRequired,
            boolean scoringServiceUnavailable,
            JSONObject answers,
            long submittedAt,
            long expiresAt
    ) {
        public static QuestionnaireSubmissionRecord of(boolean passed, int score, int passScore, JSONArray details, boolean manualReviewRequired, boolean scoringServiceUnavailable, JSONObject answers, long submittedAt) {
            return new QuestionnaireSubmissionRecord(passed, score, passScore, details, manualReviewRequired, scoringServiceUnavailable, answers, submittedAt, submittedAt + QUESTIONNAIRE_SUBMISSION_TTL_MS);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
