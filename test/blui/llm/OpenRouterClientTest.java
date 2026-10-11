package blui.llm;

import java.time.Duration;
import java.util.List;

/**
 * Verification test suite for OpenRouterClient (nvidia/nemotron-3.5-lightning:free & reasoning support).
 */
public class OpenRouterClientTest {

    public static void main(String[] args) {
        System.out.println("Running OpenRouterClient verification suite...");

        testDefaultModelAndReasoningEffort();
        testMissingApiKeyThrowsException();
        testPayloadSerialization();
        testThinkTagSanitization();
        testTokenUsageExtraction();

        System.out.println("All 5 OpenRouterClient tests passed successfully!");
    }

    private static void testDefaultModelAndReasoningEffort() {
        assertCondition("nvidia/nemotron-3.5-lightning:free".equals(OpenRouterClient.DEFAULT_MODEL),
                "DEFAULT_MODEL must be nvidia/nemotron-3.5-lightning:free");
        assertCondition("high".equals(OpenRouterClient.DEFAULT_REASONING_EFFORT),
                "DEFAULT_REASONING_EFFORT must be 'high'");

        OpenRouterClient client = new OpenRouterClient("dummy-key");
        assertCondition("nvidia/nemotron-3.5-lightning:free".equals(client.modelName()),
                "Client model name must match default model");
        assertCondition("high".equals(client.reasoningEffort()),
                "Client reasoning effort must match default effort");
        System.out.println("  ✓ testDefaultModelAndReasoningEffort passed");
    }

    private static void testMissingApiKeyThrowsException() {
        OpenRouterClient client = new OpenRouterClient("");
        boolean caught = false;
        try {
            client.generate(List.of(new LlmProvider.Message("user", "Hello")));
        } catch (IllegalStateException e) {
            caught = e.getMessage().contains("OPENROUTER_API_KEY");
        } catch (Exception e) {
            // unexpected
        }
        assertCondition(caught, "Must throw IllegalStateException mentioning OPENROUTER_API_KEY when key is missing");
        System.out.println("  ✓ testMissingApiKeyThrowsException passed");
    }

    private static void testPayloadSerialization() {
        OpenRouterClient client = new OpenRouterClient("test-key", "nvidia/nemotron-3.5-lightning:free", "high", Duration.ofSeconds(10));
        List<LlmProvider.Message> messages = List.of(
                new LlmProvider.Message("system", "You are Blui."),
                new LlmProvider.Message("user", "Audit code \"special & chars\"")
        );

        String json = client.buildRequestBodyForTesting(messages);
        assertCondition(json.contains("\"model\":\"nvidia/nemotron-3.5-lightning:free\""), "JSON must include model ID");
        assertCondition(json.contains("\"reasoning\":{\"effort\":\"high\"}"), "JSON must include reasoning effort");
        assertCondition(json.contains("\"role\":\"system\""), "JSON must include system role");
        assertCondition(json.contains("\"role\":\"user\""), "JSON must include user role");
        assertCondition(json.contains("Audit code \\\"special & chars\\\""), "User message must be properly escaped");
        System.out.println("  ✓ testPayloadSerialization passed");
    }

    private static void testThinkTagSanitization() {
        String rawWithThink = "<think>\nThinking about this deeply...\nStep 1: Check files.\n</think>\nGoal achieved: code audited.";
        String cleaned = OpenRouterClient.sanitizeContentForTesting(rawWithThink);
        assertCondition("Goal achieved: code audited.".equals(cleaned),
                "Must strip <think>...</think> tags completely: got '" + cleaned + "'");

        String rawWithThought = "<thought>Internal scratchpad</thought>Final answer.";
        String cleanedThought = OpenRouterClient.sanitizeContentForTesting(rawWithThought);
        assertCondition("Final answer.".equals(cleanedThought),
                "Must strip <thought>...</thought> tags completely: got '" + cleanedThought + "'");
        System.out.println("  ✓ testThinkTagSanitization passed");
    }

    private static void testTokenUsageExtraction() {
        String mockResponse = """
            {
              "id": "gen-12345",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "TOOL: FileTool\\nINPUT: list ."
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 142,
                "completion_tokens": 38,
                "total_tokens": 180
              }
            }
            """;

        LlmProvider.LlmResponse res = OpenRouterClient.parseResponseForTesting(mockResponse);
        assertCondition("FileTool".equals(res.toolName()), "Must parse tool name FileTool");
        assertCondition("list .".equals(res.toolInput()), "Must parse tool input 'list .'");
        assertCondition(res.promptTokens() == 142, "Must extract prompt_tokens = 142");
        assertCondition(res.completionTokens() == 38, "Must extract completion_tokens = 38");
        assertCondition(res.totalTokens() == 180, "Must calculate totalTokens = 180");
        System.out.println("  ✓ testTokenUsageExtraction passed");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }
}
