package blui.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Verification test suite for Hermes-style environment configuration.
 */
public class EnvConfigTest {

    public static void main(String[] args) {
        System.out.println("Running EnvConfig verification suite...");

        testKeepChoice();
        testMasking();
        testLoadAndSaveEnv();
        testInlineSet();
        testStreamlinedVariableLists();
        testDirectPastedApiKeyAtMenuPrompt();
        testCorruptedOpenRouterModelPurgedAndReset();
        testExportPrefixStripping();

        System.out.println("All 8 EnvConfig tests passed successfully!");
    }

    private static void testStreamlinedVariableLists() {
        assertCondition(EnvConfig.OPENROUTER_VARS.size() == 1, "OPENROUTER_VARS should only contain 1 entry (OPENROUTER_API_KEY)");
        assertCondition("OPENROUTER_API_KEY".equals(EnvConfig.OPENROUTER_VARS.get(0).key()), "OPENROUTER_VARS[0] should be OPENROUTER_API_KEY");

        assertCondition(EnvConfig.GEMINI_VARS.size() == 1, "GEMINI_VARS should only contain GEMINI_API_KEY");
        assertCondition("GEMINI_API_KEY".equals(EnvConfig.GEMINI_VARS.get(0).key()), "GEMINI_VARS should be GEMINI_API_KEY");

        assertCondition(EnvConfig.OLLAMA_VARS.size() == 1, "OLLAMA_VARS should only contain OLLAMA_MODEL");
        assertCondition("OLLAMA_MODEL".equals(EnvConfig.OLLAMA_VARS.get(0).key()), "OLLAMA_VARS should be OLLAMA_MODEL");

        assertCondition(EnvConfig.MANAGED_VARS.size() == 3, "MANAGED_VARS should contain 3 essentials");
        assertCondition(EnvConfig.ADVANCED_VARS.size() == 7, "ADVANCED_VARS should contain all 7 settings");

        System.out.println("  ✓ testStreamlinedVariableLists passed");
    }

