package blui.loom;

import blui.core.ToolResults;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * High-throughput concurrent tool execution engine powered by Java 24 Virtual Threads.
 * Runs parallel tool tasks with strict join SLA timeouts.
 */
public class LoomRunner {

    /**
     * Executes a batch of tool tasks in parallel on virtual threads.
     * Enforces a hard SLA timeout across all tasks.
     */
    public List<ToolResults> runConcurrent(List<ToolExecution> executions, Duration timeout) {
        Objects.requireNonNull(executions, "executions cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");

        if (executions.isEmpty()) {
            return List.of();
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ToolResults>> futures = new ArrayList<>();

            for (ToolExecution exec : executions) {
                futures.add(executor.submit(exec::execute));
            }

            List<ToolResults> results = new ArrayList<>();
            long timeoutMillis = timeout.toMillis();

            for (int i = 0; i < futures.size(); i++) {
                Future<ToolResults> future = futures.get(i);
                ToolExecution exec = executions.get(i);
                try {
                    results.add(future.get(timeoutMillis, TimeUnit.MILLISECONDS));
                } catch (TimeoutException e) {
                    future.cancel(true);
                    results.add(new ToolResults(
                        exec.tool().name(),
                        "Execution timed out after " + timeoutMillis + "ms",
                        false,
                        timeoutMillis
                    ));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    results.add(new ToolResults(
                        exec.tool().name(),
                        "Execution interrupted",
                        false,
                        0
                    ));
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    results.add(new ToolResults(
                        exec.tool().name(),
                        "Execution failed: " + cause.getMessage(),
                        false,
                        0
                    ));
                }
            }
            return results;
        }
    }
}
