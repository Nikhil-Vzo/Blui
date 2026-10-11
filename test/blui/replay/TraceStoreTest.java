package blui.replay;

import blui.core.AgentState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Pure JDK verification suite for TraceStore disk persistence.
 * Zero external test framework dependencies.
 */
public class TraceStoreTest {

    public static void main(String[] args) throws IOException {
        System.out.println("Running TraceStore verification suite...");

        Path tempDir = Files.createTempDirectory("blui_trace_test_");
        try {
            TraceStore store = new TraceStore(tempDir);

            testSaveAndReadTrace(store);
            testListTraces(store);

            System.out.println("All TraceStore tests passed successfully!");
        } finally {
            // Clean up temporary test directory
            try (var stream = Files.walk(tempDir)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> {
                          try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                      });
            }
        }
    }

    private static void testSaveAndReadTrace(TraceStore store) throws IOException {
        AgentTrace trace = new AgentTrace();
        trace.append(StepTrace.of(0, new AgentState.Initialized("Test Goal"), 0));
        trace.append(StepTrace.of(1, new AgentState.Thinking("Test Goal", 1), 10));
        trace.append(StepTrace.of(2, new AgentState.Completed("Goal Done", 2), 0));

        Path savedPath = store.save("test_run_1", "Test Goal", trace);
        assertCondition(Files.exists(savedPath), "Trace file must exist on disk");

        String rawJson = store.readTraceJson("test_run_1");
        assertCondition(rawJson.contains("\"traceId\": \"test_run_1\""), "JSON must contain traceId");
        assertCondition(rawJson.contains("\"goal\": \"Test Goal\""), "JSON must contain goal");
        assertCondition(rawJson.contains("\"stepCount\": 3"), "JSON must contain correct step count");
        assertCondition(rawJson.contains("\"totalTokens\": 10"), "JSON must contain token count");
        System.out.println("  ✓ testSaveAndReadTrace passed");
    }

    private static void testListTraces(TraceStore store) {
        List<TraceStore.TraceSummary> traces = store.listTraces();
        assertCondition(!traces.isEmpty(), "listTraces should return at least one trace");
        assertCondition("test_run_1".equals(traces.get(0).id()), "First trace id should match 'test_run_1'");
        assertCondition("Test Goal".equals(traces.get(0).goal()), "Trace goal should match 'Test Goal'");
        assertCondition(traces.get(0).stepCount() == 3, "Trace step count should be 3");
        System.out.println("  ✓ testListTraces passed");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            System.err.println("TEST ASSERTION FAILED: " + message);
            System.exit(1);
        }
    }
}
