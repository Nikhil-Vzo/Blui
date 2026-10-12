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
            Path targetPath = null;
            if (goal != null) {
                var pattern = java.util.regex.Pattern.compile("(?:\\.{1,2}[/\\\\]|[a-zA-Z0-9_.-]+[/\\\\])[a-zA-Z0-9_.-]+|(?:\\./|\\.\\./|src|test|bin)[a-zA-Z0-9_./\\\\-]*");
                var matcher = pattern.matcher(goal);
                while (matcher.find()) {
                    String candidate = matcher.group().trim().replaceAll("^['\"]|['\"]$", "");
                    try {
                        Path p = Path.of(candidate);
                        if (Files.exists(p) && !candidate.equals(".") && !candidate.equals("..")) {
                            targetPath = p;
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (targetPath == null) {
                targetPath = Path.of("src");
            }

            if (Files.exists(targetPath)) {
                if (Files.isDirectory(targetPath)) {
                    final Path baseDir = targetPath;
                    try (Stream<Path> stream = Files.walk(baseDir)) {
                        List<String> javaFiles = stream
                            .filter(Files::isRegularFile)
                            .map(p -> baseDir.relativize(p).toString().replace('\\', '/'))
                            .filter(s -> s.endsWith(".java"))
                            .sorted()
                            .toList();
                        String displayPath = targetPath.toString().replace('\\', '/');
                        if (!displayPath.startsWith("./")) displayPath = "./" + displayPath;
                        if (!javaFiles.isEmpty()) {
                            sb.append("\nCodebase Inventory under ").append(displayPath).append(" (").append(javaFiles.size()).append(" Java 24 files):\n");
                            sb.append(String.join(", ", javaFiles));
                        }
                    }
                } else if (Files.isRegularFile(targetPath)) {
                    String displayPath = targetPath.toString().replace('\\', '/');
                    if (!displayPath.startsWith("./")) displayPath = "./" + displayPath;
                    sb.append("\nTarget File: ").append(displayPath).append(" (").append(Files.size(targetPath)).append(" bytes)");
                }
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }
}
