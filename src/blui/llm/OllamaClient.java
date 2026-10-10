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
 * Zero-dependency client for local, offline Ollama models.
 * Connects to http://localhost:11434/api/chat with zero external libraries.
 */
public final class OllamaClient implements LlmProvider {

    private static final String DEFAULT_HOST = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final Duration timeout;

    public OllamaClient() {
        this(DEFAULT_HOST, DEFAULT_MODEL, Duration.ofMinutes(2));
    }

    public OllamaClient(String model) {
        this(DEFAULT_HOST, model, Duration.ofMinutes(2));
    }

    public OllamaClient(String host, String model, Duration timeout) {
        this.baseUrl = (host != null && !host.isBlank()) ? host.replaceAll("/+$", "") : DEFAULT_HOST;
        this.model = (model != null && !model.isBlank()) ? model : DEFAULT_MODEL;
        this.timeout = timeout != null ? timeout : Duration.ofMinutes(2);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public LlmResponse generate(List<Message> conversation) throws Exception {
        String endpoint = baseUrl + "/api/chat";
        String requestJson = buildRequestBody(conversation);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String errorMsg = MinimalJson.extractString(response.body(), "error");
            throw new RuntimeException("Ollama error (HTTP " + response.statusCode() + "): "
                    + (errorMsg != null ? errorMsg : response.body()));
        }

        String responseBody = response.body();
        String content = MinimalJson.extractString(responseBody, "content");
        if (content == null) content = "";

        // Extract real local hardware tokens computed by Ollama
        int promptTokens = MinimalJson.extractInt(responseBody, "prompt_eval_count", 0);
        int completionTokens = MinimalJson.extractInt(responseBody, "eval_count", 0);

        // Detect tool invocation (Format: TOOL: <Name>\nINPUT: <Input>)
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

    /**
     * Pings local Ollama daemon to check if it is running.
     */
    public boolean isAvailable() {
        try {
            HttpRequest ping = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(ping, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildRequestBody(List<Message> conversation) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(model).append("\",");
        sb.append("\"stream\":false,");
        sb.append("\"messages\":[");

        boolean first = true;
        for (Message msg : conversation) {
            if (!first) sb.append(",");
            first = false;

            sb.append("{\"role\":\"").append(msg.role()).append("\",\"content\":\"")
              .append(MinimalJson.escape(msg.content()))
              .append("\"}");
        }

        sb.append("]}");
        return sb.toString();
    }
}
