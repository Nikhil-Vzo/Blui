package blui.loom;

import blui.core.Tool;
import blui.core.ToolResults;
import java.util.Objects;

/**
 * Represents a discrete tool invocation task ready to be executed concurrently.
 */
public record ToolExecution(Tool tool, String input) {

    public ToolExecution {
        Objects.requireNonNull(tool, "tool cannot be null");
        Objects.requireNonNull(input, "input cannot be null");
    }

    /**
     * Executes the tool safely, capturing duration and any errors.
     */
    public ToolResults execute() {
        long startTime = System.currentTimeMillis();
        try {
            return tool.execute(input);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            return new ToolResults(
                tool.name(),
                "Execution error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                false,
                duration
            );
        }
    }
}
