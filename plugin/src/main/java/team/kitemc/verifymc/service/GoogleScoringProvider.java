package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.http.HttpRequest;

public class GoogleScoringProvider extends OpenAICompatibleScoringProvider {
    public GoogleScoringProvider(Plugin plugin, LlmScoringConfig config) {
        super(plugin, config);
    }

    @Override
    protected String buildRequestUrl() {
        String base = config.getApiBase() != null ? config.getApiBase().trim() : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith(":generateContent") || base.contains(":generateContent?")) {
            return appendApiKey(base);
        }
        return appendApiKey(base + "/models/" + config.getModel() + ":generateContent");
    }

    @Override
    protected HttpRequest.Builder applyDefaultHeaders(HttpRequest.Builder builder, String requestId) {
        return builder
            .header("X-Goog-Api-Key", config.getApiKey())
            .header("X-Request-ID", requestId)
            .header("Content-Type", "application/json");
    }

    @Override
    protected JSONObject buildPayload(EssayScoringRequest request) {
        JSONObject payload = new JSONObject();

        JSONArray contents = new JSONArray();
        contents.put(new JSONObject()
            .put("role", "user")
            .put("parts", new JSONArray()
                .put(new JSONObject().put("text", sanitizePrompt(config.getSystemPrompt(), 4000)))
                .put(new JSONObject().put("text", buildUserPrompt(request))
                )));
        payload.put("contents", contents);

        payload.put("generationConfig", new JSONObject().put("temperature", 0.0D));
        return payload;
    }

    @Override
    protected String extractResponseText(JSONObject json) throws IOException {
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IOException("No candidates returned by Gemini");
        }

        JSONObject first = candidates.getJSONObject(0);
        JSONObject content = first.optJSONObject("content");
        if (content == null) {
            throw new IOException("Missing content in Gemini response");
        }

        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IOException("Missing parts in Gemini response");
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) {
                continue;
            }
            String piece = part.optString("text", "");
            if (!piece.isEmpty()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(piece);
            }
        }

        return text.toString().trim();
    }

    private String appendApiKey(String url) {
        if (url.contains("key=")) {
            return url;
        }
        String delimiter = url.contains("?") ? "&" : "?";
        return url + delimiter + "key=" + config.getApiKey();
    }
}
