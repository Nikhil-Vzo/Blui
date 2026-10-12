<div align="center">

<img src="assets/blui-readme.png" alt="Blui: AI Agent Runtime & Safety Harness" width="100%" />

# 🐈 blui

**The High-Performance AI Agent Runtime & Safety Harness, Built in Pure Java 24.**

[![Release](https://img.shields.io/badge/release-v0.1.0--alpha-success?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui/releases)
[![JDK](https://img.shields.io/badge/JDK-24%2B_LTS-blue?style=for-the-badge&logo=openjdk)](https://openjdk.org)
[![Jar Size](https://img.shields.io/badge/standalone_jar-201_KB-f59e0b?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![Zero Dependencies](https://img.shields.io/badge/dependencies-0_(Standard_Lib)-brightgreen?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![Concurrency](https://img.shields.io/badge/concurrency-virtual_threads-purple?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![Tests](https://img.shields.io/badge/tests-72%2F72_passing-brightgreen?style=for-the-badge)](https://github.com/Nikhil-Vzo/Blui)
[![License](https://img.shields.io/badge/license-Apache_2.0-lightgrey?style=for-the-badge)](LICENSE)

<br/>

> **Python is built for AI prototypes. Blui is engineered for high-throughput, deterministic enterprise workloads.**

</div>

---

## ⚡ Why Blui?

Most modern agent frameworks come wrapped in 50+ transitive dependencies, massive carrier-thread overhead, and fragile runtime abstractions:

* **Asyncio Event-Loop Bottlenecks:** Coordinating dozens of concurrent agent calls triggers event-loop starvation, thread blocking, and unhandled promise rejections.
* **Untyped Dictionary Soup:** Dynamic JSON structures fail deep inside nested agent loops rather than at compile time or schema boundaries.
* **Unbounded Financial Risk:** Missing loop detection and token guardrails cause runaway API bills when LLMs get trapped in hallucinations.
* **Massive Dependency Bloat:** A typical Spring AI or LangChain4j application builds into a **40MB–80MB** fat JAR before you write your first agent.

**Blui treats agent execution as an operating system problem:** deterministic state transitions, virtual-thread concurrency, strict runtime sandboxing, atomic financial governors, and black-box flight recording.

The entire engine builds into a **201 KB standalone executable JAR** with **zero external dependencies**. No LangChain, no Spring, no Jackson. Just pure standard library.

---

## ⚖️ The Architectural Contrast

| Dimension | Legacy Python Stacks | Enterprise Frameworks (LangChain4j / Spring AI) | **Blui (Java 24)** |
| :--- | :--- | :--- | :--- |
| **Concurrency Model** | Single-threaded `asyncio` event loops | Platform thread pools (carrier exhaustion) | **Project Loom Virtual Threads (`Thread.ofVirtual()`)** |
| **Runtime Footprint** | Dozens of brittle `pip` packages | 40MB–80MB+ fat JAR with 50+ transitive JARs | **201 KB Standalone JAR (Zero external dependencies)** |
| **State Integrity** | Mutable Python dicts (`dict[str, Any]`) | Heavy OOP boilerplate & reflection | **Sealed Type Hierarchies & Immutable Records (`AgentState`)** |
| **Safety Guardrails** | Manual timeouts & unmonitored loops | Complex interceptor chains | **Native Governor (Atomic token budget & cycle breaker)** |
| **Tool Sandbox** | Unchecked dynamic execution | Basic wrappers | **Isolated Subprocess Shell, Canonical Symlink Guard & SSRF Defense** |
| **Brain Integration** | Bloated vendor SDKs | Heavy HTTP abstractions (Netty, Jackson, gRPC) | **Zero-dep REST clients for OpenRouter, Gemini & Local Ollama** |
| **Flight Recording** | Transient logs / unreproducible dynamic runs | External APM dependencies | **Deterministic Black-Box Disk Persistence (`.blui/traces/*.json`)** |

---

## 🚀 Quickstart

### Prerequisites
* **JDK 24+** (`java -version` returns `24.0.2` or later).
* Any modern terminal (Windows Terminal, iTerm2, Kitty, Alacritty) with UTF-8 enabled.

### Option 1: Run the Standalone JAR (Instant Boot)

No build tools, Maven, or Gradle required. Download and run directly:

```bash
# Run the 201 KB standalone executable
java -jar blui.jar
```

### Option 2: Build From Source in 2 Seconds

```bash
# Clone the repository
git clone https://github.com/Nikhil-Vzo/Blui.git
cd Blui

# Compile all source files (Pure JDK, zero external libraries)
javac -d bin $(find src -name "*.java")

# Launch the interactive CLI
java -cp bin blui.app.BluiCli
```

*(On Windows PowerShell: `javac -d bin (Get-ChildItem -Recurse -Filter *.java src | Select-Object -ExpandProperty FullName)`)*

### Option 3: Package Your Own Standalone JAR

```bash
jar cfe blui.jar blui.app.BluiCli -C bin .
java -jar blui.jar
```

---

## 🛠️ Core Capabilities & Features

### 1. 🐝 Dynamic Multi-Agent Swarms (`blui swarm <goal>`)
Choreographs parallel specialist agent swarms executing concurrently on **Project Loom Virtual Threads** (`Thread.ofVirtual()`):
* **Dynamic Persona Synthesis:** The engine breaks down goals on the fly into bespoke domain specialists tailored to your objective, rather than using rigid templates.
* **Zero Carrier-Thread Blocking:** High-throughput parallel execution across hundreds of agents without OS thread pool starvation.
* **Turbo Mode (`blui swarm boost <goal>`):** Dispatches up to 8 concurrent specialists under high-stress virtual thread execution.
* **Executive Synthesis Brief:** Aggregates multi-agent findings, consensus clearances, and hardware execution telemetry into a unified summary.

### 2. 🛡️ Hardened Tool Sandbox
Every tool runs behind an explicit security boundary:
* **`ShellTool`:** Isolated subprocess execution with asynchronous virtual-thread stream draining to eliminate process buffer deadlocks.
* **`FileTool`:** Strict workspace jailing and canonical path dereferencing (`toRealPath()`) to block symlink traversal attacks.
* **`HttpTool`:** Native `java.net.http` client with pre-flight DNS validation (`InetAddress.getAllByName`) blocking private RFC 1918, loopback, and cloud metadata (`169.254.169.254`) ranges, with manual redirect loops re-validating every hop.

### 3. ⏱️ Financial Governor & Circuit Breaker
* **Atomic Budget Ceilings:** Thread-safe CAS primitives (`AtomicInteger`, `AtomicReference`) track real-time token consumption and USD costs across all threads.
* **Instantaneous Circuit-Breaker:** Trips in sub-millisecond time and broadcasts cancellation across virtual thread futures upon budget breach.
* **Sliding-Window Loop Detection:** Detects repeating or alternating action cycles and halts runaway LLM hallucination loops.

### 4. 💾 Black-Box Flight Recorder (`.blui/traces/`)
Every agent run is automatically recorded as an immutable JSON ledger in `.blui/traces/`:
* Complete step-by-step state transitions (`Initialized` → `Thinking` → `ExecutingTools` → `Completed`).
* Exact tool inputs, terminal outputs, and execution duration (ms).
* Hardware-measured token consumption and dollar cost.
* Replay past runs anytime with `blui replay <trace-id>`.

### 5. 🔑 Multi-Provider Brain & Terminal Wizard (`blui env`)
* **OpenRouter:** Supports deep reasoning models (e.g. `nvidia/nemotron-3.5-lightning:free` with peak reasoning effort).
* **Google Gemini:** Direct integration with `gemini-3.5-flash` via Google AI Studio.
* **Local Ollama:** 100% offline local inference via `localhost:11434`.
* **Auto-Failover Provider:** Automatically cascades across providers if upstream quotas are exhausted.
* **Hermes-Style Configuration Wizard:** Manage `.env` secrets inline in the terminal with masked inputs (`'k'` to keep existing values).

---

## 💻 Available CLI Commands

| Command | Description |
| :--- | :--- |
| `blui run <goal>` | Executes an autonomous agent loop with Governor and Loom concurrency |
| `blui swarm <goal>` | Dispatches a 4-agent dynamic parallel swarm on virtual threads |
| `blui swarm boost <goal>` | Dispatches an 8-agent turbo swarm with expanded domain specialists |
| `blui env` | Interactive Hermes-style setup wizard for LLM provider keys |
| `blui set env <KEY>=<VAL>` | Sets a specific environment variable inline in `.env` |
| `blui env show` | Displays masked view of current configuration |
| `blui traces` | Lists all saved flight recorder traces on disk (`.blui/traces/`) |
| `blui replay <id>` | Inspects and replays a past flight trace step by step |
| `blui status` | Displays live JVM telemetry (CPU cores, heap, Loom virtual thread status) |
| `blui exit` | Terminates the interactive CLI session |

---

## 🏗️ System Architecture

```
                               ┌──────────────────────────────┐
                               │       blui.app.BluiCli       │ (Interactive Terminal REPL)
                               └──────────────┬───────────────┘
                                              │
                               ┌──────────────┴───────────────┐
                               │     blui.core.BluiEngine     │ (Master Orchestrator Loop)
                               └───────┬──────────────┬───────┘
                                       │              │
                    ┌──────────────────┴──┐        ┌──┴──────────────────┐
                    ▼                     ▼        ▼                     ▼
         ┌─────────────────────┐┌─────────────────────┐┌─────────────────────┐
         │      blui.llm       ││    blui.governor    ││      blui.loom      │
         │ (The Agent's Brain) ││ Token budgeting,    ││ Project Loom        │
         │ Gemini, OpenRouter  ││ cost limits, loop   ││ virtual-thread      │
         │ Ollama & MinimalJson││ circuit-breaker     ││ concurrent runner   │
         └─────────────────────┘└─────────────────────┘└──────────┬──────────┘
                                                                  │
                                       ┌──────────────────────────┴──────────┐
                                       ▼                                     ▼
                            ┌─────────────────────┐               ┌─────────────────────┐
                            │     blui.tools      │               │     blui.replay     │
                            │ (The Agent's Hands) │               │ Flight recorder     │
                            │ ShellTool, FileTool │               │ persistence (.blui) │
                            │ & HttpTool          │               │ & time-travel debug │
                            └─────────────────────┘               └─────────────────────┘
```

---

## 🧪 Verification & Test Suite (72/72 Tests Passing)

Blui includes standalone, zero-dependency test suites verifying every concurrency, security, and runtime guarantee:

```powershell
# Compile and run full verification suite
javac -d bin (Get-ChildItem -Recurse -Filter "*.java" src,test | Select-Object -ExpandProperty FullName)
java -cp bin blui.core.BluiEngineTest
java -cp bin blui.replay.TraceStoreTest
java -cp bin blui.app.EnvConfigTest
java -cp bin blui.app.BluiCliTest
java -cp bin blui.swarm.SwarmEngineTest
java -cp bin blui.llm.OpenRouterClientTest
```

```text
✓ BluiEngineTest         (11/11 passed) — Budget caps, loop cycle detection, Loom join SLAs, SSRF & symlink guards
✓ TraceStoreTest         (3/3 passed)   — Trace persistence, flight listing, path sanitization
✓ EnvConfigTest          (8/8 passed)   — Masked credentials, Hermes wizard, inline parsing, auto-purge
✓ BluiCliTest            (22/22 passed) — Formatting, telemetry cards, prompt directives, dynamic synthesis
✓ SwarmEngineTest        (20/20 passed) — Virtual thread join SLAs, dynamic personas, graceful offline fallback
✓ OpenRouterClientTest   (8/8 passed)   — Token extraction, payload serialization, multiline input parsing

Total: 72/72 unit tests passing (100% pass rate)
```

---

## 📜 License & Author

* **Author**: [Nikhil Yadav](https://github.com/Nikhil-Vzo)
* **License**: Apache 2.0 — See [LICENSE](LICENSE) for full details.
