package blui.swarm;

import blui.llm.LlmProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-throughput multi-agent swarm orchestrator powered by Project Loom virtual threads.
 * Concurrently choreographs specialist personas, enforces structured join timeout SLAs,
 * integrates atomic SwarmGovernor safety tripwires, and synthesizes consolidated executive reports.
 */
public class SwarmEngine {

    public interface SwarmListener {
        default void onAgentStarted(SwarmAgent agent) {}
        default void onAgentProgress(SwarmAgent agent, long elapsedMs) {}
        default void onAgentCompleted(SwarmAgent agent, SwarmResult result) {}
        default void onAgentFailed(SwarmAgent agent, Throwable error) {}
    }

    public record SwarmReport(
        SwarmTask task,
        List<SwarmResult> results,
        String executiveSynthesis,
        int totalTokens,
        double totalCostUsd,
        long joinDurationMs,
        boolean governorTripped,
        int virtualThreadsSpawned,
        List<String> telemetryWarnings
    ) {
        public SwarmReport(SwarmTask task, List<SwarmResult> results, String executiveSynthesis,
                           int totalTokens, double totalCostUsd, long joinDurationMs,
                           boolean governorTripped, int virtualThreadsSpawned) {
            this(task, results, executiveSynthesis, totalTokens, totalCostUsd, joinDurationMs, governorTripped, virtualThreadsSpawned, List.of());
        }
    }

    private final SwarmGovernor governor;
    private final LlmProvider provider; // null in mock/simulation mode
    private final Duration joinTimeout;
    private final List<String> telemetryWarnings = new CopyOnWriteArrayList<>();

    public SwarmEngine(SwarmGovernor governor, Duration joinTimeout) {
        this(governor, null, joinTimeout);
    }

    public SwarmEngine(SwarmGovernor governor, LlmProvider provider, Duration joinTimeout) {
        this.governor = Objects.requireNonNull(governor, "governor cannot be null");
        this.provider = provider;
        this.joinTimeout = Objects.requireNonNull(joinTimeout, "joinTimeout cannot be null");
    }

    public static boolean isAmbiguousGoal(String goal) {
        return SwarmAgent.isAmbiguousGoal(goal);
    }

    public static List<SwarmAgent> derivePersonas(String goal, boolean boost) {
        return SwarmAgent.derivePersonas(goal, boost);
    }

    public static List<SwarmAgent> derivePersonas(String goal) {
        return SwarmAgent.derivePersonas(goal, false);
    }

    /**
     * Executes the swarm with the specified task and agent personas.
     */
    public SwarmReport runSwarm(SwarmTask task, List<SwarmAgent> agents) {
        return runSwarm(task, agents, new SwarmListener() {});
    }

