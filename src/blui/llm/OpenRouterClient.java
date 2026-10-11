package blui.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zero-dependency client for OpenRouter AI API.
 * Defaults to nvidia/nemotron-3.5-lightning:free with high/max reasoning effort support.
 */
public final class OpenRouterClient implements LlmProvider {

    public static final String DEFAULT_MODEL = "nvidia/nemotron-3.5-lightning:free";
    public static final String DEFAULT_REASONING_EFFORT = "high";
    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final String reasoningEffort;
    private final HttpClient httpClient;
    private final Duration timeout;

    public OpenRouterClient() {
        this(resolveEnv("OPENROUTER_API_KEY"), resolveDefaultModel(), resolveDefaultReasoningEffort(), Duration.ofMinutes(2));
    }

    public OpenRouterClient(String apiKey) {
        this(apiKey, resolveDefaultModel(), resolveDefaultReasoningEffort(), Duration.ofMinutes(2));
    }

    public OpenRouterClient(String apiKey, String model) {
        this(apiKey, model, resolveDefaultReasoningEffort(), Duration.ofMinutes(2));
    }

    public OpenRouterClient(String apiKey, String model, String reasoningEffort, Duration timeout) {
        this.apiKey = blui.app.EnvConfig.cleanSecret(apiKey);
        String m = model != null && !model.isBlank() ? blui.app.EnvConfig.cleanSecret(model) : DEFAULT_MODEL;
        if (m.isBlank() || blui.app.EnvConfig.isCorruptModel(m, this.apiKey)) {
            m = DEFAULT_MODEL;
        }
        this.model = m;
        this.reasoningEffort = reasoningEffort != null && !reasoningEffort.isBlank() ? reasoningEffort.trim() : DEFAULT_REASONING_EFFORT;
        this.timeout = timeout != null ? timeout : Duration.ofMinutes(2);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static String resolveEnv(String key) {
        String val = System.getenv(key);
        return val != null ? blui.app.EnvConfig.cleanSecret(val) : null;
    }

    private static String resolveDefaultModel() {
        String env = System.getenv("OPENROUTER_MODEL");
        if (env == null || env.isBlank()) {
            env = blui.app.EnvConfig.get("OPENROUTER_MODEL");
        }
        String apiKey = System.getenv("OPENROUTER_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = blui.app.EnvConfig.get("OPENROUTER_API_KEY");
        }
        if (env != null && (env.isBlank() || blui.app.EnvConfig.isCorruptModel(env, apiKey))) {
            return DEFAULT_MODEL;
        }
        return (env != null && !env.isBlank()) ? blui.app.EnvConfig.cleanSecret(env) : DEFAULT_MODEL;
    }

    private static String resolveDefaultReasoningEffort() {
        String env = System.getenv("OPENROUTER_REASONING_EFFORT");
        return (env != null && !env.isBlank()) ? env.trim() : DEFAULT_REASONING_EFFORT;
    }

    @Override
    public String modelName() {
        return model;
    }

    public String reasoningEffort() {
        return reasoningEffort;
    }

    @Override
    public LlmResponse generate(List<Message> conversation) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENROUTER_API_KEY environment variable is not set. Get an API key at https://openrouter.ai/keys");
        }

        String requestJson = buildRequestBody(conversation);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("HTTP-Referer", "https://github.com/Nikhil-Vzo/blui")
                .header("X-Title", "Blui Agent Runtime")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson));

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = MinimalJson.extractString(response.body(), "message");
            if (errorMsg == null) {
                errorMsg = MinimalJson.extractString(response.body(), "error");
            }
            throw new RuntimeException("OpenRouter API error (HTTP " + response.statusCode() + "): "
                    + (errorMsg != null ? errorMsg : response.body()));
        }

        return parseResponse(response.body());
    }

    private String buildRequestBody(List<Message> conversation) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":\"").append(MinimalJson.escape(model)).append("\",");

        if (reasoningEffort != null && !reasoningEffort.isBlank() && !"none".equalsIgnoreCase(reasoningEffort)) {
            sb.append("\"reasoning\":{\"effort\":\"").append(MinimalJson.escape(reasoningEffort)).append("\"},");
        }

        sb.append("\"messages\":[");
        boolean first = true;
        for (Message msg : conversation) {
            if (!first) sb.append(",");
            sb.append("{\"role\":\"").append(MinimalJson.escape(msg.role().toLowerCase()))
              .append("\",\"content\":\"").append(MinimalJson.escape(msg.content()))
              .append("\"}");
            first = false;
        }
        sb.append("]}");
        return sb.toString();
    }

    static LlmResponse parseResponse(String responseBody) {
        String content = MinimalJson.extractString(responseBody, "content");
        if (content == null || content.isBlank()) {
            content = MinimalJson.extractString(responseBody, "reasoning");
        }
        if (content == null) {
            content = "";
        }

        content = sanitizeContent(content);

        int promptTokens = MinimalJson.extractInt(responseBody, "prompt_tokens", 0);
        int completionTokens = MinimalJson.extractInt(responseBody, "completion_tokens", 0);

        String toolName = null;
        String toolInput = null;

        Pattern toolPattern = Pattern.compile("(?m)^TOOL:\\s*([A-Za-z0-9_]+)\\s*$\\s*^INPUT:\\s*(.*?)$", Pattern.DOTALL);
        Matcher matcher = toolPattern.matcher(content);
        if (matcher.find()) {
            toolName = matcher.group(1).trim();
            toolInput = matcher.group(2).trim();
        }

        return new LlmResponse(content, toolName, toolInput, promptTokens, completionTokens);
    }

    static String sanitizeContent(String text) {
        if (text == null) return "";
        // Strip out <think>...</think> or <thought>...</thought> blocks if present
        String sanitized = text.replaceAll("(?s)<(think|thought)>.*?</\\1>\\s*", "");
        return sanitized.trim();
    }

    String buildRequestBodyForTesting(List<Message> conversation) {
        return buildRequestBody(conversation);
    }

    static String sanitizeContentForTesting(String content) {
        return sanitizeContent(content);
    }

    static LlmResponse parseResponseForTesting(String responseBody) {
        return parseResponse(responseBody);
    }
}
