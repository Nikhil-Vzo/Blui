<div align="center">

<img src="assets/blui.png" alt="Blui: AI Agent Runtime & Safety Harness" width="320" />

# 🐈 blui

**The High-Performance AI Agent Runtime & Safety Harness, Built in Pure Java 24.**

[![Status: Alpha Incoming](https://img.shields.io/badge/status-v0.1_alpha_preview-f59e0b?style=for-the-badge)](https://github.com)
[![Runtime](https://img.shields.io/badge/JDK-24%2B-blue?style=for-the-badge&logo=openjdk)](https://openjdk.org)
[![Zero Dependencies](https://img.shields.io/badge/dependencies-0-success?style=for-the-badge)](https://github.com)
[![Architecture](https://img.shields.io/badge/concurrency-virtual_threads-purple?style=for-the-badge)](https://github.com)
[![License](https://img.shields.io/badge/license-Apache_2.0-lightgrey?style=for-the-badge)](LICENSE)

<br/>

> **Python is built for AI prototypes. Blui is engineered for high-concurrency enterprise workloads.**

</div>

---

## ⚡ Why Blui?

Modern multi-agent frameworks are plagued by leaky abstractions, unobservable execution loops, and runtime fragility:

* **Asyncio Event-Loop Bottlenecks:** Coordinating dozens of tool calls quickly triggers event-loop blocking, thread starvation, and unhandled promise rejections.
* **Untyped Dictionary Soup:** Dynamic JSON schemas fail at runtime inside deep nested agent calls rather than at compile or schema boundaries.
* **Unbounded Financial Risk:** Missing loop detection and token guardrails cause runaway API bills when LLMs get trapped in hallucinations.
* **JVM Isolation:** Enterprise data platforms, event backbones (Kafka), and microservices live on the JVM—yet teams are forced to orchestrate mission-critical logic inside fragile Python sidecars.

**Blui solves this by treating agent execution as an operating system problem:** deterministic state transitions, virtual-thread concurrency, and strict runtime governors.

---

## ⚖️ The Architectural Contrast

| Dimension | Legacy Python Stacks | **Blui (Java 24)** |
| :--- | :--- | :--- |
| **Concurrency Model** | Single-threaded `asyncio` event loops | **Virtual Threads (`StructuredTaskScope`)** |
| **State Integrity** | Mutable Python dicts (`dict[str, Any]`) | **Sealed Type Hierarchies & Immutable Records** |
| **Safety Guardrails** | External wrappers & manual timeouts | **Native Governor (Token budget, loop circuit-breaker)** |
| **Tool Execution** | Sequential or complex async orchestration | **Declarative fan-out with microsecond join SLAs** |
| **Debuggability** | Unreproducible dynamic runs | **Deterministic step-by-step Time-Travel Replay** |
| **Footprint** | Dozens of brittle transitive packages | **Zero external dependencies. Pure modern JDK.** |

---

## 🏗️ System Architecture

```
                             ┌──────────────────────────────┐
                             │       blui.core.Engine       │
                             └──────────────┬───────────────┘
                                            │
           ┌────────────────────────────────┼────────────────────────────────┐
           ▼                                ▼                                ▼
┌─────────────────────┐          ┌─────────────────────┐          ┌─────────────────────┐
│   blui.core.State   │          │    blui.governor    │          │      blui.loom      │
│                     │          │                     │          │                     │
│  Sealed records for │          │ Token budgeting,    │          │ Project Loom        │
│  deterministic,     │          │ hard cost ceilings, │          │ virtual-thread      │
│  type-safe lifecycle│          │ & cycle loop-breaker│          │ concurrent runner   │
└─────────────────────┘          └─────────────────────┘          └─────────────────────┘
                                            │
                                            ▼
                                 ┌─────────────────────┐
                                 │     blui.replay     │
                                 │ Immutable traces &  │
                                 │ time-travel resume  │
                                 └─────────────────────┘
```

### Core Primitives

* **Virtual Thread Concurrency (`blui.loom`)**: Forks parallel tool executions (SQL queries, vector searches, API lookups) on lightweight virtual threads with strict join SLAs. Clean, synchronous-style code without callback hell.
* **Sealed State Machines (`blui.core`)**: Every agent state transition is compiler-checked. If an LLM hallucination attempts an invalid transition, it is rejected at the boundary.
* **The Runtime Governor (`blui.governor`)**: Enforces token ceilings, cost limits, and cycle detection (catching LLMs calling identical tools in infinite loops).
* **Time-Travel Step Replay (`blui.replay`)**: Failed runs can be paused, inspected, and resumed from step $N$ without re-running earlier steps.

---

## 📅 Release Roadmap

- [x] Architectural manifesto & sealed state specification
- [x] Virtual thread concurrency design (`StructuredTaskScope`)
- [x] Governor budget & cycle-breaking integration (`blui.governor`)
- [x] Concurrency runner engine (`blui.loom`)
- [x] Time-travel trace & replay engine (`blui.replay`)
- [x] Interactive terminal showcase CLI (`blui.app.BluiCli`)
- [ ] v0.1.0 Public Alpha Preview

---

## ⭐ Track the Release

Blui is under active construction.

1. **Star this repository** to track the upcoming v0.1 release.
2. Click **Watch -> Custom -> Releases** to receive notification when the alpha codebase drops.
3. Join [GitHub Discussions](../../discussions) to share ideas on JVM-native AI systems.
