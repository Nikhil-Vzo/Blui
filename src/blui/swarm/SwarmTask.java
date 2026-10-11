package blui.swarm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

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
        String scope = resolveTargetScope(goal);
        return new SwarmTask(id, goal, scope);
    }

    public static String resolveTargetScope(String goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Pure Java 24 Runtime (OpenJDK 24, Zero Dependencies, Windows/POSIX).");
        try {
            Path src = Path.of("src");
            if (Files.exists(src) && Files.isDirectory(src)) {
                try (Stream<Path> stream = Files.walk(src)) {
                    List<String> javaFiles = stream
                        .filter(Files::isRegularFile)
                        .map(p -> src.relativize(p).toString().replace('\\', '/'))
                        .filter(s -> s.endsWith(".java"))
                        .sorted()
                        .toList();
                    if (!javaFiles.isEmpty()) {
                        sb.append("\nCodebase Inventory under ./src (").append(javaFiles.size()).append(" Java 24 files):\n");
                        sb.append(String.join(", ", javaFiles));
                    }
                }
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }
}
