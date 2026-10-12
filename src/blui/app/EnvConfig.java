package blui.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * Interactive environment & secret configuration manager with provider isolation.
 * Stores local API keys and endpoints in a git-ignored .env file.
 */
public final class EnvConfig {

    public static final Path DEFAULT_ENV_PATH = Path.of(".env");
    public static final String DEFAULT_OPENROUTER_MODEL = "nvidia/nemotron-3.5-lightning:free";

    public record VariableDef(String key, String description, boolean isSecret, String defaultValue) {}

    public static final List<VariableDef> OPENROUTER_VARS = List.of(
            new VariableDef("OPENROUTER_API_KEY", "OpenRouter API Key (https://openrouter.ai/keys)", true, "")
    );

    public static final List<VariableDef> GEMINI_VARS = List.of(
            new VariableDef("GEMINI_API_KEY", "Google Gemini API Key (AI Studio)", true, "")
    );

    public static final List<VariableDef> OLLAMA_VARS = List.of(
            new VariableDef("OLLAMA_MODEL", "Local Ollama Model Name (Default: llama3.2)", false, "llama3.2")
    );

    public static final List<VariableDef> MANAGED_VARS = List.of(
            new VariableDef("OPENROUTER_API_KEY", "OpenRouter API Key (https://openrouter.ai/keys)", true, ""),
            new VariableDef("GEMINI_API_KEY", "Google Gemini API Key (AI Studio)", true, ""),
            new VariableDef("OLLAMA_MODEL", "Local Ollama Model Name (Default: llama3.2)", false, "llama3.2")
    );

    public static final List<VariableDef> ADVANCED_VARS = List.of(
            new VariableDef("OPENROUTER_API_KEY", "OpenRouter API Key (https://openrouter.ai/keys)", true, ""),
            new VariableDef("OPENROUTER_MODEL", "OpenRouter Model ID", false, "nvidia/nemotron-3.5-lightning:free"),
            new VariableDef("OPENROUTER_REASONING_EFFORT", "Thinking/Reasoning Effort (high/max/medium/low)", false, "high"),
            new VariableDef("GEMINI_API_KEY", "Google Gemini API Key (AI Studio)", true, ""),
            new VariableDef("GEMINI_MODEL", "Gemini Model (Default: gemini-3.5-flash)", false, "gemini-3.5-flash"),
            new VariableDef("OLLAMA_HOST", "Local Ollama Host Endpoint", false, "http://localhost:11434"),
            new VariableDef("OLLAMA_MODEL", "Local Ollama Model Name", false, "llama3.2")
    );

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[38;2;80;190;250m";
    private static final String GREEN = "\033[38;2;52;211;153m";
    private static final String RED = "\033[38;2;248;113;113m";
    private static final String YELLOW = "\033[38;2;251;191;36m";
    private static final String BLUE = "\033[38;2;96;165;250m";
    private static final String PURPLE = "\033[38;2;192;132;252m";
    private static final String GRAY = "\033[38;2;148;163;184m";
    private static final String WHITE = "\033[38;2;248;250;252m";

    private EnvConfig() {}

    /**
     * Checks if the user's input signifies 'keep' (empty line, 'k', 'keep').
     */
    public static boolean isKeepChoice(String input) {
        if (input == null) return true;
        String trimmed = input.trim();
        return trimmed.isEmpty() || "k".equalsIgnoreCase(trimmed) || "keep".equalsIgnoreCase(trimmed);
    }

    /**
     * Strips leading/trailing quotes and 'Bearer ' authorization prefixes from credentials.
     */
    public static String cleanSecret(String s) {
        if (s == null) return "";
        String cleaned = s.trim();
        while ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            if (cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
            } else {
                break;
            }
        }
        if (cleaned.startsWith("Bearer ")) {
            cleaned = cleaned.substring(7).trim();
        }
        return cleaned;
    }

    /**
     * Determines whether an environment variable key represents a sensitive credential.
     */
    public static boolean isSecretKey(String key) {
        if (key == null) return false;
        String upper = key.toUpperCase();
        return upper.contains("KEY") ||
               upper.contains("SECRET") ||
               upper.contains("TOKEN") ||
               upper.contains("PASSWORD");
    }

