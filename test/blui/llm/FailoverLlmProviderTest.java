package blui.llm;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FailoverLlmProviderTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Running FailoverLlmProvider verification suite...");

        testPrimarySucceeds();
        testFailoverToSecondaryOnException();
        testAllProvidersFailThrowsException();
        testProviderSummary();

        System.out.println("All 4 FailoverLlmProvider tests passed successfully!");
    }

    private static void testPrimarySucceeds() throws Exception {
        LlmProvider primary = new DummyProvider("primary-model", "Primary response", false);
        LlmProvider secondary = new DummyProvider("secondary-model", "Secondary response", false);

        FailoverLlmProvider failover = new FailoverLlmProvider(List.of(primary, secondary));
        LlmProvider.LlmResponse resp = failover.generate(List.of(new LlmProvider.Message("user", "hi")));

        assertCondition("primary-model".equals(failover.modelName()), "Model name should match primary");
        assertCondition("Primary response".equals(resp.content()), "Content should match primary");
        System.out.println("  ✓ testPrimarySucceeds passed");
    }

    private static void testFailoverToSecondaryOnException() throws Exception {
        LlmProvider failingPrimary = new DummyProvider("failing-cloud", "Error 429: Quota exceeded", true);
        LlmProvider workingSecondary = new DummyProvider("fallback-cloud", "Fallback response", false);

        FailoverLlmProvider failover = new FailoverLlmProvider(List.of(failingPrimary, workingSecondary));
        LlmProvider.LlmResponse resp = failover.generate(List.of(new LlmProvider.Message("user", "audit")));

        assertCondition("Fallback response".equals(resp.content()), "Should failover to secondary response");
        System.out.println("  ✓ testFailoverToSecondaryOnException passed");
    }

    private static void testAllProvidersFailThrowsException() {
        LlmProvider p1 = new DummyProvider("p1", "429 Rate limited", true);
        LlmProvider p2 = new DummyProvider("p2", "503 Service unavailable", true);

        FailoverLlmProvider failover = new FailoverLlmProvider(List.of(p1, p2));
        boolean threw = false;
        try {
            failover.generate(List.of(new LlmProvider.Message("user", "test")));
        } catch (Exception e) {
            threw = true;
            assertCondition(e.getMessage().contains("503"), "Should preserve last failure exception");
        }
        assertCondition(threw, "Must throw exception when all providers fail");
        System.out.println("  ✓ testAllProvidersFailThrowsException passed");
    }

    private static void testProviderSummary() {
        LlmProvider p1 = new OpenRouterClient("fake-key", "nvidia/nemotron-3.5-lightning:free");
        LlmProvider p2 = new GeminiClient("fake-key", "gemini-3.8-flash", java.time.Duration.ofMinutes(1));
        LlmProvider p3 = new OllamaClient("http://localhost:11434", "llama3.2", java.time.Duration.ofMinutes(1));

        FailoverLlmProvider failover = new FailoverLlmProvider(List.of(p1, p2, p3));
        String summary = failover.providerSummary();

        assertCondition(summary.contains("OpenRouter"), "Summary must mention OpenRouter");
        assertCondition(summary.contains("Gemini"), "Summary must mention Gemini");
        assertCondition(summary.contains("Ollama"), "Summary must mention Ollama");
        System.out.println("  ✓ testProviderSummary passed");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test assertion failed: " + message);
        }
    }

    private static class DummyProvider implements LlmProvider {
        private final String model;
        private final String content;
        private final boolean shouldThrow;

        DummyProvider(String model, String content, boolean shouldThrow) {
            this.model = model;
            this.content = content;
            this.shouldThrow = shouldThrow;
        }

        @Override
        public String modelName() {
            return model;
        }

        @Override
        public LlmResponse generate(List<Message> conversation) throws Exception {
            if (shouldThrow) {
                throw new RuntimeException(content);
            }
            return new LlmResponse(content, null, null, 100, 50);
        }
    }
}
