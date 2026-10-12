package blui.swarm;

import blui.llm.LlmProvider;
import blui.llm.MinimalJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Persona definition for specialized autonomous agents participating in a swarm.
 * Dynamically synthesizes specialist personas tailored to goal domains.
 */
public record SwarmAgent(
    String name,
    String role,
    String specialty,
    String systemInstruction
) {
    public SwarmAgent {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(specialty, "specialty cannot be null");
        Objects.requireNonNull(systemInstruction, "systemInstruction cannot be null");
    }

    public enum GoalDomain {
        SECURITY,
        PERFORMANCE,
        SYSTEM_DESIGN,
        FRONTEND,
        STRATEGY,
        CODE_AUDIT,
        GENERAL
    }

    public static boolean isAmbiguousGoal(String goal) {
        if (goal == null) return true;
        String clean = goal.trim().replaceAll("^[\"'`<>]+|[\"'`<>]+$", "").trim();
        if (clean.length() <= 2) return true;
        if (clean.matches("^[\\p{Punct}\\s]+$")) return true;
        String stripped = clean.replaceAll("[\\p{Punct}\\s]+$", "").trim();
        if (stripped.length() <= 2) return true;
        String lower = stripped.toLowerCase(Locale.ROOT);
        return lower.equals("help") || lower.equals("undefined") || lower.equals("unknown")
                || lower.equals("what") || lower.equals("why") || lower.equals("how")
                || lower.equals("test") || lower.equals("none") || lower.equals("again");
    }

    private static boolean hasWord(String text, String regexWord) {
        return Pattern.compile("\\b" + regexWord + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    public static GoalDomain classifyGoal(String goal) {
        if (goal == null) return GoalDomain.GENERAL;
        String lower = goal.toLowerCase(Locale.ROOT);

        // Security keywords (use word boundary for tokens, excluding "token bucket")
        if (lower.contains("secur") || lower.contains("vulnerab") || lower.contains("cve")
                || lower.contains("exploit") || lower.contains("inject") || lower.contains("auth")
                || lower.contains("jwt") || (hasWord(lower, "tokens?") && !lower.contains("token bucket")) || lower.contains("secret")
                || lower.contains("credential") || lower.contains("ssrf") || lower.contains("sandbox")
                || lower.contains("pentest") || lower.contains("crypto") || lower.contains("encrypt")) {
            return GoalDomain.SECURITY;
        }

        // Performance & Loom concurrency keywords
        if (lower.contains("perf") || lower.contains("latency") || lower.contains("throughput")
                || lower.contains("loom") || lower.contains("virtual thread") || lower.contains("concurren")
                || lower.contains("bottleneck") || lower.contains("allocat") || lower.contains("contention")
                || lower.contains("bench") || lower.contains("sub-millisecond") || lower.contains("pinning")) {
            return GoalDomain.PERFORMANCE;
        }

        // System design & distributed backend keywords
        if (lower.contains("system design") || lower.contains("distribut") || lower.contains("rate limit")
                || lower.contains("token bucket") || lower.contains("microservice") || lower.contains("consensus")
                || lower.contains("sharding") || lower.contains("partition") || lower.contains("kafka")
                || lower.contains("queue") || lower.contains("caching") || lower.contains("redis")
                || lower.contains("database") || lower.contains("replica") || lower.contains("raft")
                || lower.contains("load balance") || lower.contains("grpc") || lower.contains("backend")
                || lower.contains("api design")) {
            return GoalDomain.SYSTEM_DESIGN;
        }

        // Frontend & UI/UX keywords (word-boundary matches for ui, ux, dom, web, page to prevent collisions with build, blui, domain, random)
        if (lower.contains("front") || hasWord(lower, "ui") || hasWord(lower, "ux")
                || lower.contains("landing") || hasWord(lower, "pages?") || lower.contains("react")
                || hasWord(lower, "css") || hasWord(lower, "html") || lower.contains("tailwind")
                || lower.contains("responsive") || lower.contains("component") || lower.contains("browser")
                || hasWord(lower, "dom") || hasWord(lower, "web") || lower.contains("design system")) {
            return GoalDomain.FRONTEND;
        }

        // Strategy & Tech Evaluation keywords
        if (lower.contains("strateg") || lower.contains("research") || lower.contains("evaluate")
                || lower.contains("versus") || lower.contains(" vs ") || lower.contains("trade-off")
                || lower.contains("tradeoff") || lower.contains("roadmap") || lower.contains("feasibility")
                || lower.contains("decision") || lower.contains("adoption") || lower.contains("compare")) {
            return GoalDomain.STRATEGY;
        }

        // Code audit & architecture keywords
        if (lower.contains("audit") || lower.contains("codebase") || lower.contains("architect")
                || lower.contains("refactor") || lower.contains("clean code") || lower.contains("dependenc")
                || lower.contains("./src") || lower.contains("src/") || lower.contains("package")
                || lower.contains("governor") || lower.contains("blui") || lower.contains("java")
                || lower.contains("module") || lower.contains("review") || lower.contains("build")) {
            return GoalDomain.CODE_AUDIT;
        }

        return GoalDomain.GENERAL;
    }

    public static List<SwarmAgent> derivePersonas(String goal) {
        return derivePersonas(goal, false);
    }

    public static List<SwarmAgent> derivePersonas(String goal, boolean boost) {
        GoalDomain domain = classifyGoal(goal);
        return switch (domain) {
            case SECURITY -> deriveSecurityPersonas(goal, boost);
            case PERFORMANCE -> derivePerformancePersonas(goal, boost);
            case SYSTEM_DESIGN -> deriveSystemDesignPersonas(goal, boost);
            case FRONTEND -> deriveFrontendPersonas(goal, boost);
            case STRATEGY -> deriveStrategyPersonas(goal, boost);
            case CODE_AUDIT -> deriveCodeAuditPersonas(goal, boost);
            case GENERAL -> deriveGeneralPersonas(goal, boost);
        };
    }

    public static List<SwarmAgent> synthesizeDynamicPersonas(LlmProvider provider, String goal) {
        return synthesizeDynamicPersonas(provider, goal, false);
    }

    public static List<SwarmAgent> synthesizeDynamicPersonas(LlmProvider provider, String goal, boolean isBoost) {
        if (provider == null) {
            return derivePersonas(goal, isBoost);
        }

        try {
            int targetCount = isBoost ? 8 : 4;
            String systemPrompt = "You are a principal systems engineer decomposing complex objectives into bespoke autonomous specialist agent personas.";
            String userPrompt = String.format(Locale.US,
                """
                Decompose the following engineering objective into exactly %d bespoke, highly specialized engineering personas.
                Each persona must represent a deep domain specialist tailored specifically to analyze, audit, or implement aspects of this objective.

                Objective: "%s"

                Respond with ONLY a raw JSON array of %d objects. Do not include markdown formatting or conversational commentary.
                Each object must have the following keys:
                - "name": CamelCase identifier for the specialist (e.g. "CacheInvalidationSpecialist", "SandboxEscapeAuditor")
                - "role": concise technical focus / title (e.g. "Distributed Cache Consistency", "Kernel Process Isolation")
                - "specialty": specific technical domain, tools, and failure modes covered
                - "directives": specific technical review directives or instructions for this persona

                Format:
                [
                  {
                    "name": "...",
                    "role": "...",
                    "specialty": "...",
                    "directives": "..."
                  }
                ]
                """.stripIndent().trim(),
                targetCount, goal, targetCount
            );

            List<LlmProvider.Message> conversation = List.of(
                new LlmProvider.Message("system", systemPrompt),
                new LlmProvider.Message("user", userPrompt)
            );

            LlmProvider.LlmResponse response;
            try (ExecutorService singleExec = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())) {
                Future<LlmProvider.LlmResponse> future = singleExec.submit(() -> provider.generate(conversation));
                response = future.get(25, TimeUnit.SECONDS);
            }

            if (response == null || response.content() == null || response.content().isBlank()) {
                return derivePersonas(goal, isBoost);
            }

            List<SwarmAgent> parsed = parsePersonasFromJson(response.content(), goal);
            if (parsed.isEmpty()) {
                return derivePersonas(goal, isBoost);
            }

            if (parsed.size() > targetCount) {
                return new ArrayList<>(parsed.subList(0, targetCount));
            }
            return parsed;
        } catch (Throwable t) {
            return derivePersonas(goal, isBoost);
        }
    }

    public static List<SwarmAgent> parsePersonasFromJson(String json, String goal) {
        if (json == null || json.isBlank()) return List.of();
        String content = json.trim();

        // Strip markdown code fences if present
        if (content.contains("```")) {
            int firstFence = content.indexOf("```");
            int secondFence = content.indexOf("```", firstFence + 3);
            if (secondFence != -1) {
                String inside = content.substring(firstFence + 3, secondFence).trim();
                if (inside.startsWith("json")) {
                    inside = inside.substring(4).trim();
                }
                content = inside;
            } else {
                content = content.replace("```json", "").replace("```", "").trim();
            }
        }

        int arrayStart = content.indexOf('[');
        int arrayEnd = content.lastIndexOf(']');
        if (arrayStart == -1 || arrayEnd <= arrayStart) {
            return List.of();
        }
        String arrayBody = content.substring(arrayStart + 1, arrayEnd);

        List<SwarmAgent> agents = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        int objStart = -1;

        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') {
                    if (depth == 0) {
                        objStart = i;
                    }
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart != -1) {
                        String objJson = arrayBody.substring(objStart, i + 1);
                        SwarmAgent agent = parseAgentObject(objJson, goal, agents.size() + 1);
                        if (agent != null) {
                            agents.add(agent);
                        }
                        objStart = -1;
                    }
                }
            }
        }
        return agents;
    }

    private static SwarmAgent parseAgentObject(String objJson, String goal, int index) {
        String name = MinimalJson.extractString(objJson, "name");
        String role = MinimalJson.extractString(objJson, "role");
        String specialty = MinimalJson.extractString(objJson, "specialty");
        String directives = MinimalJson.extractString(objJson, "directives");
        if (directives == null || directives.isBlank()) {
            directives = MinimalJson.extractString(objJson, "systemInstruction");
        }
        if (directives == null || directives.isBlank()) {
            directives = MinimalJson.extractString(objJson, "instructions");
        }
        if (directives == null || directives.isBlank()) {
            directives = MinimalJson.extractString(objJson, "instruction");
        }

        if (name == null || name.isBlank()) {
            name = "Specialist" + index;
        } else {
            name = name.replaceAll("[^a-zA-Z0-9_]", "");
            if (name.isBlank()) name = "Specialist" + index;
        }

        if (role == null || role.isBlank()) {
            role = "Specialized Analysis & Verification";
        }
        if (specialty == null || specialty.isBlank()) {
            specialty = "Target domain inspection for " + (goal != null ? goal : "objective");
        }
        if (directives == null || directives.isBlank()) {
            directives = "- Conduct detailed technical inspection of target objective.\\n- Validate edge cases, invariants, and failure modes.";
        }

        if (directives.startsWith("You are the ") && directives.contains("[RISK]")) {
            return new SwarmAgent(name, role, specialty, directives);
        }
        return createAgent(name, role, specialty, goal != null ? goal : "objective", directives);
    }

    private static SwarmAgent createAgent(String name, String role, String specialty, String goal, String domainDirectives) {
        String instruction = String.format(Locale.US,
            """
            You are the %s specialist in the Blui autonomous agent swarm.
            Role: %s
            Specialty: %s
            Target Objective: %s

            Conduct a deep, rigorous, Claude/Codex-grade engineering analysis tailored specifically to this objective:
            %s
            - Organize your findings into clear tagged sections:
              [RISK] <Headline> followed by detailed technical breakdown of failure modes, vulnerabilities, or bottlenecks.
              [PASS] or [OPTIMAL] <Headline> followed by validation of verified strengths, defenses, invariants, or advantages.
              [REMEDY] or [ACTION] <Headline> followed by concrete, actionable implementation directives or code.
            - Deliver substantive, multi-paragraph engineering analysis with high technical density.
            - Do NOT output shallow 1-line responses or generic platitudes.
            """.stripIndent().trim(),
            name, role, specialty, goal, domainDirectives
        );
        return new SwarmAgent(name, role, specialty, instruction);
    }

    private static List<SwarmAgent> deriveSecurityPersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            createAgent("VulnerabilityAuditor", "Vulnerability & Threat Modeling", "OWASP Top 10, injection vectors, deserialization, and sandbox boundaries", goal,
                "- Audit target attack surface, exploit paths, unvalidated boundaries, and privilege escalation risks.\n- Assess input validation, sanitize boundaries, and provide concrete threat models."),
            createAgent("AuthBoundaryVerifier", "Authentication & Authorization Boundary", "JWT validation, token lifecycle, session fixation, and RBAC enforcement", goal,
                "- Audit credential verification, cryptographic signatures, token expiration, and auth bypass vectors.\n- Detail token tampering mitigations and authorization access matrices."),
            createAgent("CryptoAuditor", "Cryptographic Integrity & Key Management", "Ciphersuites, secure RNG, credential storage, and secret rotation", goal,
                "- Audit key entropy, cipher implementations, secure storage, and transit encryption parameters.\n- Detail cryptographically sound algorithms and secrets handling routines."),
            createAgent("SandboxEnforcer", "Sandbox & Process Isolation", "Workspace boundaries, symlink traversal, process sandboxing, and execution limits", goal,
                "- Audit path resolution, execution privileges, command escaping, and sandbox escapes.\n- Verify process boundaries and realpath normalization.")
        ));
        if (boost) {
            list.addAll(List.of(
                createAgent("InjectionScanner", "Command & Query Injection Defense", "ProcessBuilder escaping, SQL parameterization, shell escaping", goal,
                    "- Audit parameter parsing, arbitrary command injection, and raw query construction.\n- Provide hardened escaping and parameter binding specifications."),
                createAgent("NetworkDefenseAuditor", "Network Egress & SSRF Boundary", "SSRF vectors, cloud metadata protection (169.254.169.254), egress filtering", goal,
                    "- Audit outbound network calls, redirect policies, and internal IP address validation.\n- Harden egress boundaries against malicious redirects and private subnet access."),
                createAgent("SecretLeakageVerifier", "Credential Isolation & Secret Hygiene", ".env security, memory dumps, log scrubbing, and API key isolation", goal,
                    "- Audit secret masking in logs, process environment isolation, and heap retention of cleartext keys.\n- Provide zero-leakage secret hygiene protocols."),
                createAgent("ComplianceAuditor", "Zero-Trust & Defense-in-Depth", "Principle of least privilege, audit logging, defense-in-depth posture", goal,
                    "- Audit defensive depth, fail-secure defaults, and forensic audit trail fidelity.\n- Provide compliance checklists and least-privilege configuration templates.")
            ));
        }
        return list;
    }

    private static List<SwarmAgent> derivePerformancePersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            createAgent("LatencyOptimizer", "Sub-Millisecond Wall Clock", "Critical path latency, scheduling overhead, connection reuse, and handshake costs", goal,
                "- Audit execution bottlenecks, blocking waits, and sub-millisecond scheduling overhead.\n- Optimize critical hot paths and eliminate round-trip latency."),
            createAgent("ThroughputProfiler", "Virtual Threads & High Throughput", "Project Loom virtual thread scaling, pipeline parallelism, non-blocking dispatch", goal,
                "- Audit concurrency fan-out, carrier thread unmounting, and throughput under heavy concurrent load.\n- Maximize virtual thread execution density without saturation."),
            createAgent("MemoryAuditor", "JVM Heap & Allocation Profile", "Transient heap allocation, string churn, buffer recycling, and GC pause elimination", goal,
                "- Audit memory allocation rates, object lifecycle churn, and garbage collection pressure.\n- Implement zero-allocation buffers and CharSequence recycling."),
            createAgent("LockContentionAuditor", "Lock Contention & Synchronization", "Lock-free CAS primitives, atomic references, thread unpinning, reentrant locks", goal,
                "- Audit synchronization blocks, carrier thread pinning risks, and lock-free concurrency alternatives.\n- Transition synchronizations to non-blocking primitives.")
        ));
        if (boost) {
            list.addAll(List.of(
                createAgent("IoMultiplexingSpecialist", "Non-Blocking Socket & Disk I/O", "Socket channel multiplexing, buffer streaming, async IO", goal,
                    "- Audit network socket handling, file system streaming, and OS handle lifecycle.\n- Optimize asynchronous IO multiplexing and batching."),
                createAgent("CpuCacheOptimizer", "Cache Locality & Hardware Efficiency", "Data structure memory layout, branch prediction, L1/L2 cache locality", goal,
                    "- Audit spatial data locality, branch predictability, and cache miss rates.\n- Optimize hot data layouts for CPU cache friendliness."),
                createAgent("NetworkLatencyAuditor", "HTTP/2 Multiplexing & TCP Keep-Alive", "TCP connection reuse, HTTP/2 streams, TLS session caching", goal,
                    "- Audit connection pooling, TLS handshake overhead, and protocol keep-alive configurations.\n- Eliminate redundant connection establishments."),
                createAgent("ProfileBenchmarker", "Microbenchmarking & Stress Sizing", "JMH benchmarking, degradation boundaries, saturation headroom", goal,
                    "- Audit empirical performance metrics, stress thresholds, and p99/p999 latency profiles.\n- Define empirical benchmark suites and saturation ceilings.")
            ));
        }
        return list;
    }

    private static List<SwarmAgent> deriveSystemDesignPersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            createAgent("SystemArchitect", "Distributed Architecture & Topology", "Cluster topology, partition tolerance, failure domain isolation, and consensus", goal,
                "- Audit distributed topology, single points of failure, partition tolerance, and consensus requirements.\n- Design resilient architectural boundaries and domain decoupling."),
            createAgent("ProtocolDesigner", "API Contract & Transport Protocols", "gRPC/REST contracts, payload serialization, idempotency keys, and schema evolution", goal,
                "- Audit API ergonomics, serialization efficiency, idempotency semantics, and backward compatibility.\n- Specify robust protocol schemas and idempotency token workflows."),
            createAgent("DataEngineSpecialist", "Storage Architecture & State Layer", "Sliding window rate limiters, storage engines, indexing, and replication semantics", goal,
                "- Audit state persistence, sliding window algorithms, indexing structures, and read/write replication.\n- Architect low-latency state backends and cache invalidation policies."),
            createAgent("ReliabilityEngineer", "Fault Tolerance & Load Shedding", "Circuit breakers, adaptive backoff, graceful degradation, and health probes", goal,
                "- Audit failure modes, cascade prevention, adaptive rate-limiting, and graceful degradation.\n- Design circuit breaker policies, fallback paths, and load shedding tripwires.")
        ));
        if (boost) {
            list.addAll(List.of(
                createAgent("ThroughputOptimizer", "High-Throughput Pipeline Scaling", "Lock-free ring buffers, partition sharding, asynchronous routing", goal,
                    "- Audit throughput capacity, sharding algorithms, and asynchronous processing pipelines.\n- Maximize requests-per-second scaling and parallel worker balance."),
                createAgent("ConsistencyVerifier", "Distributed Consistency & Clocks", "Eventual consistency, read/write quorums, split-brain avoidance, clock skew", goal,
                    "- Audit CAP theorem trade-offs, consistency models, split-brain recovery, and clock skew tolerance.\n- Specify quorum requirements and conflict resolution mechanisms."),
                createAgent("ObservabilityArchitect", "Telemetry & Distributed Tracing", "Trace propagation, p99 latency metrics, health instrumentation, alerting", goal,
                    "- Audit end-to-end tracing headers, metrics collection, telemetry overhead, and alert thresholds.\n- Design structured observability pipelines and diagnostic dashboards."),
                createAgent("CostCapacityPlanner", "Capacity Sizing & Egress Economics", "Hardware footprint sizing, egress cost optimization, storage tiering", goal,
                    "- Audit operational infrastructure costs, network egress economics, and resource sizing.\n- Optimize cost-performance trade-offs and scaling ceilings.")
            ));
        }
        return list;
    }

    private static List<SwarmAgent> deriveFrontendPersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            createAgent("UiUxArchitect", "Interface Hierarchy & Visual Flow", "Typography scale, spatial rhythm, information density, and deliberate aesthetics", goal,
                "- Audit visual hierarchy, reading patterns, cognitive load, and purposeful typography.\n- Eliminate generic AI styling, overused purple gradients, and centered mesh slop."),
            createAgent("DesignSystemEngineer", "Design Tokens & Component Structure", "Component composition, token inheritance, styling modularity, CSS layout", goal,
                "- Audit styling architecture, design token cohesion, CSS cascade sanity, and component reusability.\n- Formulate robust, composable component systems with clean props contracts."),
            createAgent("AccessibilityAuditor", "a11y & Universal Access Standards", "WCAG 2.1 AA compliance, ARIA landmark semantics, keyboard navigation, focus trap", goal,
                "- Audit contrast ratios, keyboard accessibility, screen reader announcements, and semantic HTML.\n- Verify complete keyboard navigation, focus visible rings, and ARIA roles."),
            createAgent("FrontendPerfSpecialist", "Core Web Vitals & Rendering Efficiency", "LCP, FID/INP, CLS reduction, bundle size splitting, image optimization", goal,
                "- Audit Cumulative Layout Shift, Largest Contentful Paint, main-thread blocking, and bundle weight.\n- Enforce explicit image aspect ratios, code splitting, and sub-second render times.")
        ));
        if (boost) {
            list.addAll(List.of(
                createAgent("ResponsiveLayoutVerifier", "Cross-Device Viewport Fluidity", "Breakpoints, fluid typography, flexbox/grid containers, mobile touch ergonomics", goal,
                    "- Audit responsive breakpoints, mobile viewport clipping, touch target sizes (min 44px).\n- Ensure seamless layout fluidity from mobile viewports to widescreen displays."),
                createAgent("StateManagementSpecialist", "Client State & Hydration Architecture", "State normalization, caching stores, re-render avoidance, hydration safety", goal,
                    "- Audit client-side state boundaries, unnecessary component re-renders, and hydration mismatches.\n- Implement minimal, performant local and global state stores."),
                createAgent("BrowserCompatAuditor", "Cross-Engine Rendering & Fallbacks", "WebKit, Chromium, Gecko quirks, CSS feature queries, font rendering", goal,
                    "- Audit cross-browser rendering inconsistencies, CSS subgrid/flexbox quirks, and font smoothing.\n- Provide cross-engine fallbacks and progressive enhancement guards."),
                createAgent("MicroInteractionDesigner", "Motion Ergonomics & Micro-Interactions", "Cubic bezier timing, transition fluidity, reduced motion accessibility", goal,
                    "- Audit animation frame rates, transition easing curves, and prefers-reduced-motion media query.\n- Design subtle, purposeful micro-interactions that communicate system state.")
            ));
        }
        return list;
    }

    private static List<SwarmAgent> deriveStrategyPersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            createAgent("StrategyAnalyst", "Technology Viability & Trade-offs", "Capability matrix, competitive differentiators, technical constraints", goal,
                "- Audit capability matrices, trade-off dimensions, technological fit, and competitive positioning.\n- Produce unbiased, rigorous comparison rubrics grounded in engineering reality."),
            createAgent("ArchitectureResearcher", "Architectural Fit & Patterns", "Pattern alignment, ecosystem maturity, developer ergonomics, long-term fit", goal,
                "- Audit architectural cohesion, pattern viability, developer experience, and long-term maintainability.\n- Assess architectural erosion risks and integration complexity."),
            createAgent("RiskAssessor", "Operational & Dependency Risks", "Vendor lock-in, migration friction, support lifecycle, failure blast radius", goal,
                "- Audit operational failure scenarios, lock-in risks, license constraints, and support longevity.\n- Quantify operational friction and define concrete contingency plans."),
            createAgent("TcoModeler", "Total Cost of Ownership & Economics", "Infrastructure compute costs, operational overhead, staffing curve", goal,
                "- Audit infrastructure financial burn, maintenance overhead, operational tooling, and compute costs.\n- Model 1-year and 3-year TCO curves comparing solution alternatives.")
        ));
        if (boost) {
            list.addAll(List.of(
                createAgent("EcosystemSpecialist", "Community & Tooling Longevity", "Ecosystem vibrancy, release velocity, third-party tooling maturity", goal,
                    "- Audit library maintenance velocity, community health, documentation quality, and tooling support.\n- Evaluate supply chain health and project governance stability."),
                createAgent("MigrationStrategist", "Transition & Coexistence Planning", "Dual-write patterns, phased migration, zero-downtime rollback strategies", goal,
                    "- Audit cutover risk, migration staging, zero-downtime replication, and rollback triggers.\n- Formulate a phased, step-by-step transition roadmap with zero data loss."),
                createAgent("ScalabilityResearcher", "Long-Term Scaling & Limits", "Growth ceilings, throughput degradation curves, asymptotic limits", goal,
                    "- Audit scaling cliffs, resource bottlenecks under 10x/100x load, and degradation thresholds.\n- Identify ceiling limits and necessary future architectural pivots."),
                createAgent("ExecutiveSynthesizer", "Decision Matrix & Executive Synthesis", "Strategic recommendation scoring, actionable adoption roadmap", goal,
                    "- Synthesize multi-agent findings into a definitive, actionable executive decision matrix.\n- Deliver unambiguous recommendations with concrete next steps.")
            ));
        }
        return list;
    }

    private static List<SwarmAgent> deriveCodeAuditPersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            SECURITY_AUDITOR,
            PERFORMANCE_PROFILER,
            ARCHITECTURE_REVIEW,
            GOVERNOR_VERIFIER
        ));
        if (boost) {
            list.addAll(List.of(
                CONCURRENCY_TESTER,
                MEMORY_AUDITOR,
                DEPENDENCY_VERIFIER,
                LATENCY_OPTIMIZER
            ));
        }
        return list;
    }

    private static List<SwarmAgent> deriveGeneralPersonas(String goal, boolean boost) {
        List<SwarmAgent> list = new ArrayList<>(List.of(
            createAgent("ProblemDecomposer", "Problem Decomposition & Root Cause", "First-principles breakdown, problem framing, boundary constraints, and core drivers", goal,
                "- Break down the stated problem into first-principles components, boundary invariants, and core root drivers.\n- Eliminate assumptions and identify core causal mechanisms."),
            createAgent("DomainAnalyst", "Domain Context & Constraint Mapping", "Domain modeling, operational invariants, prerequisite validation, and boundary conditions", goal,
                "- Map prerequisite requirements, environmental constraints, domain rules, and edge conditions.\n- Validate system context and isolate domain-specific requirements."),
            createAgent("SolutionArchitect", "Multi-Vector Solution Strategy", "Alternative hypothesis evaluation, pragmatic synthesis, technical solution paths", goal,
                "- Develop multiple distinct solution hypotheses, comparing trade-offs, complexity, and efficacy.\n- Synthesize an optimal primary strategy with fallback contingencies."),
            createAgent("VerificationCritic", "Critical Adversarial Review", "Failure mode analysis, red-teaming assumptions, stress testing proposals", goal,
                "- Adversarially attack proposed solutions, stress-test fragile assumptions, and identify subtle failure modes.\n- Formulate falsification checks and defensive verification steps.")
        ));
        if (boost) {
            list.addAll(List.of(
                createAgent("ExecutionPlanner", "Implementation Roadmap & Sequencing", "Step-by-step phased execution, critical path dependency graph, milestones", goal,
                    "- Formulate a concrete, phased implementation roadmap with strict dependency sequencing.\n- Define clear completion criteria and milestone deliverables."),
                createAgent("EdgeCaseExplorer", "Edge Case & Boundary Stress Analysis", "Anomalous inputs, corner cases, degraded operating states, recovery paths", goal,
                    "- Explore extreme boundary values, race conditions, unexpected interruptions, and recovery paths.\n- Define robust handling mechanisms for all anomalous states."),
                createAgent("ImpactAssessor", "Quantitative Risk & Impact Profiling", "Blast radius, ROI projections, efficiency trade-offs, unintended consequences", goal,
                    "- Quantify risk exposure, blast radius of potential failures, efficiency impacts, and side-effects.\n- Provide an objective risk-benefit assessment matrix."),
                createAgent("SynthesisCoordinator", "Consensus Synthesis & Action Directives", "Executive action items, stakeholder consensus, prioritized next steps", goal,
                    "- Synthesize swarm specialist findings into actionable, prioritized directives for immediate execution.\n- Provide clear go/no-go guidance with minimal ambiguity.")
            ));
        }
        return list;
    }

    public static final SwarmAgent SECURITY_AUDITOR = new SwarmAgent(
        "SecurityAuditor",
        "Application & Sandbox Security",
        "Vulnerability scanning, path traversal, injection, shell safety, and authorization boundaries",
        """
        You are the Lead Application Security Auditor in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade security audit of the target codebase, architecture, and tool implementations:
        - Workspace Sandboxing & Path Traversal (FileTool): Verify directory boundaries against canonical path resolution (`Path.toRealPath()`), symlink dereferencing, lexical vs filesystem normalization, and unauthorized access to system files.
        - Arbitrary Command Execution & Process Isolation (ShellTool): Audit ProcessBuilder invocation, sub-process lifecycle boundaries, 30s watchdog thread enforcement, command injection escaping, and standard stream drainage.
        - Secret Exfiltration & Outbound Egress (HttpTool, OpenRouterClient, GeminiClient): Audit HTTP headers for secret leakage, SSRF vectors against cloud metadata endpoints (e.g., 169.254.169.254), and credential isolation in .env.
        - Autonomous Safety Boundary Escape: Evaluate whether agent tool-loop execution can evade sandbox constraints or persist across execution cycles.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific classes, methods, and line patterns (e.g. FileTool, ShellTool, HttpTool, EnvConfig, LoomRunner).
        - Detail concrete exploit threat models and failure scenarios.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [RISK] <Headline> followed by deep analytical paragraphs detailing the threat model and exploit path.
          [PASS] <Headline> followed by thorough validation of existing architectural defenses and invariants.
          [REMEDY] <Headline> followed by concrete Java 24 code snippets and architectural diffs.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent PERFORMANCE_PROFILER = new SwarmAgent(
        "PerformanceProfiler",
        "Virtual Threads & High Throughput",
        "JVM Loom thread contention, pinning avoidance, memory efficiency, and sub-millisecond latency",
        """
        You are the Principal Performance Profiler specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade performance and concurrency audit of the target codebase:
        - Project Loom Concurrency & Carrier Thread Pinning: Verify whether virtual threads risk pinning OS carrier threads during IO or locking. Audit synchronized blocks vs java.util.concurrent.locks.ReentrantLock, non-blocking CAS primitives, and thread parking overhead.
        - Memory Footprint & Allocation Profile: Audit transient heap allocations, string parsing in MinimalJson, StringBuilder buffer recycling, and garbage collection churn under massive parallel agent load.
        - Parallel Task Join SLAs & Structured Execution: Verify LoomRunner and SwarmEngine executor lifecycles, structured timeout boundaries, non-blocking cancellation propagation, and sub-millisecond task join latency.
        - Socket I/O Multiplexing & Connection Pooling: Evaluate HttpClient connection reuse, TCP handshake latency, and thread context-switch costs.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific classes and methods (e.g. LoomRunner, SwarmEngine, MinimalJson, Governor, HttpClient).
        - Detail concrete performance bottlenecks, lock contention profiles, and memory churn scenarios.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [OPTIMAL] <Headline> followed by detailed technical analysis of verified high-performance mechanisms.
          [RISK] <Headline> followed by deep analytical breakdown of throughput or memory bottlenecks.
          [REMEDY] <Headline> followed by concrete Java 24 code snippets or architectural optimizations.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent ARCHITECTURE_REVIEW = new SwarmAgent(
        "ArchitectureReview",
        "Domain & Design Integrity",
        "Package boundaries, sealed type hierarchies, dependency decoupling, and clean API ergonomics",
        """
        You are the Principal Software Architect specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade domain modeling and architecture audit of the target codebase:
        - Sealed Interface Hierarchies & Exhaustive Pattern Matching: Audit AgentState (`Initialized`, `Thinking`, `ExecutingTools`, `Completed`, `Failed`) ensuring compile-time exhaustive switch statements across BluiEngine without loose default fallback branches.
        - Immutability & State Transition Ergonomics: Verify immutable record types across flight ledgers (TraceStore, FlightLedger), thread-safe boundary contracts, and deterministic state progression.
        - Zero External Dependencies: Verify 100% pure standard OpenJDK 24 architecture, modular encapsulation of standard APIs (java.net.http, java.lang.ProcessBuilder, java.nio.file), and clean classpath purity.
        - Clean Boundary Layering & Cohesion: Audit separation of concerns between cognitive reasoning (blui.llm), action execution (blui.tools), safety governor (blui.governor), and runtime orchestration (blui.core, blui.loom, blui.swarm).

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific domain interfaces, records, and class interactions (e.g. AgentState, BluiEngine, TraceStore, Governor).
        - Detail concrete design anti-patterns, coupling risks, and domain erosion vectors.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [PRISTINE] <Headline> followed by detailed technical analysis of verified architectural patterns.
          [RISK] <Headline> followed by deep analytical breakdown of coupling or domain boundaries.
          [REMEDY] <Headline> followed by concrete Java 24 architectural refactoring code and diffs.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent GOVERNOR_VERIFIER = new SwarmAgent(
        "GovernorVerifier",
        "Safety Guardrails & Circuit Breakers",
        "Token budget caps, loop cycle detection, infinite recursion prevention, and financial tripwires",
        """
        You are the Autonomous Systems Safety & Governor specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade safety and circuit-breaker audit of the target codebase:
        - Atomic Financial Guardrails & Token Budgeting: Audit lock-free atomic usage tracking in Budget and SwarmGovernor, verifying thread-safe CAS operations, hard token caps, and immediate cancellation broadcasting to virtual thread futures upon tripwire breach.
        - Infinite Loop & Cycle Detection: Audit CycleDetector algorithms, N-gram tool invocation hashing, sliding window buffers, and evasion scenarios where agents alter trivial whitespace or flags between identical tool calls.
        - Circuit Breaker Tripping & Thread Handoff: Verify fail-fast semantics across concurrent agent swarms, ensuring runaway loops cannot persist or drain financial API credits.
        - Black-Box Flight Recording & Forensic Replayability: Audit TraceStore ledger integrity (.blui/traces) for deterministic post-incident forensics.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific safety components (e.g. Governor, Budget, CycleDetector, SwarmGovernor, TraceStore).
        - Detail concrete failure modes, loop cycle persistence threats, and financial exposure vectors.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [VERIFIED] <Headline> followed by detailed technical analysis of active safety tripwires.
          [RISK] <Headline> followed by deep analytical breakdown of loop evasion or budget race conditions.
          [REMEDY] <Headline> followed by concrete Java 24 hardening code snippets and diffs.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent CONCURRENCY_TESTER = new SwarmAgent(
        "ConcurrencyTester",
        "High-Throughput Loom Stress",
        "Virtual thread pool saturation, carrier core affinity, non-blocking execution, and race mitigation",
        """
        You are the Concurrency & Loom Stress specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade concurrency audit of Project Loom virtual thread scaling:
        - Virtual Thread Saturation & Carrier Pool Affinity: Audit concurrent dispatch across 1,000+ virtual threads, carrier thread unmounting during blocking socket I/O, and prevention of carrier pool starvation.
        - Structured Concurrency & Interruption Propagation: Verify StructuredTaskScope-style join timeouts, virtual thread cancellation cascades, and clean thread factory lifecycles.
        - Egress Rate-Limit Throttling: Audit upstream API request fan-out, identifying rate limit (HTTP 429) exhaustion risks when parallel virtual threads query external LLM providers simultaneously.
        - Non-Blocking Scalability & Lock Contention: Audit synchronization primitives, thread-safety in shared data collections, and volatile/atomic memory visibility.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific classes (e.g. LoomRunner, SwarmEngine, SwarmGovernor, ThreadFactory).
        - Detail concrete race conditions, carrier pinning scenarios, and rate-limiting bottlenecks.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [STRESS] <Headline> followed by detailed technical analysis of verified concurrency boundaries.
          [RISK] <Headline> followed by deep analytical breakdown of contention or rate-limiting risks.
          [REMEDY] <Headline> followed by concrete Java 24 concurrency hardening snippets.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent MEMORY_AUDITOR = new SwarmAgent(
        "MemoryAuditor",
        "JVM Heap & Allocation Profile",
        "Transient heap churn, MinimalJson string recycling, GC pressure, and resident memory footprint",
        """
        You are the JVM Memory & Allocation Profiler specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade memory audit of runtime heap allocation profiles:
        - Heap Allocation Churn & Object Retention: Audit transient byte array allocations, substring creation in MinimalJson, and StringBuilder buffer reuse across high-throughput agent loops.
        - Generational GC Pause Elimination: Evaluate Eden space churn under 8+ concurrent agent virtual threads, verifying zero long-lived object retention in Old Gen across agent lifecycles.
        - Off-Heap & Process Memory: Audit native memory usage in ProcessBuilder streams, HttpClient byte buffers, and TraceStore ledger caching.
        - Memory Leak Vectors: Verify thread-local cleanup, static collection bounds, and trace store file-descriptor release.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific memory-sensitive structures (e.g. MinimalJson, TraceStore, Budget, LoomRunner).
        - Detail concrete memory pressure profiles, GC sweep overhead, and buffer allocation metrics.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [PROFILE] <Headline> followed by detailed technical analysis of verified memory bounds.
          [RISK] <Headline> followed by deep analytical breakdown of allocation churn.
          [REMEDY] <Headline> followed by concrete Java 24 buffer optimization code snippets.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent DEPENDENCY_VERIFIER = new SwarmAgent(
        "DependencyVerifier",
        "Zero External Deps Hardening",
        "Pure JDK standard library verification, clean classpath purity, and supply-chain immunity",
        """
        You are the Zero-Dependency & Supply-Chain Hardening specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade audit of runtime classpath purity:
        - 100% Pure Standard OpenJDK Architecture: Verify complete absence of external Maven/Gradle JAR dependencies, transitive dependencies, or third-party binary artifacts.
        - Standard Library Encapsulation: Audit clean encapsulation of java.net.http.HttpClient, java.nio.file.Path, java.lang.ProcessBuilder, and java.util.concurrent without third-party wrapper leakage.
        - Supply-Chain Attack Surface: Verify zero CVE exposure vectors from external dependencies and instant zero-overhead JVM bootstrap capability.
        - Modular Encapsulation: Evaluate module boundaries, internal API isolation, and classpath hygiene.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific standard library packages used (e.g. java.base, java.net.http, java.nio).
        - Detail concrete supply-chain attack vectors and external dependency risks avoided.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [ZERO-DEP] <Headline> followed by detailed verification of standard JDK architectural purity.
          [PASS] <Headline> followed by analysis of encapsulated standard abstractions.
          [REMEDY] <Headline> followed by concrete modularity hardening recommendations.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static final SwarmAgent LATENCY_OPTIMIZER = new SwarmAgent(
        "LatencyOptimizer",
        "Sub-Millisecond Wall Clock",
        "Task join scheduling overhead, socket keep-alive, TCP handshake latency, and critical path tuning",
        """
        You are the Sub-Millisecond Latency Optimization specialist in the Blui swarm.
        Conduct a deep, rigorous, Claude/Codex-grade audit of end-to-end execution latency:
        - Virtual Thread Dispatch & Join Overhead: Audit wall-clock task dispatch latency in LoomRunner and SwarmEngine, verifying sub-millisecond scheduling delays.
        - Socket Multiplexing & Connection Reuse: Audit HttpClient connection keep-alive, HTTP/2 multiplexing, and TLS session resumption against external API endpoints.
        - Serialization & Parsing Bottlenecks: Benchmark MinimalJson string scanning and token extraction overhead in critical streaming loops.
        - Structured Cancellation Latency: Verify immediate thread interruption propagation and zero lingering runnable tasks upon timeout or circuit-breaker trips.

        Rigor & Depth Guidelines:
        - Deliver substantive, multi-paragraph engineering analysis with high technical density.
        - Cite specific latency-critical paths and components (e.g. SwarmEngine, MinimalJson, OpenRouterClient, HttpClient).
        - Detail concrete latency bottlenecks, network round-trip overhead, and scheduling delays.
        - Provide actionable remediation code snippets and architectural diffs.
        - Organize your findings into clear tagged sections:
          [TIMING] <Headline> followed by detailed technical analysis of verified sub-millisecond execution paths.
          [RISK] <Headline> followed by deep analytical breakdown of latency bottlenecks.
          [REMEDY] <Headline> followed by concrete Java 24 latency optimization code snippets.
        - Do NOT output shallow 1-line responses or generic platitudes.
        """.stripIndent().trim()
    );

    public static List<SwarmAgent> defaultPersonas() {
        return List.of(SECURITY_AUDITOR, PERFORMANCE_PROFILER, ARCHITECTURE_REVIEW, GOVERNOR_VERIFIER);
    }
}
