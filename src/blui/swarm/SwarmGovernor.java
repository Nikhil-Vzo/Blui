package blui.swarm;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe cross-agent governor enforcing atomic token and dollar budget caps across concurrent swarm agents.
 * Trips circuit breaker instantly if total cumulative usage across all virtual threads reaches or crosses limits.
 */
public class SwarmGovernor {

    public static class SwarmBudgetExceededException extends RuntimeException {
        public SwarmBudgetExceededException(String message) {
            super(message);
        }
    }

    private final int maxTokens;
    private final double maxCostUsd;
    private final double costPerTokenUsd;
    private final AtomicInteger usedTokens;
    private final AtomicBoolean tripped;

    public SwarmGovernor(int maxTokens, double maxCostUsd, double costPerTokenUsd) {
        if (maxTokens <= 0 || maxCostUsd <= 0.0 || costPerTokenUsd < 0.0) {
            throw new IllegalArgumentException("Budget limits must be positive values");
        }
        this.maxTokens = maxTokens;
        this.maxCostUsd = maxCostUsd;
        this.costPerTokenUsd = costPerTokenUsd;
        this.usedTokens = new AtomicInteger(0);
        this.tripped = new AtomicBoolean(false);
    }

    public static SwarmGovernor standard() {
        return new SwarmGovernor(50000, 1.00, 0.000003);
    }

    /**
     * Atomically adds token usage from an agent.
     * If the hard cap is reached or exceeded, trips the circuit breaker immediately.
     */
    public void recordUsage(int tokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens cannot be negative");
        }
        int total = this.usedTokens.addAndGet(tokens);
        double cost = total * costPerTokenUsd;
        if (total >= maxTokens || cost >= maxCostUsd) {
            tripped.set(true);
        }
    }

    /**
     * Verifies that the circuit breaker has not been tripped and budget is within limits.
     * Throws SwarmBudgetExceededException if tripped or exceeded.
     */
    public void checkBudget() {
        if (tripped.get() || usedTokens.get() >= maxTokens || currentCostUsd() >= maxCostUsd) {
            tripped.set(true);
            throw new SwarmBudgetExceededException(
                String.format(Locale.US, "Swarm budget hard cap reached: used %d tokens (max: %d, cost: $%.4f)",
                    usedTokens.get(), maxTokens, currentCostUsd())
            );
        }
    }

    public void inspect() {
        checkBudget();
    }

    public void trip() {
        tripped.set(true);
    }

    public boolean isTripped() {
        return tripped.get() || usedTokens.get() >= maxTokens || currentCostUsd() >= maxCostUsd;
    }

    public int usedTokens() {
        return usedTokens.get();
    }

    public int maxTokens() {
        return maxTokens;
    }

    public double maxCostUsd() {
        return maxCostUsd;
    }

    public double costPerTokenUsd() {
        return costPerTokenUsd;
    }

    public int remainingTokens() {
        return Math.max(0, maxTokens - usedTokens.get());
    }

    public double currentCostUsd() {
        return usedTokens.get() * costPerTokenUsd;
    }
}
