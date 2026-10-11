package blui.swarm;

import java.time.Duration;
import java.util.List;

/**
 * Pure JDK verification suite for Phase 10 SwarmEngine multi-agent orchestrator.
 * Zero external test framework dependencies.
 */
public class SwarmEngineTest {

    public static void main(String[] args) {
        System.out.println("Running SwarmEngine verification suite...");

        testDefaultPersonas();
        testParallelExecutionOnVirtualThreadsWithinSla();
        testSwarmGovernorBudgetCapEnforcement();
        testResultSynthesisAndPersonaRouting();
        testDynamicBudgetTripDuringExecution();
        testBoostPersonasAndMockFindings();

        System.out.println("All 6 SwarmEngine tests passed successfully!");
    }

    private static void testDynamicBudgetTripDuringExecution() {
        // Budget allowed for only 1 agent (~250-320 tokens), so 4 parallel agents trip the breaker dynamically
        SwarmGovernor governor = new SwarmGovernor(300, 0.05, 0.000003);
        assertCondition(!governor.isTripped(), "Governor must not be tripped initially");

        SwarmEngine engine = new SwarmEngine(governor, Duration.ofSeconds(3));
        SwarmTask task = new SwarmTask("task_dynamic_trip", "Parallel stress test", "core");

        SwarmEngine.SwarmReport report = engine.runSwarm(task, SwarmAgent.defaultPersonas());
        assertCondition(report.governorTripped(), "Governor must trip dynamically during execution");
        assertCondition(report.executiveSynthesis().contains("SWARM HALT"), "Synthesis must reflect SWARM HALT");

        boolean hasTripResult = report.results().stream().anyMatch(r -> !r.success() && r.findings().contains("Circuit Breaker Trip"));
        assertCondition(hasTripResult, "At least one agent must record Circuit Breaker Trip result");

        System.out.println("  ✓ testDynamicBudgetTripDuringExecution passed");
    }

    private static void testBoostPersonasAndMockFindings() {
        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, Duration.ofSeconds(3));

        List<SwarmAgent> boostAgents = List.of(
            SwarmAgent.SECURITY_AUDITOR,
            SwarmAgent.PERFORMANCE_PROFILER,
            SwarmAgent.ARCHITECTURE_REVIEW,
            SwarmAgent.GOVERNOR_VERIFIER,
            blui.app.BluiCli.CONCURRENCY_TESTER,
            blui.app.BluiCli.MEMORY_AUDITOR,
            blui.app.BluiCli.DEPENDENCY_VERIFIER,
            blui.app.BluiCli.LATENCY_OPTIMIZER
        );

        SwarmTask task = new SwarmTask("task_boost", "Deep stress of Loom concurrency", "runtime");
        SwarmEngine.SwarmReport report = engine.runSwarm(task, boostAgents);

        assertCondition(report.results().size() == 8, "Expected 8 results for boost swarm, got " + report.results().size());
        assertCondition(report.virtualThreadsSpawned() == 8, "Expected 8 virtual threads spawned");
        assertCondition(!report.governorTripped(), "Governor should not trip");

        for (SwarmResult r : report.results()) {
            assertCondition(r.success(), "Agent " + r.agentName() + " must succeed");
            assertCondition(!r.findings().isBlank(), "Agent " + r.agentName() + " must have non-blank findings");
        }

        String synthesis = report.executiveSynthesis();
        assertCondition(synthesis.contains("ConcurrencyTester"), "Synthesis must include ConcurrencyTester");
        assertCondition(synthesis.contains("MemoryAuditor"), "Synthesis must include MemoryAuditor");
        assertCondition(synthesis.contains("DependencyVerifier"), "Synthesis must include DependencyVerifier");
        assertCondition(synthesis.contains("LatencyOptimizer"), "Synthesis must include LatencyOptimizer");

