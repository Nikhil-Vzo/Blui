package blui.tools;

import blui.core.Tool;
import blui.core.ToolResults;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Zero-dependency HTTP client tool built on Java 24 standard library.
 * Includes comprehensive SSRF protection preventing requests and redirects
 * to loopback, link-local, private, and cloud metadata IP ranges.
 */
public final class HttpTool implements Tool {

    private static final int MAX_RESPONSE_CHARS = 100_000; // 100KB memory safeguard
    private static final int MAX_REDIRECTS = 5;
    private final HttpClient client;
    private final Duration timeout;

    public HttpTool() {
        this(Duration.ofSeconds(10));
    }

    public HttpTool(Duration timeout) {
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        this.client = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
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
            URI currentUri = URI.create(url);
            HttpResponse<String> response = null;
            int redirectCount = 0;

            while (true) {
                validateSsrf(currentUri);

                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(currentUri)
                        .timeout(this.timeout)
                        .header("User-Agent", "Blui-Agent/1.0 (Java 24)");

                if ("POST".equalsIgnoreCase(method) && redirectCount == 0) {
                    reqBuilder.header("Content-Type", "application/json")
                              .POST(HttpRequest.BodyPublishers.ofString(body));
                } else {
                    reqBuilder.GET();
                }

                response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if ((status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
                        && response.headers().firstValue("Location").isPresent()) {
                    redirectCount++;
                    if (redirectCount > MAX_REDIRECTS) {
                        return new ToolResults(name(), "Error: Too many redirects (exceeded limit of " + MAX_REDIRECTS + ")", false, System.currentTimeMillis() - start);
                    }
                    String location = response.headers().firstValue("Location").get();
                    URI nextUri = currentUri.resolve(location);
                    validateSsrf(nextUri);
                    currentUri = nextUri;
                } else {
                    break;
                }
            }

            long duration = System.currentTimeMillis() - start;
            int status = response.statusCode();
            boolean success = (status >= 200 && status < 300);

            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_CHARS) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_CHARS) + "\n...[TRUNCATED: Response exceeded 100KB limit]";
            }

            String output = String.format("HTTP %d%n%s", status, responseBody != null ? responseBody : "");
            return new ToolResults(name(), output, success, duration);

        } catch (SecurityException se) {
            return new ToolResults(name(), "Security Violation: " + se.getMessage(), false, System.currentTimeMillis() - start);
        } catch (IllegalArgumentException iae) {
            return new ToolResults(name(), "Error: Invalid URL syntax - " + iae.getMessage(), false, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new ToolResults(name(), "HTTP request failed: " + e.getMessage(), false, System.currentTimeMillis() - start);
        }
    }

    public static void validateSsrf(URI uri) throws SecurityException {
        if (uri == null) {
            throw new SecurityException("URI cannot be null");
        }
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new SecurityException("Only http and https protocols are supported: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("Host cannot be empty");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT).trim();
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost") || lowerHost.endsWith(".local") || lowerHost.equals("127.0.0.1") || lowerHost.equals("::1")) {
            throw new SecurityException("SSRF blocked: Access to localhost or loopback is forbidden (" + host + ")");
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isBlockedAddress(addr)) {
                    throw new SecurityException("SSRF blocked: Destination address " + addr.getHostAddress() + " (" + host + ") is in a private, loopback, link-local, or cloud metadata range.");
                }
            }
        } catch (UnknownHostException e) {
            throw new SecurityException("Host resolution failed: " + host, e);
        }
    }

    private static boolean isBlockedAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress() || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }
        byte[] b = addr.getAddress();
        if (b.length == 4) {
            int b0 = b[0] & 0xFF;
            int b1 = b[1] & 0xFF;
            if (b0 == 0) return true; // 0.0.0.0/8
            if (b0 == 10) return true; // 10.0.0.0/8
            if (b0 == 127) return true; // 127.0.0.0/8
            if (b0 == 169 && b1 == 254) return true; // 169.254.0.0/16
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true; // 172.16.0.0/12
            if (b0 == 192 && b1 == 168) return true; // 192.168.0.0/16
            if (b0 >= 224) return true; // 224.0.0.0+
        } else if (b.length == 16) {
            boolean allZeroExceptLast = true;
            for (int i = 0; i < 15; i++) {
                if (b[i] != 0) { allZeroExceptLast = false; break; }
            }
            if (allZeroExceptLast && (b[15] == 1 || b[15] == 0)) return true; // ::1, ::

            if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) return true; // fe80::/10
            if ((b[0] & 0xFE) == 0xFC) return true; // fc00::/7

            boolean isMapped = true;
            for (int i = 0; i < 10; i++) {
                if (b[i] != 0) { isMapped = false; break; }
            }
            if (isMapped && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
                int mb0 = b[12] & 0xFF;
                int mb1 = b[13] & 0xFF;
                if (mb0 == 0 || mb0 == 10 || mb0 == 127 || (mb0 == 169 && mb1 == 254)
                        || (mb0 == 172 && (mb1 >= 16 && mb1 <= 31))
                        || (mb0 == 192 && mb1 == 168) || mb0 >= 224) {
                    return true;
                }
            }
        }
        return false;
    }
}
