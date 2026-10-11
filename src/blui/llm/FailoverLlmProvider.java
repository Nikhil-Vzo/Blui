package blui.llm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resilient LLM provider wrapper that automatically fails over across multiple configured providers
 * (e.g. OpenRouter -> Gemini -> local Ollama) when rate limits, quota exhaustion (HTTP 429),
 * authentication errors, or network outages occur.
 */
public final class FailoverLlmProvider implements LlmProvider {

    private final List<LlmProvider> providers;

    public FailoverLlmProvider(List<LlmProvider> providers) {
        Objects.requireNonNull(providers, "providers list cannot be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("providers list cannot be empty");
        }
        this.providers = new ArrayList<>(providers);
    }

    public List<LlmProvider> providers() {
        return Collections.unmodifiableList(providers);
    }

    public LlmProvider primary() {
        return providers.get(0);
    }

    public String providerSummary() {
        List<String> names = new ArrayList<>();
        for (LlmProvider p : providers) {
            String kind = (p instanceof OpenRouterClient) ? "OpenRouter" :
                          (p instanceof GeminiClient) ? "Gemini" : "Ollama";
            names.add(p.modelName() + " [" + kind + "]");
        }
        return String.join(" -> ", names);
    }

    @Override
    public String modelName() {
        return primary().modelName();
    }

    @Override
    public LlmResponse generate(List<Message> conversation) throws Exception {
        Exception lastError = null;
        for (int i = 0; i < providers.size(); i++) {
            LlmProvider current = providers.get(i);
            try {
                return current.generate(conversation);
            } catch (Exception ex) {
                lastError = ex;
                if (i < providers.size() - 1) {
                    LlmProvider next = providers.get(i + 1);
                    String errMsg = ex.getMessage() != null && !ex.getMessage().isBlank() ? ex.getMessage() : ex.getClass().getSimpleName();
                    System.err.println("[SWARM FAILOVER] " + current.modelName() + " unavailable (" + errMsg + "). Failing over to " + next.modelName() + "...");
                }
            }
        }
        throw lastError != null ? lastError : new RuntimeException("All configured providers failed");
    }
}