    private static void testDirectPastedApiKeyAtMenuPrompt() {
        Map<String, String> backup = EnvConfig.load(EnvConfig.DEFAULT_ENV_PATH);
        try {
            // Direct paste without quotes
            java.util.Scanner scanner = new java.util.Scanner("sk-or-v1-abc123test\n");
            StringBuilder outBuf = new StringBuilder();
            Map<String, String> result = EnvConfig.runInteractiveWizard(scanner, s -> outBuf.append(s).append("\n"), s -> outBuf.append(s), null);

            assertCondition("sk-or-v1-abc123test".equals(result.get("OPENROUTER_API_KEY")),
                    "Must save directly pasted OpenRouter API key from menu prompt");

            // Direct paste with quotes
            java.util.Scanner quotedScanner = new java.util.Scanner("\"sk-or-v1-quotedtest\"\n");
            Map<String, String> quotedResult = EnvConfig.runInteractiveWizard(quotedScanner, s -> {}, s -> {}, null);
            assertCondition("sk-or-v1-quotedtest".equals(quotedResult.get("OPENROUTER_API_KEY")),
                    "Must unquote directly pasted OpenRouter API key from menu prompt");

            // Direct paste with Bearer prefix
            java.util.Scanner bearerScanner = new java.util.Scanner("Bearer sk-or-v1-bearertest\n");
            Map<String, String> bearerResult = EnvConfig.runInteractiveWizard(bearerScanner, s -> {}, s -> {}, null);
            assertCondition("sk-or-v1-bearertest".equals(bearerResult.get("OPENROUTER_API_KEY")),
                    "Must strip Bearer prefix from directly pasted OpenRouter API key");

            System.out.println("  ✓ testDirectPastedApiKeyAtMenuPrompt passed");
        } finally {
            if (!backup.isEmpty()) {
                try {
                    EnvConfig.save(EnvConfig.DEFAULT_ENV_PATH, backup);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void testKeepChoice() {
        assertCondition(EnvConfig.isKeepChoice(""), "Empty input should keep existing value");
        assertCondition(EnvConfig.isKeepChoice("k"), "'k' should keep existing value");
        assertCondition(EnvConfig.isKeepChoice("K"), "'K' should keep existing value");
        assertCondition(EnvConfig.isKeepChoice("keep"), "'keep' should keep existing value");
        assertCondition(EnvConfig.isKeepChoice("  k  "), "Trimmed 'k' should keep existing value");
        assertCondition(!EnvConfig.isKeepChoice("new_key_123"), "New key should not be keep");
        assertCondition(!EnvConfig.isKeepChoice("clear"), "'clear' should not be keep");
        System.out.println("  ✓ testKeepChoice passed");
    }

    private static void testMasking() {
        assertCondition("(not set)".equals(EnvConfig.mask("GEMINI_API_KEY", null)), "Null should be (not set)");
        assertCondition("(not set)".equals(EnvConfig.mask("GEMINI_API_KEY", "   ")), "Blank should be (not set)");

        String maskedKey = EnvConfig.mask("GEMINI_API_KEY", "AIzaSyABC1234567890XYZ");
        assertCondition(maskedKey.startsWith("AIzaSy"), "Masked key should retain prefix");
        assertCondition(maskedKey.endsWith("0XYZ"), "Masked key should retain suffix");
        assertCondition(maskedKey.contains("••••"), "Masked key should contain bullets");

        String nonSecret = EnvConfig.mask("OLLAMA_HOST", "http://localhost:11434");
        assertCondition("http://localhost:11434".equals(nonSecret), "Non-secret variable should not be masked");

        String customModel = EnvConfig.mask("OPENROUTER_MODEL", "anthropic/claude-3.5-sonnet");
        assertCondition("anthropic/claude-3.5-sonnet".equals(customModel), "OPENROUTER_MODEL should not be masked");

        String quotedKey = EnvConfig.mask("OPENROUTER_API_KEY", "\"sk-or-v1-0123456789abcdef\"");
        assertCondition(quotedKey.contains("••••"), "Quoted key should be masked");
        assertCondition(!quotedKey.contains("\""), "Masked key should not contain surrounding quotes");

        System.out.println("  ✓ testMasking passed");
    }

    private static void testLoadAndSaveEnv() {
        Path tempEnv = Path.of("build", "test_temp.env");
        try {
            Files.createDirectories(tempEnv.getParent());
            Files.deleteIfExists(tempEnv);

            Map<String, String> initial = Map.of(
                    "GEMINI_API_KEY", "test_key_value",
                    "OLLAMA_HOST", "http://test:11434"
            );
            EnvConfig.save(tempEnv, initial);

            Map<String, String> loaded = EnvConfig.load(tempEnv);
            assertCondition("test_key_value".equals(loaded.get("GEMINI_API_KEY")), "GEMINI_API_KEY should match");
            assertCondition("http://test:11434".equals(loaded.get("OLLAMA_HOST")), "OLLAMA_HOST should match");

            Files.deleteIfExists(tempEnv);
            System.out.println("  ✓ testLoadAndSaveEnv passed");
        } catch (IOException e) {
            throw new RuntimeException("testLoadAndSaveEnv failed: " + e.getMessage(), e);
        }
    }

    private static void testInlineSet() {
        Path tempEnv = Path.of("build", "test_inline.env");
        try {
            Files.createDirectories(tempEnv.getParent());
            Files.deleteIfExists(tempEnv);

            EnvConfig.saveVar(tempEnv, "TEST_VAR", "hello_world");
            Map<String, String> loaded = EnvConfig.load(tempEnv);
            assertCondition("hello_world".equals(loaded.get("TEST_VAR")), "TEST_VAR should match");

            // Update same var
            EnvConfig.saveVar(tempEnv, "TEST_VAR", "updated_world");
            loaded = EnvConfig.load(tempEnv);
            assertCondition("updated_world".equals(loaded.get("TEST_VAR")), "TEST_VAR should be updated");

            Files.deleteIfExists(tempEnv);
            System.out.println("  ✓ testInlineSet passed");
        } catch (IOException e) {
            throw new RuntimeException("testInlineSet failed: " + e.getMessage(), e);
        }
    }

    private static void testCorruptedOpenRouterModelPurgedAndReset() {
        Path tempEnv = Path.of("build", "test_corrupt.env");
        try {
            Files.createDirectories(tempEnv.getParent());
            Files.deleteIfExists(tempEnv);

            // Write corrupted .env where OPENROUTER_MODEL is the API key
            String content = "OPENROUTER_API_KEY=sk-or-v1-secret-key-1234567890abcdef\n" +
                             "OPENROUTER_MODEL=sk-or-v1-secret-key-1234567890abcdef\n";
            Files.writeString(tempEnv, content);

            Map<String, String> loaded = EnvConfig.load(tempEnv);
            assertCondition("nvidia/nemotron-3.5-lightning:free".equals(loaded.get("OPENROUTER_MODEL")),
                    "Corrupted OPENROUTER_MODEL must be purged/reset to default nemotron model on load");

            // Verify the file itself on disk was sanitized
            String diskContent = Files.readString(tempEnv);
            assertCondition(!diskContent.contains("OPENROUTER_MODEL=sk-or-v1-"),
                    "File on disk must not retain corrupted API key as model");
            assertCondition(diskContent.contains("OPENROUTER_MODEL=nvidia/nemotron-3.5-lightning:free"),
                    "File on disk must have reset OPENROUTER_MODEL to default nemotron model");

            // Also test save() directly with corrupt model
            Map<String, String> toSave = Map.of(
                    "OPENROUTER_API_KEY", "sk-or-v1-another-key",
                    "OPENROUTER_MODEL", "sk-accidental-paste"
            );
            EnvConfig.save(tempEnv, toSave);
            Map<String, String> reloaded = EnvConfig.load(tempEnv);
            assertCondition("nvidia/nemotron-3.5-lightning:free".equals(reloaded.get("OPENROUTER_MODEL")),
                    "save() must sanitize corrupt OPENROUTER_MODEL before writing to disk");

            // Test bullet masked model reset
            EnvConfig.saveVar(tempEnv, "OPENROUTER_MODEL", "sk-••••••••1234");
            Map<String, String> maskedReload = EnvConfig.load(tempEnv);
            assertCondition("nvidia/nemotron-3.5-lightning:free".equals(maskedReload.get("OPENROUTER_MODEL")),
                    "Masked bullet model must be reset to default model");

            // Test quoted corrupt model in .env
            String quotedContent = "OPENROUTER_API_KEY=\"sk-or-v1-secret-key-1234567890abcdef\"\n" +
                                   "OPENROUTER_MODEL=\"sk-or-v1-secret-key-1234567890abcdef\"\n";
            Files.writeString(tempEnv, quotedContent);
            Map<String, String> loadedQuoted = EnvConfig.load(tempEnv);
            assertCondition("nvidia/nemotron-3.5-lightning:free".equals(loadedQuoted.get("OPENROUTER_MODEL")),
                    "Quoted corrupted OPENROUTER_MODEL must be purged/reset on load");

            // Test Bearer prefixed corrupt model
            EnvConfig.saveVar(tempEnv, "OPENROUTER_MODEL", "Bearer sk-or-v1-accidental");
            Map<String, String> loadedBearer = EnvConfig.load(tempEnv);
            assertCondition("nvidia/nemotron-3.5-lightning:free".equals(loadedBearer.get("OPENROUTER_MODEL")),
                    "Bearer prefixed corrupted OPENROUTER_MODEL must be reset");

            // Test valid custom model is preserved and not overwritten
            EnvConfig.saveVar(tempEnv, "OPENROUTER_MODEL", "anthropic/claude-3.5-sonnet");
            Map<String, String> loadedCustom = EnvConfig.load(tempEnv);
            assertCondition("anthropic/claude-3.5-sonnet".equals(loadedCustom.get("OPENROUTER_MODEL")),
                    "Valid custom OPENROUTER_MODEL must be preserved");

            Files.deleteIfExists(tempEnv);
            System.out.println("  ✓ testCorruptedOpenRouterModelPurgedAndReset passed");
        } catch (IOException e) {
            throw new RuntimeException("testCorruptedOpenRouterModelPurgedAndReset failed: " + e.getMessage(), e);
        }
    }

    private static void testExportPrefixStripping() {
        try {
            Path tempEnv = Files.createTempFile("blui_export_test_", ".env");
            String content = "export OPENROUTER_API_KEY=sk-or-v1-my-key\n" +
                             "export GEMINI_MODEL=gemini-3.5-flash\n" +
                             "OLLAMA_MODEL=llama3.2\n";
            Files.writeString(tempEnv, content);

            Map<String, String> loaded = EnvConfig.load(tempEnv);
            assertCondition("sk-or-v1-my-key".equals(loaded.get("OPENROUTER_API_KEY")),
                    "Key with 'export ' prefix must be stripped and loaded as OPENROUTER_API_KEY: got " + loaded);
            assertCondition(!loaded.containsKey("export OPENROUTER_API_KEY"),
                    "Map must not contain raw 'export OPENROUTER_API_KEY'");
            assertCondition("gemini-3.5-flash".equals(loaded.get("GEMINI_MODEL")),
                    "Key with 'export ' prefix must be stripped and loaded as GEMINI_MODEL");

            // Test saveVar with export prefix
            EnvConfig.saveVar(tempEnv, "export CUSTOM_VAR", "test-val");
            Map<String, String> loaded2 = EnvConfig.load(tempEnv);
            assertCondition("test-val".equals(loaded2.get("CUSTOM_VAR")),
                    "saveVar with 'export ' prefix must save as clean CUSTOM_VAR");

            Files.deleteIfExists(tempEnv);
            System.out.println("  ✓ testExportPrefixStripping passed");
        } catch (IOException e) {
            throw new RuntimeException("testExportPrefixStripping failed: " + e.getMessage(), e);
        }
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }
}
