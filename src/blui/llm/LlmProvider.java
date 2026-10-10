package blui.llm;

import java.util.List;

/**
 * Common contract for all AI model providers (Gemini, Ollama, Claude).
 * Decouples the engine from specific cloud APIs or local binaries.
 */
public interface LlmProvider {

    record Message(String role, String content) {}

    record LlmResponse(
            String content,
            String toolName,
            String toolInput,
            int promptTokens,
            int completionTokens
    ) {
        public boolean hasToolCall() {
            return toolName != null && !toolName.isBlank();
        }

        public int totalTokens() {
            return promptTokens + completionTokens;
        }
    }

    /**
     * The model identifier (e.g. "gemini-2.5-flash", "llama3.2").
     */
    String modelName();

    /**
     * Sends the conversation history to the model and returns its response with real token telemetry.
     */
    LlmResponse generate(List<Message> conversation) throws Exception;
}
