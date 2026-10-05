package blui.core;

import java.util.List;

//here all stages in lifecycle of an agent is defined

public sealed interface AgentState permits
            AgentState.Initialized,
            AgentState.Thinking,
            AgentState.ExecutingTools,
            AgentState.Completed,
            AgentState.Failed
            {
                record Initialized(String goal) implements AgentState {}
                record Thinking(String goal, int stepCount) implements AgentState {}
                record ExecutingTools(List <String> toolsName, int stepCount  ) implements AgentState {}
                record Completed(String finalAnswer, int totalSteps ) implements AgentState {}
                record Failed(String reason, int totalSteps) implements AgentState {}

            }
