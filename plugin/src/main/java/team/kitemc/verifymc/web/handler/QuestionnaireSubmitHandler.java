package team.kitemc.verifymc.web.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import team.kitemc.verifymc.core.PluginContext;
import team.kitemc.verifymc.service.QuestionnaireService;
import team.kitemc.verifymc.web.ApiResponseFactory;
import team.kitemc.verifymc.web.RegistrationProcessingHandler;
import team.kitemc.verifymc.web.WebResponseHelper;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuestionnaireSubmitHandler implements HttpHandler {
    private final PluginContext ctx;
    private final ConcurrentHashMap<String, RegistrationProcessingHandler.QuestionnaireSubmissionRecord> store;

    public QuestionnaireSubmitHandler(PluginContext ctx,
                                      ConcurrentHashMap<String, RegistrationProcessingHandler.QuestionnaireSubmissionRecord> store) {
        this.ctx = ctx;
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!WebResponseHelper.requireMethod(exchange, "POST")) return;

        JSONObject req;
        try {
            req = WebResponseHelper.readJson(exchange);
        } catch (JSONException e) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("error.invalid_json", "zh")), 400);
            return;
        }
        String language = req.optString("language", "zh");

        if (!ctx.getQuestionnaireService().isEnabled()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("questionnaire.not_enabled", language)));
            return;
        }

        JSONObject answers = req.optJSONObject("answers");
        if (answers == null || answers.isEmpty()) {
            WebResponseHelper.sendJson(exchange, ApiResponseFactory.failure(
                    ctx.getMessage("register.questionnaire_required", language)));
            return;
        }

        QuestionnaireService.QuestionnaireResult result = ctx.getQuestionnaireService().scoreAnswers(answers, language);
        int score = result.getScore();
        int passScore = result.getPassScore();
        boolean passed = result.isPassed();
        boolean manualReviewRequired = result.isManualReviewRequired();
        boolean scoringServiceUnavailable = result.isScoringServiceUnavailable();
        JSONArray details = new JSONArray();
        for (QuestionnaireService.QuestionScoreDetail detail : result.getDetails()) {
            details.put(detail.toJson());
        }

        String token = UUID.randomUUID().toString();
        long submittedAt = System.currentTimeMillis();

        RegistrationProcessingHandler.QuestionnaireSubmissionRecord record =
                RegistrationProcessingHandler.QuestionnaireSubmissionRecord.of(
                        passed, score, passScore, details, manualReviewRequired,
                        scoringServiceUnavailable, answers, submittedAt);
        store.put(token, record);

        JSONObject resp = new JSONObject();
        resp.put("success", true);
        resp.put("token", token);
        resp.put("score", score);
        resp.put("passScore", passScore);
        resp.put("passed", passed);
        resp.put("manualReviewRequired", manualReviewRequired);
        resp.put("submittedAt", submittedAt);
        resp.put("expiresAt", record.expiresAt());
        if (details != null && !details.isEmpty()) {
            resp.put("details", details);
        }
        WebResponseHelper.sendJson(exchange, resp);
    }
}
