package blui.replay;

import blui.core.AgentState;
import java.util.Objects;

/**
 * An immutable telemetry snapshot of a single agent step.
 */
public record StepTrace(
    int stepNumber,
    AgentState state,
    long timestampMs,
    int tokenUsed
) {

    public StepTrace {
        Objects.requireNonNull(state, "state cannot be null");
        if (stepNumber < 0) {
            throw new IllegalArgumentException("stepNumber cannot be negative");
        }
        if (tokenUsed < 0) {
            throw new IllegalArgumentException("tokenUsed cannot be negative");
        }
    }

    /**
     * Convenience factory that automatically timestamps the current step.
     */
    public static StepTrace of(int stepNumber, AgentState state, int tokenUsed) {
        return new StepTrace(stepNumber, state, System.currentTimeMillis(), tokenUsed);
    }
}
