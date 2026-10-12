package blui.core;

import blui.governor.Budget;
import blui.governor.CycleDetector;
import blui.governor.Governor;
import blui.loom.LoomRunner;
import blui.loom.ToolExecution;
import blui.replay.AgentTrace;
import blui.replay.StepTrace;

import java.time.Duration;
import java.util.List;

/**
 * Pure JDK verification suite for BluiEngine orchestrator.
 * Zero external test framework dependencies.
 */
public class BluiEngineTest {

    public static void main(String[] args) {
        System.out.println("Running BluiEngine verification suite...");

        testSuccessfulGoalCompletion();
        testGovernorBudgetHaltsExecution();
        testGovernorCycleDetectionHaltsExecution();
        testMaxStepsLimit();
        testBudgetIntegerOverflowCeiling();
        testStepExecutionTripsBudgetGovernor();
        testCycleDetectorAlternatingPatterns();
        testLoomRunnerGlobalSlaDeadline();
        testFileToolSymlinkTraversal();
        testHttpToolSsrfProtection();
        testShellToolAsyncDrainTimeout();

        System.out.println("All 11 BluiEngine tests passed successfully!");
    }

    private static void testSuccessfulGoalCompletion() {
        Governor governor = new Governor(new Budget(5000, 1.00, 0.000003), new CycleDetector(3));
        LoomRunner runner = new LoomRunner();
        BluiEngine engine = new BluiEngine(governor, runner, Duration.ofSeconds(2), 10);

        Tool dummyTool = input -> new ToolResults("Dummy", "echo: " + input, true, 5);

        AgentTrace trace = engine.run("Find docs", (state, history) -> {
            if (history.size() <= 2) {
                return List.of(new ToolExecution(dummyTool, "query"));
            }
            return List.of();
        });

        assertCondition(trace.size() >= 3, "Trace should record initialization, thinking, tools, and completion");
        StepTrace lastStep = trace.steps().get(trace.size() - 1);
        assertCondition(lastStep.state() instanceof AgentState.Completed, "Final state must be Completed");
        System.out.println("  ✓ testSuccessfulGoalCompletion passed");
    }

    private static void testGovernorBudgetHaltsExecution() {
        // Budget limited to 100 tokens, but Thinking consumes 150 tokens
        Governor governor = new Governor(new Budget(100, 1.00, 0.000003), new CycleDetector(3));
        governor.budget().recordUsage(150); // already exceeded

        LoomRunner runner = new LoomRunner();
        BluiEngine engine = new BluiEngine(governor, runner, Duration.ofSeconds(2), 10);

        AgentTrace trace = engine.run("Expensive query", (state, history) -> List.of());

        StepTrace lastStep = trace.steps().get(trace.size() - 1);
        assertCondition(lastStep.state() instanceof AgentState.Failed, "State must be Failed when budget exceeded");
        AgentState.Failed failedState = (AgentState.Failed) lastStep.state();
        assertCondition(failedState.reason().contains("Governor halted execution"), "Reason must mention governor halt");
        System.out.println("  ✓ testGovernorBudgetHaltsExecution passed");
    }

    private static void testGovernorCycleDetectionHaltsExecution() {
        // Cycle threshold = 2
        Governor governor = new Governor(new Budget(5000, 1.00, 0.000003), new CycleDetector(2));
        LoomRunner runner = new LoomRunner();
        BluiEngine engine = new BluiEngine(governor, runner, Duration.ofSeconds(2), 10);

        Tool loopTool = input -> new ToolResults("LoopTool", "val", true, 5);

        // Planner returns the identical tool call every step
        AgentTrace trace = engine.run("Stuck in loop", (state, history) -> {
            return List.of(new ToolExecution(loopTool, "identical-input"));
        });

        StepTrace lastStep = trace.steps().get(trace.size() - 1);
        assertCondition(lastStep.state() instanceof AgentState.Failed, "Final state must be Failed when loop detected");
        AgentState.Failed failedState = (AgentState.Failed) lastStep.state();
        assertCondition(failedState.reason().contains("Infinite loop detected"), "Reason must identify infinite loop");
        System.out.println("  ✓ testGovernorCycleDetectionHaltsExecution passed");
    }

