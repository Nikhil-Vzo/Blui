package blui.swarm;

import java.util.Objects;

/**
 * Immutable task specification for execution across concurrent swarm specialist agents.
 */
public record SwarmTask(
    String id,
    String description,
    String target
) {
    public SwarmTask {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(description, "description cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
    }

    public static SwarmTask of(String goal) {
        String id = "task_" + System.currentTimeMillis();
        return new SwarmTask(id, goal, goal);
    }
}
