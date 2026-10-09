<div align="center">

<img src="assets/blui-readme.png" alt="Blui: AI Agent Runtime & Safety Harness" width="100%" />

# 🐈 blui

**The High-Performance AI Agent Runtime & Safety Harness, Built in Pure Java 24.**

[![Status](https://img.shields.io/badge/status-v0.1_alpha_preview-f59e0b?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![Runtime](https://img.shields.io/badge/JDK-24%2B_LTS-blue?style=for-the-badge&logo=openjdk)](https://openjdk.org)
[![Zero Dependencies](https://img.shields.io/badge/dependencies-0_(Standard_Lib)-success?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![Concurrency](https://img.shields.io/badge/concurrency-virtual_threads-purple?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![License](https://img.shields.io/badge/license-Apache_2.0-lightgrey?style=for-the-badge)](LICENSE)

<br/>

> **Python is built for AI prototypes. Blui is engineered for high-throughput, deterministic enterprise workloads.**

</div>

---

## ⚡ Why Blui?

Modern multi-agent frameworks are plagued by leaky abstractions, unobservable execution loops, and runtime fragility:

* **Asyncio Event-Loop Bottlenecks:** Coordinating dozens of concurrent tool calls quickly triggers event-loop starvation, thread blocking, and unhandled promise rejections.
* **Untyped Dictionary Soup:** Dynamic JSON schemas fail deep inside nested agent calls rather than at compile or schema boundaries.
* **Unbounded Financial Risk:** Missing loop detection and token guardrails cause runaway API bills when LLMs get trapped in hallucinations.
* **JVM Isolation:** Enterprise data platforms, event backbones (Kafka), and microservices live on the JVM—yet teams are forced to orchestrate mission-critical logic inside fragile Python sidecars.

**Blui treats agent execution as an operating system problem:** deterministic state transitions, virtual-thread concurrency, strict runtime governors, and immutable flight recording.

---

## ⚖️ The Architectural Contrast

| Dimension | Legacy Python Stacks | **Blui (Java 24)** |
| :--- | :--- | :--- |
| **Concurrency Model** | Single-threaded `asyncio` event loops | **Project Loom Virtual Threads (`newVirtualThreadPerTaskExecutor`)** |
| **State Integrity** | Mutable Python dicts (`dict[str, Any]`) | **Sealed Type Hierarchies & Immutable Records (`AgentState`)** |
| **Safety Guardrails** | External wrappers & manual timeouts | **Native Governor (Atomic token budget, loop circuit-breaker)** |
| **Tool Execution** | Sequential or complex async fan-out | **Lightweight virtual thread pool with microsecond join SLAs** |
| **Debuggability** | Unreproducible dynamic runs | **Deterministic step-by-step Time-Travel Replay (`AgentTrace`)** |
| **Footprint** | Dozens of brittle transitive pip packages | **Zero external dependencies. 100% pure standard JDK.** |

---

## 🏗️ System Architecture

```
                              ┌──────────────────────────────┐
                              │    blui.app.BluiCli          │ (Interactive Terminal REPL)
                              └──────────────┬───────────────┘
                                             │
                              ┌──────────────┴───────────────┐
                              │    blui.core.BluiEngine      │ (Master Orchestrator Loop)
                              └──────────────┬───────────────┘
                                             │
            ┌────────────────────────────────┼────────────────────────────────┐
            ▼                                ▼                                ▼
 ┌─────────────────────┐          ┌─────────────────────┐          ┌─────────────────────┐
 │   blui.core         │          │    blui.governor    │          │      blui.loom      │
 │                     │          │                     │          │                     │
 │  Sealed state records│         │ Token budgeting,    │          │ Project Loom        │
 │  & tool contracts   │          │ hard cost ceilings, │          │ virtual-thread      │
 │  (AgentState, Tool) │          │ & cycle loop-breaker│          │ concurrent runner   │
 └─────────────────────┘          └─────────────────────┘          └─────────────────────┘
                                             │
                                             ▼
                                  ┌─────────────────────┐
                                  │     blui.replay     │
                                  │ Immutable traces &  │
                                  │ time-travel resume  │
                                  └─────────────────────┘
```

---

## 🚀 Quickstart (Boot in 10 Seconds)

### Prerequisites
* **JDK 24+** (`java -version` returns `24.0.2` or later).
* Any modern terminal (Windows Terminal, iTerm2, Kitty, Alacritty) with UTF-8 enabled.

### 1. Clone & Launch
```powershell
# Clone the repository
git clone https://github.com/Nikhil-Vzo/Blui.git
cd Blui

# One-click launch on Windows (auto-sets UTF-8 code page)
.\blui.bat

# Or manual compile & launch on any OS:
javac -d bin src/blui/core/*.java src/blui/governor/*.java src/blui/loom/*.java src/blui/replay/*.java src/blui/app/*.java
java -cp bin blui.app.BluiCli
```

### 2. Available Interactive Commands
```text
blui > bench               # Spawns 500 concurrent virtual threads (finishes in ~23ms)
blui > run <goal>          # Executes the autonomous agent loop with Governor safety checks
blui > status              # Displays live JVM diagnostics (CPU cores, allocated heap, OS/arch)
blui > exit                # Terminates the shell session
```

---

## 🧩 Codebase Audit (1,073 LOC & Zero Dependencies)

Every component is written from scratch using pure Java 24 primitives:

| Package | Source Files | Lines of Code | Core Responsibility |
| :--- | :--- | :--- | :--- |
| **`blui.core`** | `AgentState.java`<br>`Tool.java`<br>`ToolResults.java`<br>`BluiEngine.java` | **259 LOC** | Sealed state hierarchy, functional tool contracts, master loop, and unit verification suite. |
| **`blui.governor`** | `Budget.java`<br>`CycleDetector.java`<br>`Governor.java` | **175 LOC** | Lock-free token/cost limits and sliding-window loop circuit breaker. |
| **`blui.loom`** | `ToolExecution.java`<br>`LoomRunner.java` | **106 LOC** | Java 24 Virtual Threads executor running parallel tool calls with strict join SLAs. |
| **`blui.replay`** | `StepTrace.java`<br>`AgentTrace.java`<br>`ReplayEngine.java` | **135 LOC** | Immutable flight recorder ledger and checkpoint state restoration from step $N$. |
| **`blui.app`** | `TerminalArt.java`<br>`BluiCli.java` | **398 LOC** | Sub-pixel ANSI 24-bit Truecolor RGBA mascot renderer, hardware status cards, and interactive REPL. |
| **Total** | **14 Java Files** | **1,073 LOC** | **100% Pure JDK Standard Library** |

---

## 🧪 Verification & Testing

Blui includes a zero-dependency standalone test runner verifying state transitions, budget ceilings, and loop breakers:

```powershell
# Compile & run test suite
javac -d bin -cp bin test/blui/core/BluiEngineTest.java
java -cp bin blui.core.BluiEngineTest
```

```text
Running BluiEngine verification suite...
  ✓ testSuccessfulGoalCompletion passed
  ✓ testGovernorBudgetHaltsExecution passed
  ✓ testGovernorCycleDetectionHaltsExecution passed
  ✓ testMaxStepsLimit passed
All 4 BluiEngine tests passed successfully!
```

---

## 🗺️ Production Roadmap

- [x] **Phase 1: Sealed State Machine (`blui.core`)**
- [x] **Phase 2: Safety Governor & Cycle Detector (`blui.governor`)**
- [x] **Phase 3: Virtual Threads Loom Runner (`blui.loom`)**
- [x] **Phase 4: Flight Recorder & Replay Engine (`blui.replay`)**
- [x] **Phase 5: Master Autonomous Orchestrator Loop (`BluiEngine`)**
- [x] **Phase 6: Interactive Terminal Showcase & RGBA Mascot Renderer (`BluiCli`)**
- [ ] **Phase 7: Native Sandboxed System Tools (`blui.tools`)** — `ShellTool` (ProcessBuilder SLA timeouts), `FileTool` (scoped workspace I/O), `HttpTool` (web fetcher).
- [ ] **Phase 8: Zero-Dep LLM Provider (`blui.llm`)** — Pure JDK streaming client for local Ollama (`localhost:11434`) and Anthropic Claude.
- [ ] **Phase 9: Trace Persistence & Time-Travel Debugger (`blui.replay`)** — Local JSON ledger (`.blui/traces/*.json`), `blui replay <id>`, and `blui fork <id> --step <N>`.

---

## 📜 License & Author

* **Author**: [Nikhil Yadav](https://github.com/Nikhil-Vzo)
* **License**: Apache 2.0 &mdash; See [LICENSE](LICENSE) for details.