    private static void testMaxStepsLimit() {
        Governor governor = new Governor(new Budget(100000, 10.00, 0.000003), new CycleDetector(50));
        LoomRunner runner = new LoomRunner();
        // maxSteps = 2
        BluiEngine engine = new BluiEngine(governor, runner, Duration.ofSeconds(2), 2);

        int[] count = new int[]{0};
        Tool dummyTool = input -> new ToolResults("Worker", "val", true, 5);

        AgentTrace trace = engine.run("Endless loop", (state, history) -> {
            count[0]++;
            return List.of(new ToolExecution(dummyTool, "input-" + count[0]));
        });

        StepTrace lastStep = trace.steps().get(trace.size() - 1);
        assertCondition(lastStep.state() instanceof AgentState.Failed, "Final state must be Failed when max steps exceeded");
        AgentState.Failed failedState = (AgentState.Failed) lastStep.state();
        assertCondition(failedState.reason().contains("Max steps ceiling exceeded"), "Reason must identify max steps");
        System.out.println("  ✓ testMaxStepsLimit passed");
    }

    private static void testBudgetIntegerOverflowCeiling() {
        Budget budget = new Budget(1000, 1.00, 0.000003);
        budget.recordUsage(Integer.MAX_VALUE - 10);
        assertCondition(budget.isExceeded(), "Budget must be exceeded near MAX_VALUE");

        // Attempt integer overflow bypass
        budget.recordUsage(100);
        assertCondition(budget.usedTokens() == Integer.MAX_VALUE, "usedTokens must saturate at Integer.MAX_VALUE");
        assertCondition(budget.isExceeded(), "isExceeded must remain true after integer overflow attempt");
        assertCondition(budget.remainingTokens() == 0, "remainingTokens must be 0 when saturated");
        System.out.println("  ✓ testBudgetIntegerOverflowCeiling passed");
    }

    private static void testStepExecutionTripsBudgetGovernor() {
        // Budget limited to 20 tokens
        Governor governor = new Governor(new Budget(20, 1.00, 0.000003), new CycleDetector(5));
        LoomRunner runner = new LoomRunner();
        BluiEngine engine = new BluiEngine(governor, runner, Duration.ofSeconds(2), 10);

        // Tool output of 200 chars produces ~50 tokens, tripping the 20-token budget during step execution
        Tool largeTool = input -> new ToolResults("LargeTool", "a".repeat(200), true, 5);

        AgentTrace trace = engine.run("Run large tool", (state, history) -> {
            return List.of(new ToolExecution(largeTool, "input"));
        });

        StepTrace lastStep = trace.steps().get(trace.size() - 1);
        assertCondition(lastStep.state() instanceof AgentState.Failed, "State must be Failed when budget breached during step");
        AgentState.Failed failedState = (AgentState.Failed) lastStep.state();
        assertCondition(failedState.reason().contains("Hard budget ceiling reached"), "Reason must mention budget ceiling reached");
        assertCondition(trace.totalTokensUsed() > 0, "Trace must reflect non-zero tokens consumed");
        System.out.println("  ✓ testStepExecutionTripsBudgetGovernor passed");
    }

    private static void testCycleDetectorAlternatingPatterns() {
        CycleDetector detector = new CycleDetector(2);
        // Normalized trimming: extra spaces should normalize to identical call
        detector.recordCall("FileTool", "  read   foo.txt  ");
        detector.recordCall("ShellTool", "echo 1");
        assertCondition(!detector.isLoopDetected(), "No loop after 2 alternating calls");

        detector.recordCall("FileTool", "read foo.txt");
        detector.recordCall("ShellTool", "echo 1");
        assertCondition(detector.isLoopDetected(), "Alternating sequence (A, B, A, B) with normalized whitespace must detect loop");
        System.out.println("  ✓ testCycleDetectorAlternatingPatterns passed");
    }

    private static void testLoomRunnerGlobalSlaDeadline() {
        LoomRunner runner = new LoomRunner();
        // 2 tasks that each sleep for 300ms. With a 100ms global SLA, both must time out and return within ~250ms total
        Tool sleepingTool = input -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
            return new ToolResults("Sleeper", "done", true, 300);
        };

        long start = System.currentTimeMillis();
        List<ToolResults> results = runner.runConcurrent(
                List.of(new ToolExecution(sleepingTool, "1"), new ToolExecution(sleepingTool, "2")),
                Duration.ofMillis(100)
        );
        long elapsed = System.currentTimeMillis() - start;

