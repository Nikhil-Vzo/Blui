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
 * Zero-dependency client for Google AI Studio Gemini API (Free Tier).
 * Supports gemini-2.5-flash and gemini-1.5-flash with real token tracking.
 */
public final class GeminiClient implements LlmProvider {

    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String FALLBACK_MODEL = "gemini-3.5-flash";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final Duration timeout;

    public GeminiClient() {
        this(System.getenv("GEMINI_API_KEY"), resolveDefaultModel(), Duration.ofMinutes(2));
    }

    public GeminiClient(String apiKey) {
        this(apiKey, resolveDefaultModel(), Duration.ofMinutes(2));
    }

    public GeminiClient(String apiKey, String model, Duration timeout) {
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : resolveDefaultModel();
        this.timeout = timeout != null ? timeout : Duration.ofMinutes(2);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private static String resolveDefaultModel() {
        String env = System.getenv("GEMINI_MODEL");
        return (env != null && !env.isBlank()) ? env.trim() : DEFAULT_MODEL;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public LlmResponse generate(List<Message> conversation) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY environment variable is not set. Get a free key at https://aistudio.google.com");
        }

        String requestJson = buildRequestBody(conversation);
        String endpoint = BASE_URL + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Auto-fallback if the primary model is deprecated or overloaded
        if (response.statusCode() != 200 && !model.equals(FALLBACK_MODEL)) {
            String fallbackEndpoint = BASE_URL + FALLBACK_MODEL + ":generateContent?key=" + apiKey;
            HttpRequest fallbackRequest = HttpRequest.newBuilder()
                    .uri(URI.create(fallbackEndpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> fallbackResponse = httpClient.send(fallbackRequest, HttpResponse.BodyHandlers.ofString());
            if (fallbackResponse.statusCode() == 200) {
                response = fallbackResponse;
            }
        }

        if (response.statusCode() != 200) {
            String errorMsg = MinimalJson.extractString(response.body(), "message");
            throw new RuntimeException("Gemini API error (HTTP " + response.statusCode() + "): "
                    + (errorMsg != null ? errorMsg : response.body()));
        }

        String responseBody = response.body();
        String text = MinimalJson.extractString(responseBody, "text");
        if (text == null) text = "";

        int promptTokens = MinimalJson.extractInt(responseBody, "promptTokenCount", 0);
        int completionTokens = MinimalJson.extractInt(responseBody, "candidatesTokenCount", 0);

        // Detect tool invocation (Format: TOOL: <Name>\nINPUT: <Input>)
        String toolName = null;
        String toolInput = null;

        Pattern toolPattern = Pattern.compile("(?m)^TOOL:\\s*([A-Za-z0-9_]+)\\s*$\\s*^INPUT:\\s*(.*?)$", Pattern.DOTALL);
        Matcher matcher = toolPattern.matcher(text);
        if (matcher.find()) {
            toolName = matcher.group(1).trim();
            toolInput = matcher.group(2).trim();
        }

        return new LlmResponse(text, toolName, toolInput, promptTokens, completionTokens);
    }

    private String buildRequestBody(List<Message> conversation) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"contents\":[");

        boolean first = true;
        for (Message msg : conversation) {
            if ("system".equalsIgnoreCase(msg.role())) continue;
            if (!first) sb.append(",");
            first = false;

            String role = "assistant".equalsIgnoreCase(msg.role()) ? "model" : "user";
            sb.append("{\"role\":\"").append(role).append("\",\"parts\":[{\"text\":\"")
              .append(MinimalJson.escape(msg.content()))
              .append("\"}]}");
        }

        sb.append("]");

        // Attach system instruction if present in conversation
        for (Message msg : conversation) {
            if ("system".equalsIgnoreCase(msg.role())) {
                sb.append(",\"systemInstruction\":{\"parts\":[{\"text\":\"")
                  .append(MinimalJson.escape(msg.content()))
                  .append("\"}]}");
                break;
            }
        }

        sb.append("}");
        return sb.toString();
    }
}
