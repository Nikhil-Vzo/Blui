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

        System.out.println("All 4 BluiEngine tests passed successfully!");
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

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test assertion failed: " + message);
        }
    }
}
