package team.kitemc.verifymc.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Questionnaire service for handling registration questionnaire
 * Supports single/multiple choice/text questions with scoring system
 */
public class QuestionnaireService {
    private final Plugin plugin;
    private final boolean debug;
    private FileConfiguration questionnaireConfig;
    private final EssayScoringService essayScoringService;
    private final int llmDefaultMaxScore;
    private final String llmScoringRule;

    public QuestionnaireService(Plugin plugin) {
        this.plugin = plugin;
        this.debug = plugin.getConfig().getBoolean("debug", false);
        this.essayScoringService = buildScoringService();
        this.llmDefaultMaxScore = Math.max(1, plugin.getConfig().getInt("llm.max_score", 20));
        this.llmScoringRule = plugin.getConfig().getString("llm.scoring_rule", "Evaluate relevance, detail and rule-awareness.");
        loadQuestionnaireConfig();
    }

    private EssayScoringService buildScoringService() {
        String provider = plugin.getConfig().getString("llm.provider", "deepseek").toLowerCase(Locale.ROOT);
        String defaultApiBase = "google".equals(provider)
            ? "https://generativelanguage.googleapis.com/v1beta"
            : "https://api.deepseek.com/v1";
        String defaultModel = "google".equals(provider) ? "gemini-1.5-flash" : "deepseek-chat";

        if ("google".equals(provider)) {
            plugin.getLogger().info("[VerifyMC] llm.provider=google requires Gemini-style config: api_base=https://generativelanguage.googleapis.com/v1beta, model=<gemini-model>, api_key=<google-ai-api-key>");
        }

        OpenAICompatibleScoringProvider.LlmScoringConfig config = new OpenAICompatibleScoringProvider.LlmScoringConfig(
            provider,
            plugin.getConfig().getString("llm.api_base", defaultApiBase),
            plugin.getConfig().getString("llm.api_key", ""),
            plugin.getConfig().getString("llm.model", defaultModel),
            plugin.getConfig().getInt("llm.timeout", 10000),
            plugin.getConfig().getInt("llm.retry", 1),
            plugin.getConfig().getString("llm.system_prompt", "You are an impartial questionnaire scorer. Return JSON only."),
            plugin.getConfig().getString("llm.score_format", "{\"score\": number, \"reason\": string, \"confidence\": number}"),
            plugin.getConfig().getInt("llm.max_concurrency", 4),
            plugin.getConfig().getInt("llm.acquire_timeout", 1500),
            plugin.getConfig().getInt("llm.retry_backoff_base", 300),
            plugin.getConfig().getInt("llm.retry_backoff_max", 5000),
            plugin.getConfig().getInt("llm.circuit_breaker.failure_threshold", 5),
            plugin.getConfig().getInt("llm.circuit_breaker.open_ms", 30000),
            plugin.getConfig().getInt("llm.input_max_length", 2000)
        );

        if ("google".equals(provider)) {
            return new GoogleScoringProvider(plugin, config);
        }
        if ("deepseek".equals(provider)) {
            return new DeepSeekScoringProvider(plugin, config);
        }

        plugin.getLogger().warning("[VerifyMC] Unknown llm.provider: " + provider + ", fallback to deepseek");
        return new DeepSeekScoringProvider(plugin, config);
    }

    /**
     * Load questionnaire configuration from file
     */
    private void loadQuestionnaireConfig() {
        File configFile = new File(plugin.getDataFolder(), "questionnaire.yml");
        if (!configFile.exists()) {
            // Create default questionnaire config
            createDefaultQuestionnaireConfig(configFile);
        }
        questionnaireConfig = YamlConfiguration.loadConfiguration(configFile);
        debugLog("Questionnaire configuration loaded");
    }

