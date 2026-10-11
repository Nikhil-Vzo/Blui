package blui.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, zero-dependency JSON utility for LLM requests and responses.
 * Avoids multi-megabyte Jackson/Gson dependencies.
 */
public final class MinimalJson {

    private MinimalJson() {}

    /**
     * Escapes raw user prompt strings for valid JSON payload serialization.
     */
    public static String escape(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length() + 20);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * Extracts the first string value matching the given key from a JSON payload.
     * Example: extractString(json, "text") -> contents of "text": "..."
     */
    public static String extractString(String json, String key) {
        if (json == null || key == null) return null;
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon == -1) return null;
        int p = colon + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) {
            p++;
        }
        if (p >= json.length() || json.charAt(p) != '"') {
            return null;
        }
        int quoteStart = p;

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return unescape(sb.toString());
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    /**
     * Extracts an integer number value for a given key.
     * Example: extractInt(json, "totalTokenCount", 0) -> 60
     */
    public static int extractInt(String json, String key, int defaultValue) {
        if (json == null || key == null) return defaultValue;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private static String unescape(String escaped) {
        StringBuilder sb = new StringBuilder(escaped.length());
        boolean inSlash = false;
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (inSlash) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 < escaped.length()) {
                            String hex = escaped.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append("\\u");
                            }
                        } else {
                            sb.append("\\u");
                        }
                    }
                    default -> sb.append('\\').append(c);
                }
                inSlash = false;
            } else if (c == '\\') {
                inSlash = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
