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

        System.out.println("All 6 EnvConfig tests passed successfully!");
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
        java.util.Scanner scanner = new java.util.Scanner("sk-or-v1-abc123test\n");
        StringBuilder outBuf = new StringBuilder();
        Map<String, String> result = EnvConfig.runInteractiveWizard(scanner, s -> outBuf.append(s).append("\n"), s -> outBuf.append(s), null);

        assertCondition("sk-or-v1-abc123test".equals(result.get("OPENROUTER_API_KEY")),
                "Must save directly pasted OpenRouter API key from menu prompt");
        System.out.println("  ✓ testDirectPastedApiKeyAtMenuPrompt passed");
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

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }
}