    /**
     * Create default questionnaire configuration file
     * @param configFile The config file to create
     */
    private void createDefaultQuestionnaireConfig(File configFile) {
        try {
            InputStream is = plugin.getResource("questionnaire.yml");
            if (is != null) {
                questionnaireConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(is, StandardCharsets.UTF_8));
                questionnaireConfig.save(configFile);
            } else {
                questionnaireConfig = new YamlConfiguration();
                questionnaireConfig.set("questions", Collections.emptyList());
                questionnaireConfig.save(configFile);
            }
            debugLog("Created default questionnaire config");
        } catch (Exception e) {
            debugLog("Failed to create questionnaire config: " + e.getMessage());
        }
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("questionnaire.enabled", false);
    }

    public int getPassScore() {
        return plugin.getConfig().getInt("questionnaire.pass_score", 60);
    }

    public boolean isAutoApproveOnPass() {
        return plugin.getConfig().getBoolean("questionnaire.auto_approve_on_pass", false);
    }

    public JSONObject getQuestionnaire(String language) {
        JSONObject result = new JSONObject();
        result.put("enabled", isEnabled());
        result.put("pass_score", getPassScore());

        if (!isEnabled() || questionnaireConfig == null) {
            result.put("questions", new JSONArray());
            return result;
        }

        List<?> questionsList = questionnaireConfig.getList("questions");
        JSONArray questionsArray = new JSONArray();

        if (questionsList != null) {
            for (Object qObj : questionsList) {
                if (qObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> questionMap = (Map<String, Object>) qObj;
                    JSONObject question = new JSONObject();

                    question.put("id", questionMap.get("id"));
                    String questionText = "zh".equals(language) ?
                        (String) questionMap.get("question_zh") :
                        (String) questionMap.get("question_en");
                    if (questionText == null) {
                        questionText = (String) questionMap.getOrDefault("question", "");
                    }
                    question.put("question", questionText);
                    String type = String.valueOf(questionMap.getOrDefault("type", "single_choice"));
                    question.put("type", type);
                    question.put("required", Boolean.TRUE.equals(questionMap.get("required")));

                    JSONObject inputMeta = new JSONObject();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> inputMap = (Map<String, Object>) questionMap.get("input");
                    if (inputMap != null) {
                        for (Map.Entry<String, Object> entry : inputMap.entrySet()) {
                            inputMeta.put(entry.getKey(), entry.getValue());
                        }
                    }

                    if ("text".equals(type) && inputMap != null) {
                        String placeholder = "zh".equals(language)
                            ? String.valueOf(inputMap.getOrDefault("placeholder_zh", ""))
                            : String.valueOf(inputMap.getOrDefault("placeholder_en", ""));
                        inputMeta.put("placeholder", placeholder);
                    }

                    question.put("input", inputMeta);

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> optionsList = (List<Map<String, Object>>) questionMap.get("options");
                    JSONArray optionsArray = new JSONArray();

                    if (optionsList != null) {
                        int optionIndex = 0;
                        for (Map<String, Object> optMap : optionsList) {
                            JSONObject option = new JSONObject();
                            option.put("id", optionIndex++);
                            String optText = "zh".equals(language)
                                ? (String) optMap.get("text_zh")
                                : (String) optMap.get("text_en");
                            if (optText == null) {
                                optText = (String) optMap.getOrDefault("text", "");
                            }
                            option.put("text", optText);
                            optionsArray.put(option);
                        }
                    }
                    question.put("options", optionsArray);
                    questionsArray.put(question);
                }
            }
        }

        result.put("questions", questionsArray);
        return result;
    }

    public QuestionnaireResult evaluateAnswers(Map<Integer, QuestionAnswer> answers) {
        if (!isEnabled() || questionnaireConfig == null) {
            return new QuestionnaireResult(true, 100, getPassScore(), Collections.emptyList());
        }

        int totalScore = 0;
        List<QuestionScoreDetail> details = new ArrayList<>();
        List<?> questionsList = questionnaireConfig.getList("questions");

        if (questionsList != null) {
            for (Object qObj : questionsList) {
                if (!(qObj instanceof Map)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> questionMap = (Map<String, Object>) qObj;
                int questionId = ((Number) questionMap.get("id")).intValue();
                String questionType = String.valueOf(questionMap.getOrDefault("type", "single_choice"));
                QuestionAnswer answer = answers.get(questionId);

                if (answer == null) {
                    details.add(new QuestionScoreDetail(questionId, questionType, 0, resolveMaxScore(questionMap), "No answer submitted", 0.0D, false, "local", "", "", 0L, 0));
                    continue;
                }

                if ("text".equalsIgnoreCase(questionType)) {
                    QuestionScoreDetail detail = scoreTextQuestion(questionMap, answer, questionId);
                    totalScore += detail.getScore();
                    details.add(detail);
                } else {
                    QuestionScoreDetail detail = scoreChoiceQuestion(questionMap, answer, questionId);
                    totalScore += detail.getScore();
                    details.add(detail);
                }
            }
        }

        int passScore = getPassScore();
        boolean passed = totalScore >= passScore;
        debugLog("Questionnaire evaluation: score=" + totalScore + ", passScore=" + passScore + ", passed=" + passed);
        return new QuestionnaireResult(passed, totalScore, passScore, details);
    }

    private QuestionScoreDetail scoreChoiceQuestion(Map<String, Object> questionMap, QuestionAnswer answer, int questionId) {
        int questionScore = 0;
        int maxScore = resolveMaxScore(questionMap);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsList = (List<Map<String, Object>>) questionMap.get("options");
        if (optionsList != null) {
            for (int optionId : answer.getSelectedOptionIds()) {
                if (optionId >= 0 && optionId < optionsList.size()) {
                    Object scoreObj = optionsList.get(optionId).get("score");
                    if (scoreObj instanceof Number) {
                        questionScore += ((Number) scoreObj).intValue();
                    }
                }
            }
        }

        questionScore = Math.max(0, Math.min(maxScore, questionScore));
        return new QuestionScoreDetail(questionId, answer.getType(), questionScore, maxScore, "Locally scored", 1.0D, false, "local", "", "", 0L, 0);
    }

    private QuestionScoreDetail scoreTextQuestion(Map<String, Object> questionMap, QuestionAnswer answer, int questionId) {
        int maxScore = resolveMaxScore(questionMap);
        String questionText = resolveQuestionText(questionMap);
        String scoringRule = resolveScoringRule(questionMap);

        EssayScoringService.EssayScoringRequest request = new EssayScoringService.EssayScoringRequest(
            questionId,
            questionText,
            answer.getTextAnswer(),
            scoringRule,
            maxScore
        );

        EssayScoringService.EssayScoringResult result = essayScoringService.score(request);
        return new QuestionScoreDetail(
            questionId,
            answer.getType(),
            result.getScore(),
            maxScore,
            result.getReason(),
            result.getConfidence(),
            result.isManualReview(),
            result.getProvider(),
            result.getModel(),
            result.getRequestId(),
            result.getLatencyMs(),
            result.getRetryCount()
        );
    }


    private String resolveQuestionText(Map<String, Object> questionMap) {
        String zh = String.valueOf(questionMap.getOrDefault("question_zh", "")).trim();
        String en = String.valueOf(questionMap.getOrDefault("question_en", "")).trim();
        if (!zh.isEmpty() && !en.isEmpty()) {
            return "[ZH] " + zh + "\n[EN] " + en;
        }
        return !zh.isEmpty() ? zh : en;
    }

    private String resolveScoringRule(Map<String, Object> questionMap) {
        Object localRule = questionMap.get("scoring_rule");
        if (localRule instanceof String && !((String) localRule).trim().isEmpty()) {
            return ((String) localRule).trim();
        }
        return llmScoringRule;
    }

    private int resolveMaxScore(Map<String, Object> questionMap) {
        Object configured = questionMap.get("max_score");
        if (configured instanceof Number) {
            return Math.max(1, ((Number) configured).intValue());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsList = (List<Map<String, Object>>) questionMap.get("options");
        if (optionsList != null && !optionsList.isEmpty()) {
            int total = 0;
            for (Map<String, Object> option : optionsList) {
                Object scoreObj = option.get("score");
                if (scoreObj instanceof Number) {
                    total += ((Number) scoreObj).intValue();
                }
            }
            return Math.max(1, total);
        }

        return llmDefaultMaxScore;
    }

    public void reload() {
        loadQuestionnaireConfig();
    }

    private void debugLog(String msg) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] QuestionnaireService: " + msg);
        }
    }

    public static class QuestionAnswer {
        private final String type;
        private final List<Integer> selectedOptionIds;
        private final String textAnswer;

        public QuestionAnswer(String type, List<Integer> selectedOptionIds, String textAnswer) {
            this.type = type != null ? type : "";
            this.selectedOptionIds = selectedOptionIds != null ? new ArrayList<>(selectedOptionIds) : new ArrayList<>();
            this.textAnswer = textAnswer != null ? textAnswer : "";
        }

        public String getType() { return type; }

        public List<Integer> getSelectedOptionIds() { return Collections.unmodifiableList(selectedOptionIds); }

        public String getTextAnswer() { return textAnswer; }
    }

    public static class QuestionScoreDetail {
        private final int questionId;
        private final String type;
        private final int score;
        private final int maxScore;
        private final String reason;
        private final double confidence;
        private final boolean manualReview;
        private final String provider;
        private final String model;
        private final String requestId;
        private final long latencyMs;
        private final int retryCount;

        public QuestionScoreDetail(int questionId, String type, int score, int maxScore, String reason, double confidence, boolean manualReview,
                                   String provider, String model, String requestId, long latencyMs, int retryCount) {
            this.questionId = questionId;
            this.type = type;
            this.score = score;
            this.maxScore = maxScore;
            this.reason = reason;
            this.confidence = confidence;
            this.manualReview = manualReview;
            this.provider = provider;
            this.model = model;
            this.requestId = requestId;
            this.latencyMs = latencyMs;
            this.retryCount = retryCount;
        }

        public int getQuestionId() { return questionId; }
        public int getScore() { return score; }
        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("question_id", questionId);
            json.put("type", type);
            json.put("score", score);
            json.put("max_score", maxScore);
            json.put("reason", reason);
            json.put("confidence", confidence);
            json.put("manual_review", manualReview);
            json.put("provider", provider);
            json.put("model", model);
            json.put("request_id", requestId);
            json.put("latency_ms", latencyMs);
            json.put("retry_count", retryCount);
            return json;
        }
    }

    public static class QuestionnaireResult {
        private final boolean passed;
        private final int score;
        private final int passScore;
        private final List<QuestionScoreDetail> details;

        public QuestionnaireResult(boolean passed, int score, int passScore, List<QuestionScoreDetail> details) {
            this.passed = passed;
            this.score = score;
            this.passScore = passScore;
            this.details = details != null ? new ArrayList<>(details) : new ArrayList<>();
        }

        public boolean isPassed() { return passed; }
        public int getScore() { return score; }
        public int getPassScore() { return passScore; }
        public List<QuestionScoreDetail> getDetails() { return Collections.unmodifiableList(details); }

        public boolean isManualReviewRequired() {
            for (QuestionScoreDetail detail : details) {
                if (detail.manualReview) {
                    return true;
                }
            }
            return false;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("passed", passed);
            json.put("score", score);
            json.put("pass_score", passScore);
            json.put("manual_review_required", isManualReviewRequired());

            JSONArray detailArray = new JSONArray();
            for (QuestionScoreDetail detail : details) {
                detailArray.put(detail.toJson());
            }
            json.put("details", detailArray);
            return json;
        }
    }
}
