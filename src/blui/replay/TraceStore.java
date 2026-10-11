package blui.replay;

import blui.llm.MinimalJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Disk persistence ledger for agent flight traces.
 * Saves and loads execution traces to and from .blui/traces/ with zero external dependencies.
 */
public final class TraceStore {

    public record TraceSummary(
            String id,
            String goal,
            long timestamp,
            int stepCount,
            String status
    ) {}

    private final Path storageDir;

    public TraceStore() {
        this(Path.of(".blui", "traces"));
    }

    public TraceStore(Path storageDir) {
        this.storageDir = storageDir;
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize trace storage directory: " + storageDir, e);
        }
    }

    /**
     * Persists an AgentTrace run to disk as a JSON flight ledger.
     */
    public Path save(String traceId, String goal, AgentTrace trace) throws IOException {
        String safeId = (traceId != null && !traceId.isBlank())
                ? traceId
                : "trace_" + System.currentTimeMillis();

        Path file = storageDir.resolve(safeId + ".json");

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"traceId\": \"").append(MinimalJson.escape(safeId)).append("\",\n");
        json.append("  \"goal\": \"").append(MinimalJson.escape(goal != null ? goal : "Unknown")).append("\",\n");
        json.append("  \"timestamp\": ").append(System.currentTimeMillis()).append(",\n");
        json.append("  \"isoDate\": \"").append(Instant.now().toString()).append("\",\n");
        json.append("  \"stepCount\": ").append(trace.size()).append(",\n");
        json.append("  \"status\": \"").append(trace.size() > 0 ? "RECORDED" : "EMPTY").append("\",\n");
        json.append("  \"totalTokens\": ").append(trace.totalTokensUsed()).append(",\n");
        json.append("  \"steps\": [\n");

        List<StepTrace> steps = trace.steps();
        for (int i = 0; i < steps.size(); i++) {
            StepTrace step = steps.get(i);
            json.append("    {\n");
            json.append("      \"stepNumber\": ").append(step.stepNumber()).append(",\n");
            json.append("      \"timestampMs\": ").append(step.timestampMs()).append(",\n");
            json.append("      \"tokenUsed\": ").append(step.tokenUsed()).append(",\n");
            json.append("      \"stateType\": \"").append(step.state().getClass().getSimpleName()).append("\"\n");
            json.append("    }").append(i < steps.size() - 1 ? "," : "").append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        Files.writeString(file, json.toString());
        return file;
    }

    /**
     * Lists all saved traces on disk, newest first.
     */
    public List<TraceSummary> listTraces() {
        if (!Files.exists(storageDir)) return List.of();

        List<TraceSummary> summaries = new ArrayList<>();
        try (Stream<Path> files = Files.list(storageDir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    String content = Files.readString(p);
                    String id = MinimalJson.extractString(content, "traceId");
                    String goal = MinimalJson.extractString(content, "goal");
                    String status = MinimalJson.extractString(content, "status");
                    int steps = MinimalJson.extractInt(content, "stepCount", 0);
                    int ts = MinimalJson.extractInt(content, "timestamp", 0);

                    if (id == null) {
                        id = p.getFileName().toString().replace(".json", "");
                    }
                    summaries.add(new TraceSummary(
                            id,
                            goal != null ? goal : "(no goal)",
                            ts > 0 ? ts : Files.getLastModifiedTime(p).toMillis(),
                            steps,
                            status != null ? status : "UNKNOWN"
                    ));
                } catch (Exception ignored) {}
            });
        } catch (IOException e) {
            return List.of();
        }

        summaries.sort(Comparator.comparingLong(TraceSummary::timestamp).reversed());
        return summaries;
    }

    /**
     * Reads the raw JSON ledger of a specific trace from disk.
     */
    public String readTraceJson(String traceId) throws IOException {
        Path file = storageDir.resolve(traceId + ".json");
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Trace not found: " + traceId);
        }
        return Files.readString(file);
    }

    public Path getStorageDir() {
        return storageDir;
    }
}