    /**
     * Checks if a provided model string is an accidental API key or masked token string.
     */
    public static boolean isCorruptModel(String model, String apiKey) {
        if (model == null || model.isBlank()) return false;
        String trimmed = cleanSecret(model);
        if (trimmed.isBlank()) return false;
        if (trimmed.startsWith("sk-") || trimmed.startsWith("AIzaSy") || trimmed.contains("••••")) {
            return true;
        }
        String cleanApiKey = cleanSecret(apiKey);
        if (!cleanApiKey.isBlank() && trimmed.equalsIgnoreCase(cleanApiKey)) {
            return true;
        }
        String envKey = cleanSecret(System.getenv("OPENROUTER_API_KEY"));
        if (!envKey.isBlank() && trimmed.equalsIgnoreCase(envKey)) {
            return true;
        }
        return false;
    }

    /**
     * Sanitizes map by resetting corrupted OPENROUTER_MODEL to default.
     * Returns true if map was modified.
     */
    public static boolean sanitizeEnvMap(Map<String, String> map) {
        if (map == null || !map.containsKey("OPENROUTER_MODEL")) {
            return false;
        }
        String model = map.get("OPENROUTER_MODEL");
        String apiKey = map.get("OPENROUTER_API_KEY");
        if (isCorruptModel(model, apiKey)) {
            map.put("OPENROUTER_MODEL", DEFAULT_OPENROUTER_MODEL);
            return true;
        }
        return false;
    }

    /**
     * Masks secret tokens (showing prefix and suffix with bullets in between).
     * Ensures secret masking never leaks into non-secret fields.
     */
    public static String mask(String key, String val) {
        if (val == null || val.isBlank()) {
            return "(not set)";
        }
        if (!isSecretKey(key)) {
            return val;
        }

        String trimmed = cleanSecret(val);
        if (trimmed.length() > 10) {
            return trimmed.substring(0, 6) + "••••••••" + trimmed.substring(trimmed.length() - 4);
        } else if (trimmed.length() > 4) {
            return trimmed.substring(0, 2) + "••••••••" + trimmed.substring(trimmed.length() - 2);
        } else {
            return "••••••••";
        }
    }

