package blui.replay;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe ordered flight recorder capturing every step taken during an agent's execution.
 */
public class AgentTrace {
    private final List<StepTrace> traceHistory = new ArrayList<>();

    public synchronized void append(StepTrace step) {
        Objects.requireNonNull(step, "step cannot be null");
        traceHistory.add(step);
    }

    public synchronized List<StepTrace> steps() {
        return List.copyOf(traceHistory);
    }

    public synchronized StepTrace getStep(int index) {
        if (index < 0 || index >= traceHistory.size()) {
            throw new IndexOutOfBoundsException("Step index out of bounds: " + index + ", total: " + traceHistory.size());
        }
        return traceHistory.get(index);
    }

    public synchronized int size() {
        return traceHistory.size();
    }

    public synchronized boolean isEmpty() {
        return traceHistory.isEmpty();
    }

    public synchronized StepTrace latestStep() {
        if (traceHistory.isEmpty()) {
            return null;
        }
        return traceHistory.get(traceHistory.size() - 1);
    }

    public synchronized int totalTokensUsed() {
        return traceHistory.stream()
            .mapToInt(StepTrace::tokenUsed)
            .sum();
    }
}
