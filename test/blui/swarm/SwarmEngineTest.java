package blui.swarm;

import blui.llm.LlmProvider;

import java.time.Duration;
import java.util.List;

/**
 * Pure JDK verification suite for Phase 10 SwarmEngine multi-agent orchestrator.
 * Zero external test framework dependencies.
 */
public class SwarmEngineTest {

    public static void main(String[] args) throws Exception {
        System.out.println("Running SwarmEngine verification suite...");

        testDefaultPersonas();
        testParallelExecutionOnVirtualThreadsWithinSla();
        testSwarmGovernorBudgetCapEnforcement();
        testResultSynthesisAndPersonaRouting();
        testDynamicBudgetTripDuringExecution();
        testBoostPersonasAndMockFindings();
        testAmbiguousGoalRejection();
        testDynamicSpecialistPersonas();
        testGracefulOfflineSimFallbackOnProviderFailure();
        testNoCascadeCancelOnProviderError();
        testLlmProviderMockForcesSimulation();
        testWordBoundaryDomainClassification();
        testTargetScopePathResolution();
        testSynthesizeDynamicPersonasSuccess();
        testSynthesizeDynamicPersonasBoostMode();
        testSynthesizeDynamicPersonasMarkdownCodeFences();
        testSynthesizeDynamicPersonasNullProviderFallback();
        testSynthesizeDynamicPersonasFailureFallback();
        testSynthesizeDynamicPersonasMalformedJsonFallback();
        testSynthesizeDynamicPersonasWithThinkingTags();

        System.out.println("All 20 SwarmEngine tests passed successfully!");
    }

    private static void testAmbiguousGoalRejection() {
        assertCondition(SwarmEngine.isAmbiguousGoal("h"), "Single letter 'h' must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("hu"), "Two letters 'hu' must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("?"), "Question mark must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("???"), "Multiple punctuation must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal(""), "Empty string must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("   "), "Whitespace string must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal(null), "Null must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("help"), "'help' token must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("help!"), "'help!' with trailing exclamation must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("what?"), "'what?' with trailing question mark must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal("why?!"), "'why?!' with multiple trailing punctuation must be ambiguous");
        assertCondition(!SwarmEngine.isAmbiguousGoal("audit ./src"), "'audit ./src' must not be ambiguous");
        assertCondition(!SwarmEngine.isAmbiguousGoal("design distributed rate limiter"), "'design distributed rate limiter' must not be ambiguous");

        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, Duration.ofSeconds(3));
        boolean threw = false;
        try {
            engine.runSwarm(new SwarmTask("t_ambiguous", "h", "src"), SwarmAgent.defaultPersonas());
        } catch (IllegalArgumentException e) {
            threw = true;
            assertCondition(e.getMessage().contains("Ambiguous swarm goal"), "Must indicate ambiguous swarm goal in exception message");
        }
        assertCondition(threw, "SwarmEngine.runSwarm must reject ambiguous goal 'h' with IllegalArgumentException");