    /**
     * Loads key-value pairs from the given .env file path.
     * Automatically purges and resets corrupted model values.
     */
    public static Map<String, String> load(Path path) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            return map;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.toLowerCase().startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    String k = trimmed.substring(0, eq).trim();
                    if (k.toLowerCase().startsWith("export ")) {
                        k = k.substring(7).trim();
                    }
                    String v = trimmed.substring(eq + 1).trim();
                    if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
                        if (v.length() >= 2) {
                            v = v.substring(1, v.length() - 1);
                        }
                    }
                    map.put(k, v);
                }
            }
        } catch (IOException ignored) {}

        if (sanitizeEnvMap(map)) {
            try {
                save(path, map);
            } catch (IOException ignored) {}
        }

        return map;
    }

    /**
     * Saves key-value pairs to the specified .env file path.
     */
    public static void save(Path path, Map<String, String> vars) throws IOException {
        Map<String, String> sanitized = new LinkedHashMap<>(vars);
        sanitizeEnvMap(sanitized);

        StringBuilder sb = new StringBuilder();
        sb.append("# Blui Agent Environment Configuration\n");
        sb.append("# Generated interactively via terminal\n\n");

        for (Map.Entry<String, String> entry : sanitized.entrySet()) {
            String val = entry.getValue();
            if (val != null && !val.isBlank()) {
                sb.append(entry.getKey()).append("=").append(val.trim()).append("\n");
            }
        }

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Updates or sets a single variable in the specified .env file path.
     */
    public static void saveVar(Path path, String key, String val) throws IOException {
        String cleanKey = (key != null && key.toLowerCase().startsWith("export ")) ? key.substring(7).trim() : key;
        Map<String, String> current = load(path);
        if (val == null || val.isBlank() || "clear".equalsIgnoreCase(val.trim())) {
            current.remove(cleanKey);
        } else {
            String newVal = cleanSecret(val);
            if ("OPENROUTER_MODEL".equals(cleanKey)) {
                String apiKey = current.get("OPENROUTER_API_KEY");
                if (isCorruptModel(newVal, apiKey)) {
                    newVal = DEFAULT_OPENROUTER_MODEL;
                }
            }
            current.put(cleanKey, newVal);
        }
        save(path, current);
    }

    /**
     * Resolves a variable by checking:
     * 1. OS Environment Variable
     * 2. Local .env file
     */
    public static String get(String key) {
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.isBlank()) {
            if ("OPENROUTER_MODEL".equals(key)) {
                String apiKey = System.getenv("OPENROUTER_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = load(DEFAULT_ENV_PATH).get("OPENROUTER_API_KEY");
                }
                if (isCorruptModel(envVal, apiKey)) {
                    envVal = DEFAULT_OPENROUTER_MODEL;
                }
            }
            return cleanSecret(envVal);
        }
        Map<String, String> envMap = load(DEFAULT_ENV_PATH);
        String fileVal = envMap.get(key);
        if (fileVal != null && !fileVal.isBlank()) {
            return cleanSecret(fileVal);
        }
        return null;
    }

    public static Map<String, String> runInteractiveWizard(Scanner scanner, Consumer<String> out, Consumer<String> outInline) {
        return runInteractiveWizard(scanner, out, outInline, null);
    }

    /**
     * Runs provider-aware interactive terminal prompt for environment variables.
     */
    public static Map<String, String> runInteractiveWizard(Scanner scanner, Consumer<String> out, Consumer<String> outInline, String targetProvider) {
        List<VariableDef> targetVars = MANAGED_VARS;
        String providerTitle = "All Providers";

        if (targetProvider == null || targetProvider.isBlank()) {
            out.accept("\n" + CYAN + "┌─ ENVIRONMENT SETUP ────────────────────────────────────────────────┐" + RESET);
            out.accept(CYAN + "│ " + WHITE + "Choose provider to configure:                                      " + CYAN + "│" + RESET);
            out.accept(CYAN + "│   " + GREEN + "[1] OpenRouter" + GRAY + "    (Nemotron 3.5 Lightning / Any Model)           " + CYAN + "│" + RESET);
            out.accept(CYAN + "│   " + BLUE + "[2] Google Gemini" + GRAY + " (Gemini 3.5 Flash)                             " + CYAN + "│" + RESET);
            out.accept(CYAN + "│   " + YELLOW + "[3] Local Ollama" + GRAY + "  (Model Name)                                   " + CYAN + "│" + RESET);
            out.accept(CYAN + "│   " + PURPLE + "[4] All Providers" + GRAY + " (OpenRouter, Gemini, Ollama)                   " + CYAN + "│" + RESET);
            out.accept(CYAN + "│   " + WHITE + "[5] Advanced" + GRAY + "      (All Settings, Endpoints & Reasoning)          " + CYAN + "│" + RESET);
            out.accept(CYAN + "└────────────────────────────────────────────────────────────────────┘" + RESET);
            outInline.accept(BOLD + "Enter choice [1-5] (Default: 1 for OpenRouter): " + RESET);

            String choice = "";
            if (scanner.hasNextLine()) {
                choice = scanner.nextLine().trim();
            }
            String cleanChoice = cleanSecret(choice);

            // If user directly pasted an API key at the menu prompt, save it immediately
            if (cleanChoice.startsWith("sk-or-") || cleanChoice.startsWith("sk-") || cleanChoice.startsWith("AIzaSy")) {
                if (cleanChoice.startsWith("AIzaSy")) {
                    try {
                        saveVar(DEFAULT_ENV_PATH, "GEMINI_API_KEY", cleanChoice);
                        out.accept(GREEN + "✓ Detected Google Gemini API key (" + mask("GEMINI_API_KEY", cleanChoice) + ") — saved to .env!\n" + RESET);
                    } catch (IOException e) {
                        out.accept(RED + "✗ Error saving .env: " + e.getMessage() + "\n" + RESET);
                    }
                } else {
                    try {
                        saveVar(DEFAULT_ENV_PATH, "OPENROUTER_API_KEY", cleanChoice);
                        out.accept(GREEN + "✓ Detected OpenRouter API key (" + mask("OPENROUTER_API_KEY", cleanChoice) + ") — saved to .env!\n" + RESET);
                    } catch (IOException e) {
                        out.accept(RED + "✗ Error saving .env: " + e.getMessage() + "\n" + RESET);
                    }
                }
                return load(DEFAULT_ENV_PATH);
            }

            if ("2".equals(choice) || "gemini".equalsIgnoreCase(choice)) {
                targetVars = GEMINI_VARS;
                providerTitle = "Google Gemini";
            } else if ("3".equals(choice) || "ollama".equalsIgnoreCase(choice)) {
                targetVars = OLLAMA_VARS;
                providerTitle = "Local Ollama";
            } else if ("4".equals(choice) || "all".equalsIgnoreCase(choice) || "both".equalsIgnoreCase(choice)) {
                targetVars = MANAGED_VARS;
                providerTitle = "All Providers";
            } else if ("5".equals(choice) || "advanced".equalsIgnoreCase(choice)) {
                targetVars = ADVANCED_VARS;
                providerTitle = "Advanced Settings";
            } else {
                targetVars = OPENROUTER_VARS;
                providerTitle = "OpenRouter";
            }
        } else if ("openrouter".equalsIgnoreCase(targetProvider)) {
            targetVars = OPENROUTER_VARS;
            providerTitle = "OpenRouter";
        } else if ("gemini".equalsIgnoreCase(targetProvider)) {
            targetVars = GEMINI_VARS;
            providerTitle = "Google Gemini";
        } else if ("ollama".equalsIgnoreCase(targetProvider)) {
            targetVars = OLLAMA_VARS;
            providerTitle = "Local Ollama";
        } else if ("advanced".equalsIgnoreCase(targetProvider) || "all".equalsIgnoreCase(targetProvider)) {
            targetVars = ADVANCED_VARS;
            providerTitle = "Advanced Settings";
        }

        out.accept("\n" + CYAN + "─ Configuring " + providerTitle + " ────────────────────────────────────────" + RESET);
        out.accept(GRAY + "Press Enter or 'k' to keep existing value | Type 'clear' to remove" + RESET + "\n");

        Map<String, String> currentEnv = load(DEFAULT_ENV_PATH);
        Map<String, String> updated = new LinkedHashMap<>(currentEnv);

        int index = 1;
        for (VariableDef def : targetVars) {
            String existing = updated.get(def.key());
            if ((existing == null || existing.isBlank()) && System.getenv(def.key()) != null) {
                existing = System.getenv(def.key());
            }

            String maskedCurrent = mask(def.key(), existing);

            out.accept(BOLD + "[" + index + "/" + targetVars.size() + "] " + CYAN + def.key() + RESET +
                       GRAY + " (" + def.description() + ")" + RESET);
            out.accept("      Current: " + (existing != null && !existing.isBlank() ? GREEN + maskedCurrent : GRAY + maskedCurrent) + RESET);

            outInline.accept(BOLD + "      Enter value (or 'k' to keep): " + RESET);
            String input = "";
            if (scanner.hasNextLine()) {
                input = scanner.nextLine();
            }

            if (isKeepChoice(input)) {
                out.accept(GRAY + "      └─ Kept existing value.\n" + RESET);
            } else if ("clear".equalsIgnoreCase(input.trim()) || "none".equalsIgnoreCase(input.trim())) {
                updated.remove(def.key());
                out.accept(YELLOW + "      └─ Cleared.\n" + RESET);
            } else {
                String newVal = cleanSecret(input);
                if ("OPENROUTER_MODEL".equals(def.key()) && isCorruptModel(newVal, updated.get("OPENROUTER_API_KEY"))) {
                    newVal = DEFAULT_OPENROUTER_MODEL;
                    out.accept(YELLOW + "      └─ Note: Accidental API key detected for model ID. Resetting to default: " + DEFAULT_OPENROUTER_MODEL + "\n" + RESET);
                } else {
                    out.accept(GREEN + "      └─ Updated: " + mask(def.key(), newVal) + "\n" + RESET);
                }
                updated.put(def.key(), newVal);
            }

            index++;
        }

        try {
            save(DEFAULT_ENV_PATH, updated);
            out.accept(GREEN + BOLD + "✓ Environment configuration saved to .env (git-ignored)\n" + RESET);
        } catch (IOException e) {
            out.accept(RED + "✗ Error saving .env file: " + e.getMessage() + "\n" + RESET);
        }

        return updated;
    }

    /**
     * Prints the current environment variables in a clean status box.
     */
    public static void printCurrent(Consumer<String> out) {
        Map<String, String> env = load(DEFAULT_ENV_PATH);

        out.accept("\n" + CYAN + "┌─ CURRENT ENVIRONMENT (.env) ───────────────────────────────────────┐" + RESET);
        for (VariableDef def : ADVANCED_VARS) {
            String val = env.get(def.key());
            if (val == null && System.getenv(def.key()) != null) {
                val = System.getenv(def.key()) + " (from OS env)";
            }
            String masked = mask(def.key(), val);
            String line = String.format("│ %-16s = %-46s │", def.key(), masked);
            out.accept(CYAN + line + RESET);
        }
        out.accept(CYAN + "└────────────────────────────────────────────────────────────────────┘\n" + RESET);
    }
}
