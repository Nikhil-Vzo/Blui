package blui.tools;

import blui.core.ToolResults;
import blui.core.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Sandboxed filesystem tool.
 * Jails all file read, write, append, and list operations to the workspace root.
 */

public final class FileTool implements Tool {
    private final Path workspaceRoot;
    public FileTool() {
        this(Path.of(".").toAbsolutePath().normalize());
    }
    public FileTool(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
    }
    @Override
    public String name() {
        return "FileTool";
    }
    @Override
    public ToolResults execute(String input) {
        long start = System.currentTimeMillis();
        if (input == null || input.isBlank()) {
            return new ToolResults(name(), "Error: Command cannot be empty. Usage: read|write|append|list <path> [content]", false, 0);
        }
        String[] parts = input.strip().split("\\s+", 3);
        String action = parts[0].toLowerCase();
        try {
            return switch (action) {
                case "read" -> {
                    if (parts.length < 2) yield error("Usage: read <file-path>", start);
                    Path target = resolveSafe(parts[1]);
                    if (!Files.exists(target)) yield error("File not found: " + parts[1], start);
                    String content = Files.readString(target);
                    yield success(content, start);
                }
                case "write" -> {
                    if (parts.length < 3) yield error("Usage: write <file-path> <content>", start);
                    Path target = resolveSafe(parts[1]);
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Files.writeString(target, parts[2]);
                    yield success("File written successfully: " + parts[1], start);
                }
                case "append" -> {
                    if (parts.length < 3) yield error("Usage: append <file-path> <content>", start);
                    Path target = resolveSafe(parts[1]);
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Files.writeString(target, parts[2], StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    yield success("Content appended successfully to: " + parts[1], start);
                }
                case "list" -> {
                    String subPath = parts.length > 1 ? parts[1] : ".";
                    Path target = resolveSafe(subPath);
                    if (!Files.exists(target) || !Files.isDirectory(target)) {
                        yield error("Directory not found: " + subPath, start);
                    }
                    try (Stream<Path> stream = Files.list(target)) {
                        String listing = stream.map(p -> (Files.isDirectory(p) ? "[DIR]  " : "[FILE] ") + p.getFileName().toString())
                                .collect(Collectors.joining(System.lineSeparator()));
                        yield success(listing.isEmpty() ? "(empty directory)" : listing, start);
                    }
                }
                default -> error("Unknown action '" + action + "'. Supported: read, write, append, list", start);
            };
        } catch (SecurityException se) {
            return error("Security Violation: " + se.getMessage(), start);
        } catch (IOException ioe) {
            return error("I/O Error: " + ioe.getMessage(), start);
        } catch (Exception e) {
            return error("Execution failed: " + e.getMessage(), start);
        }
    }
    /**
     * Resolves the requested relative path and verifies it cannot escape the workspace jail.
     */
    private Path resolveSafe(String relativePath) {
        Path resolved = workspaceRoot.resolve(relativePath).normalize().toAbsolutePath();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path traversal blocked: '" + relativePath + "' escapes workspace root.");
        }
        return resolved;
    }
    private ToolResults success(String output, long startTime) {
        return new ToolResults(name(), output, true, System.currentTimeMillis() - startTime);
    }
    private ToolResults error(String message, long startTime) {
        return new ToolResults(name(), "Error: " + message, false, System.currentTimeMillis() - startTime);
    }
}