        assertCondition(results.size() == 2, "Must return 2 results");
        assertCondition(elapsed < 400, "Global SLA deadline must terminate within ~100-250ms, not 2 * 100ms: elapsed=" + elapsed);
        assertCondition(!results.get(0).isSuccess() && results.get(0).output().contains("timed out"), "First result must time out");
        assertCondition(!results.get(1).isSuccess() && results.get(1).output().contains("timed out"), "Second result must time out");
        System.out.println("  ✓ testLoomRunnerGlobalSlaDeadline passed");
    }

    private static void testFileToolSymlinkTraversal() {
        try {
            java.nio.file.Path tempRoot = java.nio.file.Files.createTempDirectory("blui_filetool_test_");
            java.nio.file.Path secretDir = java.nio.file.Files.createTempDirectory("blui_secret_");
            try {
                java.nio.file.Path secretFile = secretDir.resolve("secret.txt");
                java.nio.file.Files.writeString(secretFile, "TOP_SECRET_CREDENTIALS");

                blui.tools.FileTool tool = new blui.tools.FileTool(tempRoot);

                // 1. Upward path traversal
                blui.core.ToolResults upRes = tool.execute("read ../../outside.txt");
                assertCondition(!upRes.isSuccess() && upRes.output().contains("Security Violation"),
                        "FileTool must block ../ traversal");

                // 2. Symlink escape (if platform supports symlinks)
                try {
                    java.nio.file.Path symlinkPath = tempRoot.resolve("symlink_escape");
                    java.nio.file.Files.createSymbolicLink(symlinkPath, secretFile);
                    blui.core.ToolResults linkRes = tool.execute("read symlink_escape");
                    assertCondition(!linkRes.isSuccess() && linkRes.output().contains("Security Violation"),
                            "FileTool must block symlink escape");
                } catch (UnsupportedOperationException | java.io.IOException e) {
                    // Symlinks may require elevated privileges on some Windows configurations
                }

                // 3. Normal safe read/write inside workspace
                blui.core.ToolResults writeRes = tool.execute("write safe.txt hello_world");
                assertCondition(writeRes.isSuccess(), "Writing safe file must succeed");
                blui.core.ToolResults readRes = tool.execute("read safe.txt");
                assertCondition(readRes.isSuccess() && "hello_world".equals(readRes.output()), "Reading safe file must succeed");

                System.out.println("  ✓ testFileToolSymlinkTraversal passed");
            } finally {
                cleanDir(tempRoot);
                cleanDir(secretDir);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to run testFileToolSymlinkTraversal", e);
        }
    }

    private static void testHttpToolSsrfProtection() {
        blui.tools.HttpTool tool = new blui.tools.HttpTool(Duration.ofSeconds(1));

        // 1. Loopback blocking
        blui.core.ToolResults r1 = tool.execute("get http://127.0.0.1:8080/admin");
        assertCondition(!r1.isSuccess() && r1.output().contains("Security Violation"), "Must block 127.0.0.1");

        blui.core.ToolResults r2 = tool.execute("get http://localhost/metrics");
        assertCondition(!r2.isSuccess() && r2.output().contains("Security Violation"), "Must block localhost");

        // 2. Cloud metadata IP blocking
        blui.core.ToolResults r3 = tool.execute("get http://169.254.169.254/latest/meta-data/");
        assertCondition(!r3.isSuccess() && r3.output().contains("Security Violation"), "Must block AWS/GCP metadata IP 169.254.169.254");

        // 3. Private IP 10.0.0.0/8 and 192.168.0.0/16
        blui.core.ToolResults r4 = tool.execute("get http://10.0.0.1/");
        assertCondition(!r4.isSuccess() && r4.output().contains("Security Violation"), "Must block 10.0.0.1 private IP");

        blui.core.ToolResults r5 = tool.execute("get http://192.168.1.1/router");
        assertCondition(!r5.isSuccess() && r5.output().contains("Security Violation"), "Must block 192.168.1.1 private IP");

        System.out.println("  ✓ testHttpToolSsrfProtection passed");
    }

    private static void testShellToolAsyncDrainTimeout() {
        blui.tools.ShellTool tool = new blui.tools.ShellTool(Duration.ofMillis(300));
        long start = System.currentTimeMillis();

        // Run infinite output loop or sleep that would deadlock if drained synchronously before wait
        String isWindows = System.getProperty("os.name", "").toLowerCase();
        String cmd = isWindows.contains("win")
                ? "powershell -Command \"while ($true) { Write-Output 'flood' }\""
                : "yes flood";

        blui.core.ToolResults res = tool.execute(cmd);
        long elapsed = System.currentTimeMillis() - start;

        assertCondition(!res.isSuccess(), "Infinite output process must not succeed");
        assertCondition(res.output().contains("timed out"), "Output must report command timed out: " + res.output());
        assertCondition(elapsed < 3000, "Timeout must cleanly terminate runaway process within reasonable grace period: elapsed=" + elapsed);

        System.out.println("  ✓ testShellToolAsyncDrainTimeout passed");
    }

    private static void cleanDir(java.nio.file.Path dir) {
        if (!java.nio.file.Files.exists(dir)) return;
        try (var stream = java.nio.file.Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .forEach(p -> {
                      try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {}
                  });
        } catch (Exception ignored) {}
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test assertion failed: " + message);
        }
    }
}
