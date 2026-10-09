package blui.replay;

import blui.core.AgentState;

import java.util.Objects;

/**
 * Deterministic time-travel replay engine.
 * Restores agent states from historical traces and branches execution timelines.
 */

public class ReplayEngine
{
    /**
         * Restores the exact AgentState recorded at a specific step index.
         */

    public AgentState restoreStateAt(AgentTrace trace, int stepIndex)
    {
        Objects.requireNonNull(trace, "traced cannot be null");
        return trace.getStep(stepIndex).state();
    }
    /**
       * Rewinds an AgentTrace, returning a new historical timeline
       * containing all steps up to stepIndex (inclusive).
       */
    public AgentTrace rewindTo(AgentTrace trace, int stepIndex)
    {
        Objects.requireNonNull(trace, "trace cannot be null");
        if(stepIndex < 0 || stepIndex >= trace.size()){
            throw new IndexOutOfBoundsException("step index out of bounds:" + stepIndex + ",total:" + trace.size());
        }
        AgentTrace forkedTimeline = new AgentTrace();
        for(int i = 0; i <= stepIndex; i++)
        {
            forkedTimeline.append(trace.getStep(i));
        }
        return forkedTimeline;
    }
    /**
        * Determines whether an AgentState is resumable using pattern matching.
        */
    public boolean isResumable(AgentState state)
    {
        Objects.requireNonNull(state, "state cannot be null");
        return switch (state){
            case AgentState.Completed c -> false;
            case AgentState.Failed f -> false;
            case AgentState.Initialized i -> true;
            case AgentState.Thinking t -> true;
            case AgentState.ExecutingTools e -> true;
        };
    }
}