        System.out.println("  ✓ testAmbiguousGoalRejection passed");
    }

    private static void testDynamicSpecialistPersonas() {
        // System Design domain
        List<SwarmAgent> sysDesignAgents = SwarmAgent.derivePersonas("design distributed rate limiter", false);
        assertCondition(sysDesignAgents.size() == 4, "Expected 4 agents for standard system design swarm");
        assertCondition(sysDesignAgents.stream().anyMatch(a -> a.name().equals("SystemArchitect")), "Must include SystemArchitect");
        assertCondition(sysDesignAgents.stream().anyMatch(a -> a.name().equals("ProtocolDesigner")), "Must include ProtocolDesigner");
        assertCondition(sysDesignAgents.stream().anyMatch(a -> a.name().equals("DataEngineSpecialist")), "Must include DataEngineSpecialist");
        assertCondition(sysDesignAgents.stream().anyMatch(a -> a.name().equals("ReliabilityEngineer")), "Must include ReliabilityEngineer");

        // Boost system design (8 agents)
        List<SwarmAgent> sysDesignBoost = SwarmAgent.derivePersonas("design distributed rate limiter", true);
        assertCondition(sysDesignBoost.size() == 8, "Expected 8 agents for boost system design swarm");
        assertCondition(sysDesignBoost.stream().anyMatch(a -> a.name().equals("ThroughputOptimizer")), "Must include ThroughputOptimizer");

        // Frontend domain
        List<SwarmAgent> frontendAgents = SwarmAgent.derivePersonas("redesign landing page UI with react", false);
        assertCondition(frontendAgents.size() == 4, "Expected 4 agents for frontend swarm");
        assertCondition(frontendAgents.stream().anyMatch(a -> a.name().equals("UiUxArchitect")), "Must include UiUxArchitect");
        assertCondition(frontendAgents.stream().anyMatch(a -> a.name().equals("AccessibilityAuditor")), "Must include AccessibilityAuditor");

        // Performance domain
        List<SwarmAgent> perfAgents = SwarmAgent.derivePersonas("optimize virtual threads and latency", false);
        assertCondition(perfAgents.size() == 4, "Expected 4 agents for perf swarm");
        assertCondition(perfAgents.stream().anyMatch(a -> a.name().equals("LatencyOptimizer")), "Must include LatencyOptimizer");

        // Execution of dynamic personas in simulation mode generates rich tagged findings
        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, Duration.ofSeconds(3));
        SwarmTask task = new SwarmTask("task_dynamic", "design distributed rate limiter", "redis");
        SwarmEngine.SwarmReport report = engine.runSwarm(task, sysDesignAgents);

        assertCondition(report.results().size() == 4, "Expected 4 results");
        for (SwarmResult r : report.results()) {
            assertCondition(r.success(), "Dynamic agent " + r.agentName() + " must succeed");
            assertCondition(r.findings().contains("[RISK]"), "Findings must contain [RISK] tag");
            assertCondition(r.findings().contains("[PASS]"), "Findings must contain [PASS] tag");
            assertCondition(r.findings().contains("[REMEDY]"), "Findings must contain [REMEDY] tag");
        }
        assertCondition(report.executiveSynthesis().contains("SystemArchitect"), "Synthesis must include SystemArchitect");

        System.out.println("  ✓ testDynamicSpecialistPersonas passed");
    }

    private static void testGracefulOfflineSimFallbackOnProviderFailure() {
        LlmProvider failingProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "test-cloud-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                throw new RuntimeException("Error 429: LLM Quota Exhausted / Rate limit exceeded");
            }
        };

        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, failingProvider, Duration.ofSeconds(3));

        SwarmTask task = new SwarmTask("task_provider_fail", "audit blui concurrency", "src/blui");
        SwarmEngine.SwarmReport report = engine.runSwarm(task, SwarmAgent.defaultPersonas());

        assertCondition(report.results().size() == 4, "Report must contain 4 results");
        for (SwarmResult r : report.results()) {
            assertCondition(r.success(), "Agent " + r.agentName() + " must succeed via graceful mock fallback");
            assertCondition(r.findings().contains("[OFFLINE SIM — Provider quota exhausted, showing local benchmark]"),
                    "Agent findings must contain offline sim benchmark label: " + r.findings());
        }

        String synthesis = report.executiveSynthesis();
        assertCondition(!synthesis.contains("SWARM HALT"),
                "Synthesis must NOT halt when degrading gracefully to simulation: " + synthesis);
        assertCondition(synthesis.contains("SWARM CONSENSUS"),
                "Synthesis must reflect consensus: " + synthesis);

        boolean hasWarning = report.telemetryWarnings().stream().anyMatch(w -> w.contains("429") || w.contains("Quota"));
        assertCondition(hasWarning, "Telemetry warnings must record provider quota / 429 error");

        System.out.println("  ✓ testGracefulOfflineSimFallbackOnProviderFailure passed");
    }

    private static void testNoCascadeCancelOnProviderError() {
        // One agent fails with 429 quota, while another agent succeeds live
        LlmProvider partialFailingProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "partial-fail-cloud-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                String fullText = conversation.stream().map(Message::content).reduce("", (a, b) -> a + " " + b);
                if (fullText.contains("Lead Application Security Auditor") || fullText.contains("Sandbox Security")) {
                    throw new RuntimeException("Error 429: Rate limit exceeded");
                }
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                return new LlmResponse("Live specialist review passed without issues.", null, null, 50, 70);
            }
        };

        SwarmGovernor governor = new SwarmGovernor(50000, 1.00, 0.000003);
        SwarmEngine engine = new SwarmEngine(governor, partialFailingProvider, Duration.ofSeconds(3));

        SwarmTask task = new SwarmTask("task_no_cascade", "audit concurrency", "src/blui");
        SwarmEngine.SwarmReport report = engine.runSwarm(task, SwarmAgent.defaultPersonas());

        assertCondition(report.results().size() == 4, "Must have 4 results");
        for (SwarmResult r : report.results()) {
            assertCondition(r.success(), "Agent " + r.agentName() + " must succeed, but got failure: " + r.findings());
        }

        SwarmResult secAgent = report.results().stream().filter(r -> r.agentName().equals("SecurityAuditor")).findFirst().orElseThrow();
        assertCondition(secAgent.findings().contains("[OFFLINE SIM — Provider quota exhausted, showing local benchmark]"),
                "SecurityAuditor must have fallen back to offline simulation: " + secAgent.findings());

        // PerformanceProfiler did not fail live provider, so it must contain live response, NOT cancelled!
        SwarmResult perfAgent = report.results().stream().filter(r -> r.agentName().equals("PerformanceProfiler")).findFirst().orElseThrow();
        assertCondition(perfAgent.findings().contains("Live specialist review passed"),
                "PerformanceProfiler must have completed live without being cascade-cancelled: " + perfAgent.findings());

        System.out.println("  ✓ testNoCascadeCancelOnProviderError passed");
    }

    private static void testLlmProviderMockForcesSimulation() throws Exception {
        java.nio.file.Path envPath = blui.app.EnvConfig.DEFAULT_ENV_PATH;
        java.util.Map<String, String> backup = blui.app.EnvConfig.load(envPath);
        try {
            blui.app.EnvConfig.saveVar(envPath, "LLM_PROVIDER", "mock");
            blui.llm.LlmProvider resolved = blui.app.BluiCli.resolveProviderForTesting();
            assertCondition(resolved == null, "resolveProvider must return null when LLM_PROVIDER=mock");
        } finally {
            blui.app.EnvConfig.save(envPath, backup);
        }
        System.out.println("  ✓ testLlmProviderMockForcesSimulation passed");
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

    private static void testWordBoundaryDomainClassification() {
        // "build the project" should NOT classify as FRONTEND (due to "ui")
        SwarmAgent.GoalDomain dBuild = SwarmAgent.classifyGoal("build the project");
        assertCondition(dBuild != SwarmAgent.GoalDomain.FRONTEND, "'build the project' must not be classified as FRONTEND");

        // "audit blui codebase" should be CODE_AUDIT, not FRONTEND
        SwarmAgent.GoalDomain dBlui = SwarmAgent.classifyGoal("audit blui codebase");
        assertCondition(dBlui == SwarmAgent.GoalDomain.CODE_AUDIT, "'audit blui codebase' must be CODE_AUDIT, got: " + dBlui);

        // "random domain model" should NOT classify as FRONTEND (due to "dom")
        SwarmAgent.GoalDomain dDom = SwarmAgent.classifyGoal("random domain model");
        assertCondition(dDom != SwarmAgent.GoalDomain.FRONTEND, "'random domain model' must not be FRONTEND");

        // "token bucket rate limiter" should classify as SYSTEM_DESIGN, not SECURITY
        SwarmAgent.GoalDomain dTokenBucket = SwarmAgent.classifyGoal("token bucket rate limiter");
        assertCondition(dTokenBucket == SwarmAgent.GoalDomain.SYSTEM_DESIGN, "'token bucket rate limiter' must be SYSTEM_DESIGN, got: " + dTokenBucket);

        // "review auth tokens" should be SECURITY
        SwarmAgent.GoalDomain dSec = SwarmAgent.classifyGoal("review auth tokens");
        assertCondition(dSec == SwarmAgent.GoalDomain.SECURITY, "'review auth tokens' must be SECURITY");

        // "build responsive UI components" should be FRONTEND
        SwarmAgent.GoalDomain dUi = SwarmAgent.classifyGoal("build responsive UI components");
        assertCondition(dUi == SwarmAgent.GoalDomain.FRONTEND, "'build responsive UI components' must be FRONTEND");

        System.out.println("  ✓ testWordBoundaryDomainClassification passed");
    }

    private static void testTargetScopePathResolution() {
        // Goal specifying "./test" must resolve target scope targeting test directory
        String testScope = SwarmTask.resolveTargetScope("audit ./test");
        assertCondition(testScope.contains("Codebase Inventory under ./test"), "Target scope must inventory ./test directory when specified in goal: " + testScope);

        // Default goal with no explicit path resolves ./src
        String defaultScope = SwarmTask.resolveTargetScope("review architecture");
        assertCondition(defaultScope.contains("Codebase Inventory under ./src"), "Default target scope must inventory ./src");

        System.out.println("  ✓ testTargetScopePathResolution passed");
    }

    private static void testSynthesizeDynamicPersonasSuccess() {
        LlmProvider mockProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "test-synthesis-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                String json = """
                    [
                      {
                        "name": "DistributedCacheArchitect",
                        "role": "Cache Topology & Eviction",
                        "specialty": "Redis clusters, TTL skew, cache stampede mitigations",
                        "directives": "- Verify cache coherence across read replicas\\n- Audit stampede locks"
                      },
                      {
                        "name": "RaftConsensusAuditor",
                        "role": "Consensus Invariant Verification",
                        "specialty": "Raft leader leases, quorum heartbeats, split-brain recovery",
                        "directives": "- Check term counters and commit index\\n- Audit asymmetric partition tolerance"
                      },
                      {
                        "name": "EgressBandwidthOptimizer",
                        "role": "Payload Compression & Serialization",
                        "specialty": "Protobuf/gRPC encoding, zero-copy buffers, network egress limits",
                        "directives": "- Profile serialization overhead\\n- Implement streaming frame compression"
                      },
                      {
                        "name": "CircuitBreakerEngineer",
                        "role": "Cascading Failure Prevention",
                        "specialty": "Adaptive rate limiters, backoff jitter, sliding window error tripwires",
                        "directives": "- Audit half-open recovery probes\\n- Validate bulkhead thread isolation"
                      }
                    ]
                    """;
                return new LlmResponse(json, null, null, 120, 180);
            }
        };

        List<SwarmAgent> personas = SwarmEngine.synthesizeDynamicPersonas(mockProvider, "design distributed caching with raft", false);
        assertCondition(personas.size() == 4, "Expected 4 synthesized personas, got " + personas.size());
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("DistributedCacheArchitect")), "Must contain DistributedCacheArchitect");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("RaftConsensusAuditor")), "Must contain RaftConsensusAuditor");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("EgressBandwidthOptimizer")), "Must contain EgressBandwidthOptimizer");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("CircuitBreakerEngineer")), "Must contain CircuitBreakerEngineer");

        SwarmAgent cacheAgent = personas.stream().filter(p -> p.name().equals("DistributedCacheArchitect")).findFirst().orElseThrow();
        assertCondition(cacheAgent.role().equals("Cache Topology & Eviction"), "Role must match: " + cacheAgent.role());
        assertCondition(cacheAgent.specialty().contains("Redis clusters"), "Specialty must contain Redis clusters: " + cacheAgent.specialty());
        assertCondition(cacheAgent.systemInstruction().contains("cache coherence"), "Instruction must contain directives");

        System.out.println("  ✓ testSynthesizeDynamicPersonasSuccess passed");
    }

    private static void testSynthesizeDynamicPersonasBoostMode() {
        LlmProvider mockProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "test-synthesis-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                // Verify conversation requests 8 personas
                boolean requested8 = conversation.stream().anyMatch(m -> m.content().contains("exactly 8"));
                assertCondition(requested8, "Boost mode prompt must request exactly 8 personas");

                StringBuilder sb = new StringBuilder("[\n");
                for (int i = 1; i <= 8; i++) {
                    sb.append(String.format("""
                      {
                        "name": "Specialist%d",
                        "role": "Specialist Role %d",
                        "specialty": "Specialty domain %d",
                        "directives": "Directives %d"
                      }%s
                    """, i, i, i, i, i < 8 ? "," : ""));
                }
                sb.append("]");
                return new LlmResponse(sb.toString(), null, null, 200, 300);
            }
        };

        List<SwarmAgent> personas = SwarmEngine.synthesizeDynamicPersonas(mockProvider, "deep audit of enterprise system", true);
        assertCondition(personas.size() == 8, "Expected 8 synthesized personas in boost mode, got " + personas.size());
        for (int i = 1; i <= 8; i++) {
            final int idx = i;
            assertCondition(personas.stream().anyMatch(p -> p.name().equals("Specialist" + idx)), "Missing Specialist" + idx);
        }

        System.out.println("  ✓ testSynthesizeDynamicPersonasBoostMode passed");
    }

    private static void testSynthesizeDynamicPersonasMarkdownCodeFences() {
        LlmProvider mockProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "test-fenced-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                String responseText = """
                    Here is your bespoke agent decomposition:
                    ```json
                    [
                      {
                        "name": "KernelSandboxAuditor",
                        "role": "Seccomp & Namespaces",
                        "specialty": "Linux cgroups, eBPF filters",
                        "directives": "Check syscall boundaries"
                      },
                      {
                        "name": "TlsHandshakeOptimizer",
                        "role": "TLS 1.3 Session Resumption",
                        "specialty": "Cryptographic session tickets, 0-RTT",
                        "directives": "Audit replay defense"
                      },
                      {
                        "name": "ZeroAllocationBufferScanner",
                        "role": "Off-Heap Unsafe Memory",
                        "specialty": "DirectByteBuffer recycling",
                        "directives": "Audit memory leaks"
                      },
                      {
                        "name": "DeadlockHunter",
                        "role": "Lock Ordering & Wait Graphs",
                        "specialty": "ReentrantLock cycles, CAS spinning",
                        "directives": "Inspect thread wait trees"
                      }
                    ]
                    ```
                    Hope this helps!
                    """;
                return new LlmResponse(responseText, null, null, 100, 150);
            }
        };

        List<SwarmAgent> personas = SwarmEngine.synthesizeDynamicPersonas(mockProvider, "harden kernel and tls", false);
        assertCondition(personas.size() == 4, "Expected 4 personas extracted from markdown code fences, got " + personas.size());
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("KernelSandboxAuditor")), "Must contain KernelSandboxAuditor");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("TlsHandshakeOptimizer")), "Must contain TlsHandshakeOptimizer");

        System.out.println("  ✓ testSynthesizeDynamicPersonasMarkdownCodeFences passed");
    }

    private static void testSynthesizeDynamicPersonasNullProviderFallback() {
        List<SwarmAgent> fallbackPersonas = SwarmEngine.synthesizeDynamicPersonas(null, "design distributed rate limiter", false);
        assertCondition(fallbackPersonas.size() == 4, "Null provider must return 4 catalog fallback personas");
        assertCondition(fallbackPersonas.stream().anyMatch(p -> p.name().equals("SystemArchitect")), "Fallback must use deterministic catalog SystemArchitect");

        List<SwarmAgent> fallbackBoost = SwarmEngine.synthesizeDynamicPersonas(null, "design distributed rate limiter", true);
        assertCondition(fallbackBoost.size() == 8, "Null provider boost must return 8 catalog fallback personas");

        System.out.println("  ✓ testSynthesizeDynamicPersonasNullProviderFallback passed");
    }

    private static void testSynthesizeDynamicPersonasFailureFallback() {
        LlmProvider throwingProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "failing-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                throw new RuntimeException("HTTP 504 Gateway Timeout or Provider Crash");
            }
        };

        List<SwarmAgent> personas = SwarmEngine.synthesizeDynamicPersonas(throwingProvider, "redesign landing page UI with react", false);
        assertCondition(personas.size() == 4, "Provider error must fall back gracefully to 4 catalog personas");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("UiUxArchitect")), "Fallback must return frontend catalog persona UiUxArchitect");

        System.out.println("  ✓ testSynthesizeDynamicPersonasFailureFallback passed");
    }

    private static void testSynthesizeDynamicPersonasMalformedJsonFallback() {
        LlmProvider garbageProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "garbage-model";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                return new LlmResponse("I'm sorry, as an AI language model I cannot parse this.", null, null, 10, 15);
            }
        };

        List<SwarmAgent> personas = SwarmEngine.synthesizeDynamicPersonas(garbageProvider, "audit project security", false);
        assertCondition(personas.size() == 4, "Malformed response must fall back to 4 catalog personas");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("VulnerabilityAuditor") || p.name().equals("SecurityAuditor")),
                "Fallback must return security catalog personas");

        System.out.println("  ✓ testSynthesizeDynamicPersonasMalformedJsonFallback passed");
    }

    private static void testSynthesizeDynamicPersonasWithThinkingTags() {
        LlmProvider thinkingProvider = new LlmProvider() {
            @Override
            public String modelName() {
                return "deepseek-r1-reasoner";
            }

            @Override
            public LlmResponse generate(List<Message> conversation) {
                String responseText = """
                    <think>
                    Thinking about personas for cryptography and secrets management...
                    Let's create 4 specialized agents.
                    </think>
                    ```json
                    [
                      {
                        "name": "KeyDerivationAuditor",
                        "role": "KDF and Entropy",
                        "specialty": "Argon2id, PBKDF2 salt entropy, timing attacks",
                        "directives": "Validate salt randomness and iteration count"
                      },
                      {
                        "name": "SecretZeroizationInspector",
                        "role": "Ephemeral Key Zeroization",
                        "specialty": "DirectByteBuffer cleanup, GC memory leak vectors",
                        "directives": "Ensure char[] / byte[] overwritten before GC"
                      },
                      {
                        "name": "VaultRotationEngineer",
                        "role": "Dynamic Secret Leases",
                        "specialty": "Vault engine integration, lease expiration",
                        "directives": "Audit automated rotation cycles"
                      },
                      {
                        "name": "TpmHardwareAttestationAuditor",
                        "role": "Hardware Root of Trust",
                        "specialty": "TPM 2.0 PCR registers, enclave isolation",
                        "directives": "Verify hardware quote signatures"
                      }
                    ]
                    ```
                    """;
                return new LlmResponse(responseText, null, null, 150, 220);
            }
        };

        List<SwarmAgent> personas = SwarmEngine.synthesizeDynamicPersonas(thinkingProvider, "audit crypto secret storage", false);
        assertCondition(personas.size() == 4, "Must parse 4 personas despite <think> tags");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("KeyDerivationAuditor")), "Must contain KeyDerivationAuditor");
        assertCondition(personas.stream().anyMatch(p -> p.name().equals("SecretZeroizationInspector")), "Must contain SecretZeroizationInspector");

        System.out.println("  ✓ testSynthesizeDynamicPersonasWithThinkingTags passed");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test assertion failed: " + message);
        }
    }
}
