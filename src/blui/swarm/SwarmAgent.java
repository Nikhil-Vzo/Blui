package blui.swarm;

import java.util.List;
import java.util.Objects;

/**
 * Persona definition for specialized autonomous agents participating in a swarm.
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

    public static final SwarmAgent SECURITY_AUDITOR = new SwarmAgent(
        "SecurityAuditor",
        "Application & Sandbox Security",
        "Vulnerability scanning, path traversal, injection, shell safety, and authorization boundaries",
        """
        You are the Security Auditor specialist in the Blui swarm.
        Analyze the target codebase and architecture for security vulnerabilities:
        - Workspace path traversal and sandboxing boundaries (FileTool)
        - Arbitrary command injection and process execution risks (ShellTool)
        - Secret exfiltration and HTTP request sanitization (HttpTool)
        - Authentication and token handling integrity (.env)
        Provide concise, high-severity findings and actionable remediation recommendations.
        """.stripIndent().trim()
    );

    public static final SwarmAgent PERFORMANCE_PROFILER = new SwarmAgent(
        "PerformanceProfiler",
        "Virtual Threads & High Throughput",
        "JVM Loom thread contention, pinning avoidance, memory efficiency, and sub-millisecond latency",
        """
        You are the Performance Profiler specialist in the Blui swarm.
        Analyze the target codebase and runtime for performance and concurrency characteristics:
        - Project Loom virtual thread carrier pinning (synchronized blocks vs ReentrantLock)
        - Parallel task join SLAs and timeout enforcement
        - Zero-allocation string parsing and MinimalJson efficiency
        - Sub-millisecond wall-clock latency overhead and memory footprint
        Provide clear metrics, bottlenecks identified, and optimization opportunities.
        """.stripIndent().trim()
    );

    public static final SwarmAgent ARCHITECTURE_REVIEW = new SwarmAgent(
        "ArchitectureReview",
        "Domain & Design Integrity",
        "Package boundaries, sealed type hierarchies, dependency decoupling, and clean API ergonomics",
        """
        You are the Architecture Review specialist in the Blui swarm.
        Analyze the target codebase for domain modeling and engineering rigor:
        - Sealed type hierarchies and exhaustive pattern matching (AgentState)
        - Immutable records and value types for trace ledgers
        - Zero external runtime dependencies (100% pure standard JDK 24)
        - Package cohesion and clean layer separation between brain, hands, governor, and runtime
        Provide architectural assessment, modularity evaluation, and design feedback.
        """.stripIndent().trim()
    );

    public static final SwarmAgent GOVERNOR_VERIFIER = new SwarmAgent(
        "GovernorVerifier",
        "Safety Guardrails & Circuit Breakers",
        "Token budget caps, loop cycle detection, infinite recursion prevention, and financial tripwires",
        """
        You are the Governor Verifier specialist in the Blui swarm.
        Analyze the target codebase for safety harnesses and autonomous safeguards:
        - Atomic token budgeting and thread-safe lock-free usage tracking
        - Financial USD cost limits and hard ceiling circuit-breakers
        - N-gram and repetition cycle detection preventing agent infinite loops
        - Black-box flight recorder auditability (.blui/traces)
        Provide safety score, tripwire effectiveness analysis, and guardrail validations.
        """.stripIndent().trim()
    );

    public static List<SwarmAgent> defaultPersonas() {
        return List.of(SECURITY_AUDITOR, PERFORMANCE_PROFILER, ARCHITECTURE_REVIEW, GOVERNOR_VERIFIER);
    }
}