        System.out.println("  ✓ testBoostPersonasAndMockFindings passed");
    }

    private static void testDefaultPersonas() {
        List<SwarmAgent> personas = SwarmAgent.defaultPersonas();
        assertCondition(personas.size() == 4, "Must have exactly 4 default review personas");

        boolean hasSecurity = personas.stream().anyMatch(p -> p.name().equals("SecurityAuditor"));
        boolean hasPerf = personas.stream().anyMatch(p -> p.name().equals("PerformanceProfiler"));
        boolean hasArch = personas.stream().anyMatch(p -> p.name().equals("ArchitectureReview"));
        boolean hasGov = personas.stream().anyMatch(p -> p.name().equals("GovernorVerifier"));

        assertCondition(hasSecurity, "Missing SecurityAuditor persona");
        assertCondition(hasPerf, "Missing PerformanceProfiler persona");
        assertCondition(hasArch, "Missing ArchitectureReview persona");
        assertCondition(hasGov, "Missing GovernorVerifier persona");

        System.out.println("  ✓ testDefaultPersonas passed");
    }

    private static void testParallelExecutionOnVirtualThreadsWithinSla() {
        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, Duration.ofSeconds(3));

        SwarmTask task = new SwarmTask("task_1", "Audit Blui concurrency architecture", "src/blui");
        long start = System.currentTimeMillis();
        SwarmEngine.SwarmReport report = engine.runSwarm(task, SwarmAgent.defaultPersonas());
        long elapsed = System.currentTimeMillis() - start;

        assertCondition(report != null, "Report must not be null");
        assertCondition(report.results().size() == 4, "Expected 4 agent results, got " + report.results().size());
        assertCondition(report.virtualThreadsSpawned() == 4, "Expected 4 virtual threads spawned");
        assertCondition(!report.governorTripped(), "Governor should not trip for standard budget");
        assertCondition(elapsed < 3000, "Join SLA exceeded: took " + elapsed + "ms");

        for (SwarmResult result : report.results()) {
            assertCondition(result.success(), "Agent " + result.agentName() + " should succeed");
            assertCondition(result.durationMs() >= 0, "Duration must be non-negative");
            assertCondition(!result.findings().isBlank(), "Findings must not be blank for " + result.agentName());
        }

        System.out.println("  ✓ testParallelExecutionOnVirtualThreadsWithinSla passed");
    }

    private static void testSwarmGovernorBudgetCapEnforcement() {
        // Budget limited to 50 tokens
        SwarmGovernor tightGovernor = new SwarmGovernor(50, 0.01, 0.000003);
        tightGovernor.recordUsage(50); // Hard cap reached

        assertCondition(tightGovernor.isTripped(), "Governor must report tripped after cap reached");

        SwarmEngine engine = new SwarmEngine(tightGovernor, Duration.ofSeconds(3));
        SwarmTask task = new SwarmTask("task_cap", "Exceed budget test", "core");

        SwarmEngine.SwarmReport report = engine.runSwarm(task, SwarmAgent.defaultPersonas());
        assertCondition(report.governorTripped(), "Swarm report must indicate governor tripped");

        System.out.println("  ✓ testSwarmGovernorBudgetCapEnforcement passed");
    }

    private static void testResultSynthesisAndPersonaRouting() {
        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, Duration.ofSeconds(3));

        SwarmTask task = new SwarmTask("task_synthesis", "Review blui safety harness", "blui.governor");
        SwarmEngine.SwarmReport report = engine.runSwarm(task, SwarmAgent.defaultPersonas());

        String synthesis = report.executiveSynthesis();
        assertCondition(synthesis != null && !synthesis.isBlank(), "Executive synthesis must not be blank");
        assertCondition(synthesis.contains("SecurityAuditor"), "Synthesis should reference SecurityAuditor");
        assertCondition(synthesis.contains("PerformanceProfiler"), "Synthesis should reference PerformanceProfiler");
        assertCondition(synthesis.contains("ArchitectureReview"), "Synthesis should reference ArchitectureReview");
        assertCondition(synthesis.contains("GovernorVerifier"), "Synthesis should reference GovernorVerifier");

        System.out.println("  ✓ testResultSynthesisAndPersonaRouting passed");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test assertion failed: " + message);
        }
    }
}
