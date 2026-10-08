package blui.governor;

import blui.core.AgentState;
import java.util.Objects;

/**
 * Composite gatekeeper that enforces budget ceilings and breaks infinite loops
 * before the agent executes state transitions or tool calls.
 */
public class Governor {

    public static class BudgetExceededException extends RuntimeException {
        public BudgetExceededException(String message) {
            super(message);
        }
    }

    public static class LoopDetectedException extends RuntimeException {
        public LoopDetectedException(String message) {
            super(message);
        }
    }

    private final Budget budget;
    private final CycleDetector cycleDetector;

    public Governor(Budget budget, CycleDetector cycleDetector) {
        this.budget = Objects.requireNonNull(budget, "budget cannot be null");
        this.cycleDetector = Objects.requireNonNull(cycleDetector, "cycleDetector cannot be null");
    }

    /**
     * Inspects the agent state before execution.
     * Halts immediately if the financial or token budget has been breached.
     */
    public void inspectStep(AgentState state) {
        Objects.requireNonNull(state, "state cannot be null");
        if (budget.isExceeded()) {
            throw new BudgetExceededException(
                "Hard budget ceiling reached: used " + budget.usedTokens()
                + " tokens (cost: $" + String.format("%.4f", budget.currentCostUsd()) + ")"
            );
        }
    }

    /**
     * Records a tool call and verifies that the agent has not entered
     * an infinite loop.
     */
    public void recordAndVerifyToolCall(String toolName, String input) {
        cycleDetector.recordCall(toolName, input);
        if (cycleDetector.isLoopDetected()) {
            throw new LoopDetectedException(
                "Infinite loop detected: tool '" + toolName + "' repeated identical execution."
            );
        }
    }

    public Budget budget() {
        return budget;
    }

    public CycleDetector cycleDetector() {
        return cycleDetector;
    }
}