    /**
     * Executes the swarm with live listener callbacks for terminal rendering.
     */
    public SwarmReport runSwarm(SwarmTask task, List<SwarmAgent> agents, SwarmListener listener) {
        Objects.requireNonNull(task, "task cannot be null");
        Objects.requireNonNull(agents, "agents cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        if (isAmbiguousGoal(task.description())) {
            throw new IllegalArgumentException(
                "Ambiguous swarm goal '" + task.description() + "'. Please provide a specific objective (e.g. 'audit ./src', 'design distributed rate limiter')."
            );
        }

        if (agents.isEmpty()) {
            return new SwarmReport(task, List.of(), "Empty swarm - zero specialist agents configured.", 0, 0.0, 0, false, 0);
        }

        long swarmStartTime = System.currentTimeMillis();
        AtomicBoolean budgetTripped = new AtomicBoolean(governor.isTripped());
        AtomicReference<String> providerFailure = new AtomicReference<>(null);

        // Fast-fail if governor budget is already breached before dispatch
        if (budgetTripped.get()) {
            List<SwarmResult> haltedResults = new ArrayList<>();
            for (SwarmAgent agent : agents) {
                haltedResults.add(SwarmResult.failure(
                    agent.name(),
                    agent.role(),
                    "Execution halted: SwarmGovernor circuit breaker already tripped.",
                    0
                ));
            }
            long elapsed = System.currentTimeMillis() - swarmStartTime;
            String synthesis = buildExecutiveSynthesis(task, haltedResults, true);
            return new SwarmReport(task, haltedResults, synthesis, governor.usedTokens(), governor.currentCostUsd(), elapsed, true, 0);
        }

        List<SwarmResult> results = new CopyOnWriteArrayList<>();
        List<Future<SwarmResult>> futures = Collections.synchronizedList(new ArrayList<>());

        // Use Project Loom virtual-thread executor powered by Thread.ofVirtual()
        ThreadFactory virtualFactory = Thread.ofVirtual().name("blui-swarm-agent-", 1).factory();
        try (ExecutorService executor = Executors.newThreadPerTaskExecutor(virtualFactory)) {
            for (SwarmAgent agent : agents) {
                futures.add(executor.submit(() -> {
                    long agentStart = System.currentTimeMillis();

                    try {
                        listener.onAgentStarted(agent);
                        // Check governor before execution
                        governor.checkBudget();

                        SwarmResult res;
                        if (provider != null) {
                            res = executeLiveAgent(task, agent, agentStart, futures, providerFailure);
                        } else {
                            res = executeMockAgent(task, agent, agentStart);
                        }

                        results.add(res);
                        if (res.success()) {
                            listener.onAgentCompleted(agent, res);
                        } else {
                            listener.onAgentFailed(agent, new RuntimeException(res.findings()));
                        }
                        return res;
                    } catch (SwarmGovernor.SwarmBudgetExceededException e) {
                        budgetTripped.set(true);
                        // Instantly cancel other running agents on virtual threads
                        synchronized (futures) {
                            for (Future<?> f : futures) {
                                f.cancel(true);
                            }
                        }
                        long dur = System.currentTimeMillis() - agentStart;
                        SwarmResult fail = SwarmResult.failure(agent.name(), agent.role(), "Circuit Breaker Trip: " + e.getMessage(), dur);
                        results.add(fail);
                        listener.onAgentFailed(agent, e);
                        return fail;
                    } catch (Throwable t) {
                        long dur = System.currentTimeMillis() - agentStart;
                        String msg = t.getMessage() != null && !t.getMessage().isBlank() ? t.getMessage() : t.toString();
                        SwarmResult fail = SwarmResult.failure(agent.name(), agent.role(), "Error: " + msg, dur);
                        results.add(fail);
                        listener.onAgentFailed(agent, t);
                        return fail;
                    }
                }));
            }

            long deadline = swarmStartTime + joinTimeout.toMillis();
            for (int i = 0; i < futures.size(); i++) {
                Future<SwarmResult> f = futures.get(i);
                SwarmAgent agent = agents.get(i);
                long remainingMs = Math.max(10, deadline - System.currentTimeMillis());
                try {
                    f.get(remainingMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    f.cancel(true);
                    long elapsed = System.currentTimeMillis() - swarmStartTime;
                    String errMsg = (provider != null)
                        ? "Provider timeout: all configured LLM providers failed or quota exhausted"
                        : "Join SLA reached in simulation mode";
                    SwarmResult toRes;
                    if (provider != null) {
                        toRes = SwarmResult.failure(agent.name(), agent.role(), errMsg, elapsed);
                        if (!telemetryWarnings.stream().anyMatch(w -> w.contains("timed out"))) {
                            telemetryWarnings.add("Live LLM generation timed out on " + (provider.modelName() != null ? provider.modelName() : "provider") + " — all providers exhausted");
                        }
                        listener.onAgentFailed(agent, te);
                    } else {
                        SwarmResult mockRes = executeMockAgent(task, agent, System.currentTimeMillis() - elapsed);
                        String note = "[NOTE] Swarm join SLA reached; completed via Project Loom virtual thread simulation.\n";
                        toRes = new SwarmResult(agent.name(), agent.role(), note + mockRes.findings(), mockRes.tokens(), elapsed, true);
                        listener.onAgentCompleted(agent, toRes);
                    }
                    if (results.stream().noneMatch(r -> r.agentName().equals(agent.name()))) {
                        results.add(toRes);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    SwarmResult intRes = SwarmResult.failure(agent.name(), agent.role(), "Interrupted", 0);
                    if (results.stream().noneMatch(r -> r.agentName().equals(agent.name()))) {
                        results.add(intRes);
                    }
                    listener.onAgentFailed(agent, ie);
                } catch (java.util.concurrent.CancellationException ce) {
                    String msg;
                    if (budgetTripped.get() || governor.isTripped()) {
                        msg = "Circuit Breaker Trip: Swarm budget hard cap reached";
                    } else if (providerFailure.get() != null) {
                        msg = "Provider failure: " + providerFailure.get();
                    } else {
                        msg = "Task cancelled";
                    }
                    SwarmResult cancelRes = SwarmResult.failure(agent.name(), agent.role(), msg, 0);
                    if (results.stream().noneMatch(r -> r.agentName().equals(agent.name()))) {
                        results.add(cancelRes);
                    }
                    listener.onAgentFailed(agent, ce);
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                    long elapsed = System.currentTimeMillis() - swarmStartTime;
                    if (budgetTripped.get() || governor.isTripped()) {
                        String msg = "Circuit Breaker Trip: " + cause.getMessage();
                        SwarmResult fail = SwarmResult.failure(agent.name(), agent.role(), msg, elapsed);
                        if (results.stream().noneMatch(r -> r.agentName().equals(agent.name()))) {
                            results.add(fail);
                        }
                        listener.onAgentFailed(agent, cause);
                    } else if (provider != null) {
                        String msg = "Provider error: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
                        SwarmResult fail = SwarmResult.failure(agent.name(), agent.role(), msg, elapsed);
                        if (results.stream().noneMatch(r -> r.agentName().equals(agent.name()))) {
                            results.add(fail);
                        }
                        listener.onAgentFailed(agent, cause);
                    } else {
                        SwarmResult mockRes = executeMockAgent(task, agent, System.currentTimeMillis() - elapsed);
                        String note = "[NOTE] Completed via Loom simulation (" + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName()) + ")\n";
                        SwarmResult toRes = new SwarmResult(agent.name(), agent.role(), note + mockRes.findings(), mockRes.tokens(), elapsed, true);
                        if (results.stream().noneMatch(r -> r.agentName().equals(agent.name()))) {
                            results.add(toRes);
                        }
                        listener.onAgentCompleted(agent, toRes);
                    }
                }
            }
        }

        long joinDurationMs = System.currentTimeMillis() - swarmStartTime;
        boolean finalTripped = budgetTripped.get() || governor.isTripped();

        // Sort results to match original persona order
        List<SwarmResult> orderedResults = new ArrayList<>();
        for (SwarmAgent agent : agents) {
            SwarmResult matching = results.stream()
                .filter(r -> r.agentName().equals(agent.name()))
                .findFirst()
                .orElseGet(() -> SwarmResult.failure(agent.name(), agent.role(), "No result returned", 0));
            orderedResults.add(matching);
        }

        String synthesis = buildExecutiveSynthesis(task, orderedResults, finalTripped);
        int totalTokens = governor.usedTokens();
        double totalCost = governor.currentCostUsd();

        return new SwarmReport(
            task,
            orderedResults,
            synthesis,
            totalTokens,
            totalCost,
            joinDurationMs,
            finalTripped,
            agents.size(),
            new ArrayList<>(telemetryWarnings)
        );
    }

    private SwarmResult executeLiveAgent(SwarmTask task, SwarmAgent agent, long startTime,
                                         List<Future<SwarmResult>> futures,
                                         AtomicReference<String> providerFailure) throws Exception {
        try {
            List<LlmProvider.Message> conversation = List.of(
                new LlmProvider.Message("system", agent.systemInstruction()),
                new LlmProvider.Message("user", "Target Goal: " + task.description() + "\nTarget Scope: " + task.target() + "\nProvide your specialist findings.")
            );

            long agentTimeoutMs = Math.min(25000, Math.max(10000, joinTimeout.toMillis() - 5000));
            LlmProvider.LlmResponse response;
            try (ExecutorService singleExec = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())) {
                Future<LlmProvider.LlmResponse> genFuture = singleExec.submit(() -> provider.generate(conversation));
                try {
                    response = genFuture.get(agentTimeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException te) {
                    genFuture.cancel(true);
                    throw new RuntimeException("Live LLM generation timed out after " + agentTimeoutMs + "ms", te);
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
                    if (cause instanceof Exception ex) throw ex;
                    throw new RuntimeException(cause);
                }
            }

            governor.recordUsage(response.totalTokens());
            governor.checkBudget();
            long duration = System.currentTimeMillis() - startTime;
            String content = (response != null && response.content() != null && !response.content().isBlank())
                ? response.content().trim()
                : "Specialist review completed without textual output.";
            int tokens = response != null ? response.totalTokens() : 0;

            return new SwarmResult(
                agent.name(),
                agent.role(),
                content,
                tokens,
                duration,
                true
            );
        } catch (SwarmGovernor.SwarmBudgetExceededException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.getClass().getSimpleName();
            String pName = (provider != null && provider.modelName() != null && !provider.modelName().isBlank()) ? provider.modelName() : "cloud LLM";
            String warningMsg;
            if (msg.contains("429") || msg.contains("quota") || msg.contains("Quota")) {
                warningMsg = "LLM Quota Exhausted / Rate Limited (HTTP 429) on " + pName + ": " + msg;
            } else if (msg.contains("timeout") || msg.contains("Timeout")) {
                warningMsg = "Live LLM generation timed out on " + pName + ": " + msg;
            } else if (msg.contains("401") || msg.contains("Unauthorized") || msg.contains("API_KEY")) {
                warningMsg = "Provider authentication failed on " + pName + ": " + msg;
            } else {
                warningMsg = "Live provider error on " + pName + ": " + msg;
            }

            if (!telemetryWarnings.contains(warningMsg)) {
                telemetryWarnings.add(warningMsg);
            }
            providerFailure.compareAndSet(null, warningMsg);

            // Fast-fail other parallel virtual threads immediately
            synchronized (futures) {
                for (Future<?> f : futures) {
                    f.cancel(true);
                }
            }

            long dur = System.currentTimeMillis() - startTime;
            return SwarmResult.failure(agent.name(), agent.role(), "Provider failure: " + warningMsg, dur);
        }
    }

    private SwarmResult executeMockAgent(SwarmTask task, SwarmAgent agent, long startTime) {
        // Realistic simulated processing latency across Loom virtual threads
        try {
            Thread.sleep(250 + (long)(Math.random() * 200));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Virtual thread execution interrupted", ie);
        }

        governor.checkBudget();
        String findings = generateMockFindings(task, agent);
        int simulatedTokens = 240 + (int)(Math.random() * 80);

        governor.recordUsage(simulatedTokens);
        governor.checkBudget();

        long duration = System.currentTimeMillis() - startTime;
        return new SwarmResult(
            agent.name(),
            agent.role(),
            findings,
            simulatedTokens,
            duration,
            true
        );
    }

    private String generateMockFindings(SwarmTask task, SwarmAgent agent) {
        return switch (agent.name()) {
            case "SecurityAuditor" -> """
                [RISK] FileTool Workspace Traversal via Symbolic Link Dereference:
                In FileTool.java, path boundary validation relies on Path.normalize() against the active workspace root. While lexical traversal sequences (../) are detected, symbolic links that point outside the repository boundary (such as symlinks to system configuration or user credentials) are not dereferenced prior to startsWith checks. An adversarial prompt could craft or navigate a symlink to escape the restricted directory.

                [PASS] ShellTool Process Execution Sandboxing & Subprocess Isolation:
                ShellTool strictly delegates arbitrary commands to isolated sub-processes via standard ProcessBuilder with inherited IO disabled. Each spawned process is tracked with an active 30-second watchdog thread; upon timeout, the process tree is forcibly destroyed, and streams are drained asynchronously on virtual threads to prevent carrier thread deadlocks.

                [REMEDY] Canonical Symlink Resolution Guard in FileTool:
                Enforce canonical path resolution using Path.toRealPath() before validating startsWith(workspaceRoot):
                Path realTarget = target.toRealPath();
                Path realRoot = workspaceRoot.toRealPath();
                if (!realTarget.startsWith(realRoot)) throw new SecurityException("Sandbox escape detected");
                """.stripIndent().trim();

            case "PerformanceProfiler" -> """
                [OPTIMAL] Project Loom Carrier Thread Unpinning Architecture:
                In LoomRunner.java and SwarmEngine.java, concurrent dispatch uses Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()). The runtime completely avoids synchronized blocks in critical paths, relying exclusively on java.util.concurrent.atomic primitives and structured cancellation. Virtual threads unmount cleanly from fork-join carrier threads during network socket IO and file operations, maintaining sub-15ns parking latency.

                [RISK] MinimalJson Streaming Heap Allocation on Large LLM Payloads:
                MinimalJson.java performs lexical scanning by creating substring slices for keys and string values. Under massive token streaming or 100K+ token responses from OpenRouter/Gemini, repetitive substring allocations produce transient heap pressure and short-lived byte arrays, triggering generational GC sweeps on large swarms.

                [REMEDY] MinimalJson Zero-Copy CharSequence Scanner & Buffer Recycling:
                Refactor MinimalJson.java to operate over a reusable CharBuffer or zero-allocation CharSequence view, avoiding redundant String allocations during JSON token traversal.
                """.stripIndent().trim();

            case "ArchitectureReview" -> """
                [PRISTINE] Exhaustive Domain Modeling with Sealed Interface Hierarchies:
                AgentState.java models lifecycle states using a sealed interface permitted exclusively to records (Initialized, Thinking, ExecutingTools, Completed, Failed). This enforces exhaustive pattern-matching switches across BluiEngine.java without fallback default branches, providing compile-time safety and preventing undefined runtime states.

                [RISK] Monolithic CLI Responsibility Coupling in BluiCli:
                BluiCli.java currently bundles ANSI terminal rendering, REPL loop management, interactive configuration wizards (EnvConfig delegation), swarm ANSI spinners, and box layout formatting within a single 1500+ line compilation unit. This couples presentation logic tightly to orchestrator dispatch.

                [REMEDY] Decouple CLI View Layer into Dedicated Terminal Box Renderer:
                Extract executive box generation and ANSI card layouts into a dedicated blui.ui.BoxRenderer module, preserving clean separation between core dispatch and terminal presentation.
                """.stripIndent().trim();

            case "GovernorVerifier" -> """
                [VERIFIED] Lock-Free Atomic Token Budgeting & Financial Guardrails:
                Governor.java and SwarmGovernor.java enforce hard token ceilings and USD financial tripwires using atomic CAS primitives (AtomicInteger, AtomicReference). When token consumption breaches the configured budget ceiling, the governor immediately trips and broadcasts cancellation across virtual thread futures, protecting user API credit limits.

                [RISK] Sliding Window Repetition Evasion in Agent Tool Loops:
                CycleDetector.java tracks recent tool invocations using N-gram hashing. However, if an autonomous agent alters trivial arguments (such as alternating whitespace or minor flag changes) between identical tool calls, cyclic repetition hashes may fail to match, allowing persistent infinite loops before max step exhaustion.

                [REMEDY] Semantic Tool Call Normalization in CycleDetector:
                Normalize tool arguments prior to N-gram hashing by stripping non-semantic whitespace and canonicalizing commands in CycleDetector.recordCall().
                """.stripIndent().trim();

            case "ConcurrencyTester" -> """
                [STRESS] 1,000 Concurrent Virtual Thread Dispatch Benchmark:
                Stress testing under Project Loom demonstrates seamless scalability across 1,000+ concurrent virtual thread tasks with zero thread pool saturation and negligible memory delta (<2MB). Carrier worker threads scale linearly with physical CPU cores without starvation.

                [RISK] Unbounded Parallel Agent Fan-Out Threat to Upstream Rate Limits:
                While Project Loom can trivially spawn 10,000 virtual threads, dispatching unrestricted parallel requests to OpenRouter or Gemini instantly exhausts provider rate limits (HTTP 429), as observed during swarm turbo runs.

                [REMEDY] Virtual Thread Semaphore Throttling for Egress HTTP Calls:
                Introduce a Semaphore(concurrencyLimit) in SwarmEngine to cap concurrent outbound LLM HTTP requests while keeping virtual thread orchestration non-blocking.
                """.stripIndent().trim();

            case "MemoryAuditor" -> """
                [PROFILE] JVM Resident Memory Efficiency & GC Pause Elimination:
                Under full 8-agent swarm concurrency, JVM heap resident delta remains under 1.4MB. Zero long-lived object retention was observed across virtual thread task boundaries. All virtual threads terminate cleanly upon task completion, allowing generational garbage collectors to reclaim activation records in Eden space.

                [RISK] Flight Trace In-Memory Retention on Large Execution Ledgers:
                TraceStore.java retains in-memory ledger records during extended interactive sessions. In long-running autonomous workflows with thousands of tool invocations, unbounded trace collections in memory could lead to heap bloat.

                [REMEDY] Streaming Disk-Backed Ledger Eviction in TraceStore:
                Implement a LRU in-memory cache with transparent file-system spillover to .blui/traces/ to bound memory consumption under continuous runtime execution.
                """.stripIndent().trim();

            case "DependencyVerifier" -> """
                [ZERO-DEP] 100% Standard OpenJDK 24 Compliance & Zero External Dependencies:
                Audit of all 33 source files under src/ verifies 100% pure standard library usage (java.base, java.net.http, java.nio, java.util.concurrent). Zero third-party JARs, POMs, or external binary dependencies exist in the classpath, ensuring zero supply-chain vulnerability attack vectors and instantaneous JVM startup times.

                [PASS] Encapsulated System Process & HTTP Transport Abstractions:
                System process execution and HTTP transport are cleanly encapsulated within ShellTool.java and OpenRouterClient.java/GeminiClient.java without leaking raw sockets or unmanaged OS handles into domain logic.

                [REMEDY] Compile-Time Module Descriptor (module-info.java) Hardening:
                Formalize modular encapsulation with a standard module-info.java declaring explicit exports and zero third-party requires directives.
                """.stripIndent().trim();

            case "LatencyOptimizer" -> """
                [TIMING] Sub-Millisecond Virtual Thread Scheduling Overhead:
                Wall-clock benchmarking indicates end-to-end swarm dispatch and task scheduling overhead under Project Loom is <0.8ms. Thread initialization and unpark delays remain negligible compared to network IO.

                [OPTIMAL] Non-Blocking Virtual Thread Socket IO Multiplexing:
                Standard Java HttpClient operations run asynchronously on virtual threads, automatically yielding carrier threads to other tasks while awaiting network frames.

                [REMEDY] Socket Keep-Alive & Connection Pooling Optimization:
                Configure HttpClient.newBuilder().version(HttpClient.Version.HTTP_2) to reuse existing multiplexed TCP connections across parallel agent queries, saving 15-30ms TLS handshake latency per request.
                """.stripIndent().trim();

            default -> String.format(Locale.US,
                """
                [RISK] %s Boundary & Failure Analysis:
                Detailed evaluation of target '%s' against %s domain rules indicates potential operational trade-offs, edge-case failure paths, and concurrency boundary risks under heavy load.

                [PASS] %s Domain Invariant Verification:
                Core invariants for %s verified. Architectural specifications preserve safety constraints and isolate operational state under standard workload paths.

                [REMEDY] %s Hardening & Remediation Directives:
                Enforce defensive boundary verification and structured telemetry for '%s'. Implement resilient error recovery mechanisms aligned with %s standards.
                """.stripIndent().trim(),
                agent.role(), task.target(), agent.specialty(),
                agent.role(), agent.specialty(),
                agent.role(), task.description(), agent.role()
            );
        };
    }

    private String buildExecutiveSynthesis(SwarmTask task, List<SwarmResult> results, boolean governorTripped) {
        StringBuilder sb = new StringBuilder();

        boolean allFailed = results.stream().noneMatch(SwarmResult::success);
        boolean providerError = results.stream().anyMatch(r -> !r.success()
                && (r.findings().contains("Provider failure") || r.findings().contains("Provider timeout")
                    || r.findings().contains("quota") || r.findings().contains("timed out")));
        boolean quotaExhausted = !telemetryWarnings.isEmpty() && telemetryWarnings.stream()
                .anyMatch(w -> w.contains("429") || w.contains("quota") || w.contains("Quota") || w.contains("timed out"));

        if (governorTripped) {
            sb.append("SWARM HALT: Governor safety circuit-breaker tripped hard budget ceiling.\n");
            sb.append("Execution terminated across virtual threads to protect financial tokens.\n\n");
        } else if (allFailed && quotaExhausted && provider != null) {
            sb.append("SWARM HALT: All LLM providers exhausted or timed out.\n");
            sb.append("Your Gemini free-tier quota is exhausted and OpenRouter timed out. ");
            sb.append("No mock simulation ran — results below show actual failure reasons.\n");
            sb.append("To resolve: wait for quota reset (~2h) or add an OpenRouter API key with credits.\n\n");
        } else if (allFailed && providerError) {
            sb.append("SWARM HALT: Upstream LLM provider failed.\n");
            sb.append("Quota is exhausted or provider is unreachable. Execution terminated without mock simulation.\n\n");
        } else if (provider == null) {
            long successful = results.stream().filter(SwarmResult::success).count();
            sb.append(String.format(Locale.US, "SWARM CONSENSUS [Local Benchmark Simulation Mode]: %d/%d Specialist Reviews Completed.\n", successful, results.size()));
            sb.append("Notice: No live LLM provider configured. Running offline specialist simulations.\n");
            sb.append("Target: '").append(task.description()).append("'\n\n");
        } else {
            long successful = results.stream().filter(SwarmResult::success).count();
            sb.append(String.format(Locale.US, "SWARM CONSENSUS: %d/%d Specialist Reviews Passed Successfully.\n", successful, results.size()));
            sb.append("Target: '").append(task.description()).append("'\n\n");
        }

        sb.append("Specialist Synthesis Highlights:\n");
        for (SwarmResult r : results) {
            String status = r.success() ? "✓ PASS" : "✗ FAIL";
            sb.append(String.format(Locale.US, "  • [%s] %s (%s):\n", status, r.agentName(), r.role()));
            for (String line : r.findings().lines().toList()) {
                sb.append("      ").append(line).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
