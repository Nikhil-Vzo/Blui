package blui.swarm;

import java.util.Objects;

/**
 * Immutable evaluation result produced by an individual specialist agent in the swarm.
 */
public record SwarmResult(
    String agentName,
    String role,
    String findings,
    int tokens,
    long durationMs,
    boolean success
) {
    public SwarmResult {
        Objects.requireNonNull(agentName, "agentName cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        if (findings == null || findings.isBlank()) {
            findings = success ? "Evaluation completed successfully." : "Execution halted without findings.";
        }
    }

    public static SwarmResult failure(String agentName, String role, String error, long durationMs) {
        String msg = (error != null && !error.isBlank()) ? error : "Execution failed or was cancelled.";
        return new SwarmResult(agentName, role, msg, 0, durationMs, false);
    }
}
