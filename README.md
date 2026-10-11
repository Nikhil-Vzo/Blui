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
| **System Hands (Tools)** | Unchecked dynamic execution | **Sandboxed Shell, Workspace-Jailed File, and Safe HTTP Tools** |
| **Brain Integration** | Bloated SDK packages (OpenAI/LangChain) | **Zero-dep REST clients for Google Gemini (Free Tier) & Local Ollama** |
| **Flight Recording** | Transient logs / unreproducible dynamic runs | **Deterministic Black-Box Disk Persistence (`.blui/traces/*.json`)** |
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
                               └───────┬──────────────┬───────┘
                                       │              │
                    ┌──────────────────┴──┐        ┌──┴──────────────────┐
                    ▼                     ▼        ▼                     ▼
         ┌─────────────────────┐┌─────────────────────┐┌─────────────────────┐
         │      blui.llm       ││    blui.governor    ││      blui.loom      │
         │ (The Agent's Brain) ││ Token budgeting,    ││ Project Loom        │
         │ Gemini 2.5 & Ollama ││ cost limits, loop   ││ virtual-thread      │
         │ & MinimalJson parser││ circuit-breaker     ││ concurrent runner   │
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

## 🚀 Quickstart (Boot in 10 Seconds)

### Prerequisites
* **JDK 24+** (`java -version` returns `24.0.2` or later).
* Any modern terminal (Windows Terminal, iTerm2, Kitty, Alacritty) with UTF-8 enabled.

### 1. Launch Options

#### Option A: One-Click Launcher (Windows)
```powershell
.\blui.bat
```

#### Option B: Standalone Zero-Dependency JAR
```powershell
# Package standalone executable JAR (63 KB, zero dependencies)
jar cfe blui.jar blui.app.BluiCli -C bin .

# Run anywhere on JDK 24
java -jar blui.jar
```

#### Option C: Manual Compile & Launch
```powershell
javac -d bin src/blui/core/*.java src/blui/governor/*.java src/blui/loom/*.java src/blui/replay/*.java src/blui/app/*.java src/blui/tools/*.java src/blui/llm/*.java
java -cp bin blui.app.BluiCli
```

### 2. Available Interactive Commands
```text
blui > bench               # Spawns 500 concurrent virtual threads (finishes in ~23ms)
blui > run <goal>          # Executes live autonomous agent with Governor safety checks
blui > traces              # Lists all past flight recorder ledgers stored on disk (.blui/traces/)
blui > replay <trace-id>   # Replays exact step-by-step decisions, token usage, and tool outputs
blui > status              # Displays live JVM diagnostics (CPU cores, heap, OS/arch)
blui > exit                # Terminates the shell session
```

---

## 🔑 LLM Provider & API Key Configuration

Blui supports both **Cloud LLMs** (Google Gemini 2.5 Flash via Google AI Studio) and **100% Offline Local LLMs** (Ollama @ `localhost:11434`).

Blui resolves your credentials in the following automatic hierarchy:

1. **In-Memory Session**: If pasted in the active CLI session.
2. **OS Environment Variable**: `GEMINI_API_KEY` (e.g. `$env:GEMINI_API_KEY="AIzaSy..."` or `export GEMINI_API_KEY="..."`).
3. **Local `.env` File**: A git-ignored `.env` file in the workspace root:
   ```env
   GEMINI_API_KEY=AIzaSy...
   ```
4. **Interactive Prompt**: If no key is found when running an agent, Blui prompts you right in the terminal and asks if you would like to save it to `.env` for future sessions.
5. **Local Ollama Fallback**: If no key is provided, Blui automatically detects if local Ollama is running and uses your local model (`llama3`, `mistral`, etc.).

---

## 💾 Black-Box Flight Recorder (`.blui/traces/`)

Every agent run is automatically recorded as an immutable JSON ledger in `.blui/traces/`:
* Complete step-by-step state transitions (`Initialized` → `Thinking` → `ExecutingTools` → `Completed`)
* Exact tool inputs, terminal outputs, and execution duration (ms)
* Real hardware token consumption and financial dollar cost
* Inspect past runs anytime using `blui traces` and `blui replay <trace-id>`

---

## 🧩 Codebase Audit (2,109 LOC & Zero Dependencies)

Every component is written from scratch using pure Java 24 standard library primitives:

| Package | Source Files | Lines of Code | Core Responsibility |
| :--- | :--- | :--- | :--- |
| **`blui.core`** | `AgentState.java`<br>`Tool.java`<br>`ToolResults.java`<br>`BluiEngine.java`<br>`BluiEngineTest.java` | **259 LOC** | Sealed state hierarchy, functional tool contracts, master loop, and unit verification suite. |
| **`blui.governor`** | `Budget.java`<br>`CycleDetector.java`<br>`Governor.java` | **175 LOC** | Lock-free token/cost limits and sliding-window loop circuit breaker. |
| **`blui.loom`** | `ToolExecution.java`<br>`LoomRunner.java` | **106 LOC** | Java 24 Virtual Threads executor running parallel tool calls with strict join SLAs. |
| **`blui.tools`** | `ShellTool.java`<br>`FileTool.java`<br>`HttpTool.java` | **291 LOC** | **Pillar 1 (Hands)**: OS process execution with timeout kills, sandboxed workspace filesystem I/O, web fetcher. |
| **`blui.llm`** | `MinimalJson.java`<br>`LlmProvider.java`<br>`GeminiClient.java`<br>`OllamaClient.java` | **400 LOC** | **Pillar 2 (Brain)**: Zero-dep JSON parser, Google Gemini API free tier (`gemini-2.5-flash`), local offline Ollama (`localhost:11434`), real token metering. |
| **`blui.replay`** | `StepTrace.java`<br>`AgentTrace.java`<br>`ReplayEngine.java`<br>`TraceStore.java`<br>`TraceStoreTest.java` | **339 LOC** | **Pillar 3 (Memory)**: Black-box flight recorder to `.blui/traces/*.json`, step-by-step post-mortem inspection, state restoration. |
| **`blui.app`** | `TerminalArt.java`<br>`BluiCli.java` | **539 LOC** | Sub-pixel ANSI 24-bit Truecolor RGBA mascot renderer, hardware status cards, `.env` auth resolver, and interactive REPL shell. |
| **Total** | **24 Java Files (22 src + 2 test)** | **2,109 LOC** | **100% Pure JDK Standard Library** |

---

## 🧪 Verification & Testing

Blui includes standalone, zero-dependency verification suites with 100% passing tests:

```powershell
# Compile & run full test suite
javac -d bin -cp bin test/blui/core/BluiEngineTest.java test/blui/replay/TraceStoreTest.java
java -cp bin blui.core.BluiEngineTest
java -cp bin blui.replay.TraceStoreTest
```

```text
Running BluiEngine verification suite...
  ✓ testSuccessfulGoalCompletion passed
  ✓ testGovernorBudgetHaltsExecution passed
  ✓ testGovernorCycleDetectionHaltsExecution passed
  ✓ testMaxStepsLimit passed
All 4 BluiEngine tests passed successfully!

Running TraceStore verification suite...
  ✓ testSaveAndReadTrace passed
  ✓ testListTraces passed
All TraceStore tests passed successfully!
```

---

## 🗺️ Production Roadmap

- [x] **Phase 1: Sealed State Machine (`blui.core`)**
- [x] **Phase 2: Safety Governor & Cycle Detector (`blui.governor`)**
- [x] **Phase 3: Virtual Threads Loom Runner (`blui.loom`)**
- [x] **Phase 4: Flight Recorder & Replay Engine (`blui.replay`)**
- [x] **Phase 5: Master Autonomous Orchestrator Loop (`BluiEngine`)**
- [x] **Phase 6: Interactive Terminal Showcase & RGBA Mascot Renderer (`BluiCli`)**
- [x] **Phase 7: Native Sandboxed System Tools (`blui.tools`) &mdash; Pillar 1**
- [x] **Phase 8: Zero-Dep LLM Providers (`blui.llm`) &mdash; Pillar 2** (Gemini & Ollama)
- [x] **Phase 9: Trace Persistence & Time-Travel Debugger (`blui.replay`) &mdash; Pillar 3**
- [ ] **Phase 10: Multi-Agent Swarm Concurrency (`blui.swarm`)** &mdash; 500-agent virtual thread choreography

---

## 📜 License & Author

* **Author**: [Nikhil Yadav](https://github.com/Nikhil-Vzo)
* **License**: Apache 2.0 &mdash; See [LICENSE](LICENSE) for details.
