package blui.core;

import blui.governor.Governor;
import blui.loom.LoomRunner;
import blui.loom.ToolExecution;
import blui.replay.AgentTrace;
import blui.replay.StepTrace;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The core orchestrator engine wiring state transitions, safety governors,
 * virtual-thread concurrency, and time-travel replay logging.
 */
public class BluiEngine {

    @FunctionalInterface
    public interface StepPlanner {
        /**
         * Given the current state and step history, decides the next batch of tool executions.
         * Return an empty list if the agent has reached a final answer.
         */
        List<ToolExecution> planNextStep(AgentState state, AgentTrace history);
    }

    private final Governor governor;
    private final LoomRunner loomRunner;
    private final Duration toolTimeout;
    private final int maxSteps;

    public BluiEngine(Governor governor, LoomRunner loomRunner, Duration toolTimeout, int maxSteps) {
        this.governor = Objects.requireNonNull(governor, "governor cannot be null");
        this.loomRunner = Objects.requireNonNull(loomRunner, "loomRunner cannot be null");
        this.toolTimeout = Objects.requireNonNull(toolTimeout, "toolTimeout cannot be null");
        if (maxSteps <= 0) {
            throw new IllegalArgumentException("maxSteps must be positive");
        }
        this.maxSteps = maxSteps;
    }

    /**
     * Executes the agent loop until completion, failure, or maxSteps exceeded.
     */
    public AgentTrace run(String goal, StepPlanner planner) {
        Objects.requireNonNull(goal, "goal cannot be null");
        Objects.requireNonNull(planner, "planner cannot be null");

        AgentTrace trace = new AgentTrace();
        int currentStep = 0;

        AgentState currentState = new AgentState.Initialized(goal);
        trace.append(StepTrace.of(currentStep++, currentState, 0));

        try {
            while (currentStep <= maxSteps) {
                // 1. Safety check before step execution
                governor.inspectStep(currentState);

                // 2. Planning phase
                currentState = new AgentState.Thinking(goal, currentStep);
                trace.append(StepTrace.of(currentStep++, currentState, 0));

                List<ToolExecution> plannedTools = planner.planNextStep(currentState, trace);

                // If planner produced no tools, the goal is achieved
                if (plannedTools.isEmpty()) {
                    currentState = new AgentState.Completed("Goal achieved: " + goal, currentStep);
                    trace.append(StepTrace.of(currentStep, currentState, 0));
                    return trace;
                }

                // 3. Tool execution phase via Virtual Threads
                List<String> toolNames = plannedTools.stream().map(te -> te.tool().name()).toList();
                currentState = new AgentState.ExecutingTools(toolNames, currentStep);
                trace.append(StepTrace.of(currentStep++, currentState, 0));

                // Verify with Governor for cycle detection
                for (ToolExecution te : plannedTools) {
                    governor.recordAndVerifyToolCall(te.tool().name(), te.input());
                }

                // Dispatch concurrently across Project Loom Virtual Threads
                List<ToolResults> results = loomRunner.runConcurrent(plannedTools, toolTimeout);
            }

            // Exceeded max steps
            currentState = new AgentState.Failed("Max steps ceiling exceeded: " + maxSteps, currentStep);
            trace.append(StepTrace.of(currentStep, currentState, 0));

        } catch (Governor.BudgetExceededException | Governor.LoopDetectedException e) {
            currentState = new AgentState.Failed("Governor halted execution: " + e.getMessage(), currentStep);
            trace.append(StepTrace.of(currentStep, currentState, 0));
        } catch (Exception e) {
            currentState = new AgentState.Failed("Unexpected error: " + e.getMessage(), currentStep);
            trace.append(StepTrace.of(currentStep, currentState, 0));
        }

        return trace;
    }
}
