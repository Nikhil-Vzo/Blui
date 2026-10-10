package blui.tools;

import blui.core.Tool;
import blui.core.ToolResults;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Zero-dependency HTTP client tool built on Java 24 standard library.
 * Allows the agent to fetch live web documentation and query REST endpoints.
 */
public final class HttpTool implements Tool {

    private static final int MAX_RESPONSE_CHARS = 100_000; // 100KB memory safeguard
    private final HttpClient client;
    private final Duration timeout;

    public HttpTool() {
        this(Duration.ofSeconds(10));
    }

    public HttpTool(Duration timeout) {
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "HttpTool";
    }

    @Override
    public ToolResults execute(String input) {
        long start = System.currentTimeMillis();

        if (input == null || input.isBlank()) {
            return new ToolResults(name(), "Error: Command cannot be empty. Usage: get <url> | post <url> <body>", false, 0);
        }

        String trimmed = input.strip();
        String method = "GET";
        String url;
        String body = "";

        if (trimmed.toLowerCase().startsWith("get ")) {
            url = trimmed.substring(4).strip();
        } else if (trimmed.toLowerCase().startsWith("post ")) {
            method = "POST";
            String[] parts = trimmed.substring(5).strip().split("\\s+", 2);
            url = parts[0];
            body = parts.length > 1 ? parts[1] : "";
        } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            url = trimmed;
        } else {
            return new ToolResults(name(), "Error: Invalid format. Must start with 'get <url>', 'post <url> <body>', or 'https://'", false, 0);
        }

        try {
            URI uri = URI.create(url);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return new ToolResults(name(), "Error: Only http and https protocols are supported.", false, 0);
            }

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(this.timeout)
                    .header("User-Agent", "Blui-Agent/1.0 (Java 24)");

            if ("POST".equalsIgnoreCase(method)) {
                reqBuilder.header("Content-Type", "application/json")
                          .POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - start;
            int status = response.statusCode();
            boolean success = (status >= 200 && status < 300);

            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_CHARS) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_CHARS) + "\n...[TRUNCATED: Response exceeded 100KB limit]";
            }

            String output = String.format("HTTP %d%n%s", status, responseBody != null ? responseBody : "");
            return new ToolResults(name(), output, success, duration);

        } catch (IllegalArgumentException iae) {
            return new ToolResults(name(), "Error: Invalid URL syntax - " + iae.getMessage(), false, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new ToolResults(name(), "HTTP request failed: " + e.getMessage(), false, System.currentTimeMillis() - start);
        }
    }
}
