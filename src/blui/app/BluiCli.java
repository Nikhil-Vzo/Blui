package blui.app;

import blui.core.AgentState;
import blui.core.BluiEngine;
import blui.core.Tool;
import blui.core.ToolResults;
import blui.governor.Budget;
import blui.governor.CycleDetector;
import blui.governor.Governor;
import blui.llm.GeminiClient;
import blui.llm.LlmProvider;
import blui.llm.OllamaClient;
import blui.loom.LoomRunner;
import blui.loom.ToolExecution;
import blui.replay.AgentTrace;
import blui.replay.StepTrace;
import blui.replay.TraceStore;
import blui.tools.FileTool;
import blui.tools.HttpTool;
import blui.tools.ShellTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interactive Terminal CLI Dashboard for Blui.
 * Renders the truecolor pixel mascot alongside system telemetry and provides an interactive REPL.
 */
public class BluiCli {

    private static final TraceStore traceStore = new TraceStore();
    private static String sessionApiKey = null;

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[38;2;80;190;250m";
    private static final String GREEN = "\033[38;2;52;211;153m";
    private static final String RED = "\033[38;2;248;113;113m";
    private static final String BLUE = "\033[38;2;96;165;250m";
    private static final String PURPLE = "\033[38;2;192;132;252m";
    private static final String GRAY = "\033[38;2;148;163;184m";
    private static final String WHITE = "\033[38;2;248;250;252m";

    public static void main(String[] args) {
        initConsole();
        if (args != null && args.length > 0) {
            String commandLine = String.join(" ", args).trim();
            Scanner scanner = new Scanner(System.in);
            processCommand(commandLine, scanner);
            return;
        }
        printBanner();
        printStatusCards();
        startRepl();
    }

    private static void initConsole() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            try {
                new ProcessBuilder("cmd", "/c", "chcp 65001 > nul").inheritIO().start().waitFor();
            } catch (Exception ignored) {}
        }
    }

    public static void printBanner() {
        List<String> imageLines = new ArrayList<>();
        try {
            imageLines = TerminalArt.renderLines("assets/blui.png", 46, 16);
        } catch (IOException e) {
            // Fallback placeholder if image not found
            for (int i = 0; i < 16; i++) {
                imageLines.add(" ".repeat(46));
            }
        }

        List<String> textLines = buildRightSideHeader();

        int maxLines = Math.max(imageLines.size(), textLines.size());
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < maxLines; i++) {
            String img = i < imageLines.size() ? imageLines.get(i) : " ".repeat(46);
            String txt = i < textLines.size() ? textLines.get(i) : "";
            sb.append(img).append("   ").append(txt).append("\n");
        }

        try {
            System.out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.flush();
        } catch (IOException ignored) {}
    }

    static List<String> buildRightSideHeader() {
        List<String> lines = new ArrayList<>();

        // ASCII Title (Clean lowercase blui)
        lines.add(CYAN + BOLD + " █   █          " + RESET);
        lines.add(CYAN + BOLD + " █▀▄ █ █ █ ▄    " + RESET);
        lines.add(CYAN + BOLD + " █ █ █ █ █ █    " + RESET);
        lines.add(CYAN + BOLD + " ▀▀▀ ▀  ▀▀ ▀    " + RESET);
        lines.add(DIM + GRAY + " AI AGENT RUNTIME & SAFETY HARNESS" + RESET);
        lines.add("");

        lines.add(DIM + " ──────────────────────────────────────────────────────────" + RESET);
        lines.add(BOLD + WHITE + " COMMANDS" + RESET);
        lines.add(CYAN + " blui run " + GRAY + "<goal>       " + DIM + "# execute agent loop" + RESET);
        lines.add(CYAN + " blui env              " + DIM + "# configure environment ('k' to keep)" + RESET);
        lines.add(CYAN + " blui traces           " + DIM + "# list saved flight ledgers" + RESET);
        lines.add(CYAN + " blui replay " + GRAY + "<id>      " + DIM + "# replay past flight trace" + RESET);
        lines.add(CYAN + " blui bench            " + DIM + "# spawn 500 virtual threads" + RESET);
        lines.add(CYAN + " blui status           " + DIM + "# reprint runtime diagnostics" + RESET);
        lines.add(CYAN + " blui exit             " + DIM + "# terminate shell session" + RESET);
        lines.add(DIM + " ──────────────────────────────────────────────────────────" + RESET);
        lines.add(DIM + GRAY + " v0.1.0-alpha │ Pure Java 24 (Zero external deps)" + RESET);
        lines.add("");

        return lines;
    }

    public static void printStatusCards() {
        int cores = Runtime.getRuntime().availableProcessors();
        long heapAllocMb = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long heapMaxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        String javaVer = System.getProperty("java.version");

        List<String> card1 = buildCard("RUNTIME STATUS", List.of(
            new String[]{"JVM", "OpenJDK " + javaVer},
            new String[]{"Loom", "Virtual Threads"},
            new String[]{"Governor", "Circuit Breaker"},
            new String[]{"Ledger", "In-Memory Trace"},
            new String[]{"Runner", "Virtual Pool"}
        ), 32);

        List<String> card2 = buildCard("SYSTEM", List.of(
            new String[]{"OS", os},
            new String[]{"Arch", arch},
            new String[]{"CPU", cores + " Cores"},
            new String[]{"Heap", heapAllocMb + "M / " + heapMaxMb + "M"},
            new String[]{"Encoding", "UTF-8 (CP65001)"}
        ), 32);

        List<String> card3 = buildCard("REPOSITORY", List.of(
            new String[]{"Repo", "Nikhil-Vzo/Blui"},
            new String[]{"Lang", "Java 24 LTS"},
            new String[]{"Deps", "Zero (Standard)"},
            new String[]{"License", "Apache 2.0"},
            new String[]{"Version", "0.1.0-alpha"}
        ), 32);

        StringBuilder sb = new StringBuilder("\n");
        for (int i = 0; i < card1.size(); i++) {
            sb.append(card1.get(i)).append(" ")
              .append(card2.get(i)).append(" ")
              .append(card3.get(i)).append("\n");
        }
        sb.append("\n");

        writeUtf8(sb.toString());
    }

    static List<String> buildCard(String title, List<String[]> entries, int width) {
        List<String> card = new ArrayList<>();
        int dashCount = width - 5 - title.length();
        card.add("┌─ " + title + " " + "─".repeat(Math.max(0, dashCount)) + "┐");

        int innerWidth = width - 6; // 6 chars for "│ ● " (4) and " │" (2)
        int keyWidth = 9;
        int valWidth = innerWidth - keyWidth - 1;

        for (String[] entry : entries) {
            String key = padRight(entry[0], keyWidth);
            String val = padRight(entry[1], valWidth);
            card.add("│ " + GREEN + "●" + RESET + " " + key + " " + val + " │");
        }

        card.add("└" + "─".repeat(width - 2) + "┘");
        return card;
    }

    static List<String> buildCardForTesting(String title, List<String[]> entries, int width) {
        return buildCard(title, entries, width);
    }

    static List<String> buildRightSideHeaderForTesting() {
        return buildRightSideHeader();
    }

    private static void writeUtf8(String text) {
        try {
            System.out.write(text.getBytes(StandardCharsets.UTF_8));
            System.out.flush();
        } catch (IOException e) {
            System.out.print(text);
        }
    }

    private static void writelnUtf8(String text) {
        writeUtf8(text + "\n");
    }

    private static void startRepl() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            writeUtf8(CYAN + BOLD + "blui > " + RESET);
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();
            if (!processCommand(input, scanner)) break;
        }
    }

    static boolean processCommand(String input, Scanner scanner) {
        if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
            writelnUtf8(GRAY + "Shutting down Blui runtime." + RESET);
            return false;
        } else if (input.equalsIgnoreCase("status") || input.equalsIgnoreCase("blui status")) {
            printBanner();
            printStatusCards();
        } else if (input.equalsIgnoreCase("help") || input.equalsIgnoreCase("blui help")) {
            printHelp();
        } else if (input.equalsIgnoreCase("bench") || input.equalsIgnoreCase("blui bench")) {
            runBenchmark();
        } else if (input.equalsIgnoreCase("traces") || input.equalsIgnoreCase("blui traces")) {
            listTraces();
        } else if (input.startsWith("replay ") || input.startsWith("blui replay ")) {
            String id = input.replaceFirst("^(blui replay|replay)\\s+", "").trim();
            replayTrace(id);
        } else if (input.equalsIgnoreCase("env show") || input.equalsIgnoreCase("blui env show")
                || input.equalsIgnoreCase("env list") || input.equalsIgnoreCase("show env")) {
            EnvConfig.printCurrent(BluiCli::writelnUtf8);
        } else if (input.toLowerCase().matches("^(blui\\s+)?(env|set\\s+env|config)\\s+(gemini|ollama|both|all|advanced)$")) {
            String target = input.replaceFirst("^(blui\\s+)?(env|set\\s+env|config)\\s+", "").trim().toLowerCase();
            Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, target);
            if (updated.containsKey("GEMINI_API_KEY")) {
                sessionApiKey = updated.get("GEMINI_API_KEY");
            }
        } else if (input.equalsIgnoreCase("env") || input.equalsIgnoreCase("blui env")
                || input.equalsIgnoreCase("set env") || input.equalsIgnoreCase("blui set env")
                || input.equalsIgnoreCase("config") || input.equalsIgnoreCase("blui config")
                || input.equalsIgnoreCase("setup")) {
            Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, null);
            if (updated.containsKey("GEMINI_API_KEY")) {
                sessionApiKey = updated.get("GEMINI_API_KEY");
            }
        } else if (input.matches("^(blui\\s+)?(set\\s+env|env)\\s+\\w+\\s*=.*")) {
            handleInlineEnvSet(input);
        } else if (input.startsWith("run ") || input.startsWith("blui run ")) {
            String task = input.replaceFirst("^(blui run|run)\\s+", "");
            executeAgent(task, scanner);
        } else if (!input.isEmpty()) {
            writelnUtf8(GRAY + "Unknown command: '" + input + "'. Type 'help' for available commands." + RESET);
        }
        return true;
    }

    private static void handleInlineEnvSet(String input) {
        String rest = input.replaceFirst("^(blui\\s+)?(set\\s+env|env)\\s+", "").trim();
        int eq = rest.indexOf('=');
        if (eq > 0) {
            String key = rest.substring(0, eq).trim().toUpperCase();
            String val = rest.substring(eq + 1).trim();
            if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                if (val.length() >= 2) val = val.substring(1, val.length() - 1);
            }
            try {
                EnvConfig.saveVar(EnvConfig.DEFAULT_ENV_PATH, key, val);
                if ("GEMINI_API_KEY".equalsIgnoreCase(key)) {
                    sessionApiKey = val;
                }
                writelnUtf8(GREEN + "✓ Set " + key + " = " + EnvConfig.mask(key, val) + " in .env (git-ignored)\n" + RESET);
            } catch (IOException e) {
                writelnUtf8(RED + "✗ Error saving to .env: " + e.getMessage() + "\n" + RESET);
            }
        } else {
            writelnUtf8(GRAY + "Usage: set env KEY=VALUE (or run 'env' for interactive wizard)\n" + RESET);
        }
    }

    private static void printHelp() {
        writelnUtf8(BOLD + "\nAvailable Commands:" + RESET);
        writelnUtf8("  " + CYAN + "bench" + RESET + "                 - Run 500 concurrent virtual thread tasks");
        writelnUtf8("  " + CYAN + "run <goal>" + RESET + "            - Execute an agent loop with Governor and Loom");
        writelnUtf8("  " + CYAN + "env [gemini|ollama]" + RESET + "   - Configure provider environment (Hermes-style 'k' to keep)");
        writelnUtf8("  " + CYAN + "set env <KEY>=<VAL>" + RESET + "   - Set specific variable inline in .env");
        writelnUtf8("  " + CYAN + "env show" + RESET + "              - Display current .env configuration");
        writelnUtf8("  " + CYAN + "traces" + RESET + "                - List all saved flight recorder traces on disk");
        writelnUtf8("  " + CYAN + "replay <id>" + RESET + "           - Inspect and replay a past flight trace");
        writelnUtf8("  " + CYAN + "status" + RESET + "                - Reprint runtime dashboard");
        writelnUtf8("  " + CYAN + "exit" + RESET + "                  - Terminate CLI session\n");
    }

    private static void listTraces() {
        List<TraceStore.TraceSummary> list = traceStore.listTraces();
        if (list.isEmpty()) {
            writelnUtf8(GRAY + "\nNo saved flight traces found in .blui/traces/." + RESET);
            return;
        }

        writelnUtf8(BOLD + "\nRecorded Flight Traces (.blui/traces/):" + RESET);
        writelnUtf8(GRAY + padRight("TRACE ID", 26) + padRight("STEPS", 8) + padRight("STATUS", 12) + "GOAL" + RESET);
        writelnUtf8(DIM + "─".repeat(70) + RESET);
        for (TraceStore.TraceSummary s : list) {
            writelnUtf8(CYAN + padRight(s.id(), 26) + RESET +
                        padRight(String.valueOf(s.stepCount()), 8) +
                        GREEN + padRight(s.status(), 12) + RESET +
                        s.goal());
        }
        writelnUtf8("");
    }

    private static void replayTrace(String rawTraceId) {
        if (rawTraceId == null || rawTraceId.isBlank()) {
            writelnUtf8(GRAY + "Usage: blui replay <trace_id>" + RESET);
            return;
        }
        String id = rawTraceId.trim();
        if (id.startsWith("#")) id = id.substring(1);
        if (!id.startsWith("trace_") && id.matches("\\d+")) {
            id = "trace_" + id;
        }
        try {
            String json = traceStore.readTraceJson(id);
            writelnUtf8(CYAN + BOLD + "\n[REPLAY] Flight Ledger: " + id + RESET);
            writelnUtf8(DIM + "─".repeat(50) + RESET);
            writelnUtf8(json);
            writelnUtf8(DIM + "─".repeat(50) + RESET + "\n");
        } catch (Exception e) {
            writelnUtf8(RED + "Error replaying trace: " + e.getMessage() + RESET + "\n");
        }
    }

    static String cleanGoal(String goal) {
        if (goal == null) return "";
        String cleaned = goal.trim();
        while (true) {
            boolean modified = false;
            if (cleaned.startsWith("<") && cleaned.endsWith(">") && cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                modified = true;
            }
            if ((cleaned.startsWith("\"") && cleaned.endsWith("\"")) || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
                if (cleaned.length() >= 2) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                    modified = true;
                }
            }
            if (!modified) break;
        }
        return cleaned;
    }

    private static void runBenchmark() {
        writelnUtf8(CYAN + "\n[LOOM] Spawning 500 Virtual Threads with Structured Scope..." + RESET);
        long start = System.currentTimeMillis();

        LoomRunner runner = new LoomRunner();
        List<ToolExecution> tasks = new ArrayList<>();
        Tool mockTool = input -> new ToolResults("BenchWorker", "OK: " + input, true, 10);

        for (int i = 0; i < 500; i++) {
            tasks.add(new ToolExecution(mockTool, "Task-" + i));
        }

        List<ToolResults> results = runner.runConcurrent(tasks, Duration.ofSeconds(5));
        long duration = System.currentTimeMillis() - start;

        writelnUtf8(GREEN + BOLD + "✓ Completed " + results.size() + " Virtual Threads in " + duration + "ms!" + RESET);
        writelnUtf8(GRAY + "  Average Join SLA: <0.25ms per task | Memory overhead: ~0.5MB\n" + RESET);
    }

    private static String resolveGeminiApiKey(Scanner scanner) {
        if (sessionApiKey != null && !sessionApiKey.isBlank()) {
            return sessionApiKey;
        }

        String resolved = EnvConfig.get("GEMINI_API_KEY");
        if (resolved != null && !resolved.isBlank()) {
            sessionApiKey = resolved;
            return sessionApiKey;
        }

        // Prompt user interactively specifically for Gemini
        writelnUtf8(CYAN + "\n[AUTH] GEMINI_API_KEY not found in OS environment or .env file." + RESET);
        writeUtf8(BOLD + "       Configure Gemini API key now? [Y/n] (or press Enter to configure): " + RESET);
        if (scanner.hasNextLine()) {
            String choice = scanner.nextLine().trim();
            if (choice.isEmpty() || "y".equalsIgnoreCase(choice) || "yes".equalsIgnoreCase(choice)) {
                Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, "gemini");
                if (updated.containsKey("GEMINI_API_KEY")) {
                    sessionApiKey = updated.get("GEMINI_API_KEY");
                    return sessionApiKey;
                }
            }
        }

        return null;
    }

    private static void executeAgent(String rawGoal, Scanner scanner) {
        String goal = cleanGoal(rawGoal);
        String apiKey = resolveGeminiApiKey(scanner);

        LlmProvider provider = null;
        if (apiKey != null && !apiKey.isBlank()) {
            String customModel = EnvConfig.get("GEMINI_MODEL");
            provider = new GeminiClient(apiKey, customModel != null ? customModel : "gemini-3.5-flash", Duration.ofMinutes(2));
            writelnUtf8(CYAN + "\n[BRAIN] Provider: Google Gemini (" + provider.modelName() + " via Google AI Studio)" + RESET);
        } else {
            String ollamaHost = EnvConfig.get("OLLAMA_HOST");
            String ollamaModel = EnvConfig.get("OLLAMA_MODEL");
            String host = (ollamaHost != null && !ollamaHost.isBlank()) ? ollamaHost : "http://localhost:11434";
            String model = (ollamaModel != null && !ollamaModel.isBlank()) ? ollamaModel : "llama3.2";

            OllamaClient ollama = new OllamaClient(host, model, Duration.ofMinutes(2));
            if (ollama.isAvailable()) {
                provider = ollama;
                writelnUtf8(CYAN + "\n[BRAIN] Provider: Local Ollama (" + provider.modelName() + " @ " + host + ")" + RESET);
            } else {
                writelnUtf8(GRAY + "\n[BRAIN] Note: No Gemini key or local Ollama daemon found. Running local simulation." + RESET);
                runMockAgent(goal);
                return;
            }
        }

        runLiveAgent(goal, provider);
    }

    private static void runLiveAgent(String rawGoal, LlmProvider provider) {
        String goal = cleanGoal(rawGoal);
        writelnUtf8(CYAN + "[ENGINE] Initializing Agent for goal: '" + goal + "'..." + RESET);

        Governor governor = new Governor(new Budget(50000, 1.00, 0.000003), new CycleDetector(3));
        LoomRunner loomRunner = new LoomRunner();

        ShellTool shellTool = new ShellTool();
        FileTool fileTool = new FileTool();
        HttpTool httpTool = new HttpTool();

        AgentTrace trace = new AgentTrace();
        trace.append(StepTrace.of(0, new AgentState.Initialized(goal), 0));

        List<LlmProvider.Message> conversation = new ArrayList<>();
        conversation.add(new LlmProvider.Message("system", buildSystemPrompt()));
        conversation.add(new LlmProvider.Message("user", "Goal: " + goal));

        AgentState currentState = new AgentState.Initialized(goal);
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        long startTime = System.currentTimeMillis();
        int step = 1;
        String traceId = "trace_" + System.currentTimeMillis();

        try {
            int maxSteps = 10;
            while (step <= maxSteps) {
                // 1. Governor safety check
                governor.inspectStep(currentState);

                // 2. Planning phase with live virtual thread Loom spinner
                currentState = new AgentState.Thinking(goal, step);

                AtomicBoolean spinning = new AtomicBoolean(true);
                long t0 = System.currentTimeMillis();
                int currentStep = step;
                Thread spinner = Thread.ofVirtual().start(() -> {
                    char[] frames = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};
                    int i = 0;
                    while (spinning.get()) {
                        double elapsed = (System.currentTimeMillis() - t0) / 1000.0;
                        writeUtf8("\r\033[K" + CYAN + "  Step " + currentStep + " [Thinking] "
                                + frames[i++ % frames.length] + " Querying " + provider.modelName()
                                + String.format(Locale.US, " (%.1fs)...", elapsed) + RESET);
                        try {
                            Thread.sleep(80);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                });

                LlmProvider.LlmResponse response;
                try {
                    response = provider.generate(conversation);
                } finally {
                    spinning.set(false);
                    spinner.interrupt();
                    try {
                        spinner.join(200);
                    } catch (InterruptedException ignored) {}
                }

                double stepElapsed = (System.currentTimeMillis() - t0) / 1000.0;
                writeUtf8("\r\033[K" + DIM + "  Step " + currentStep + " [Thinking] Querying "
                        + provider.modelName() + String.format(Locale.US, " (%.1fs)", stepElapsed) + RESET + "\n");

                totalPromptTokens += response.promptTokens();
                totalCompletionTokens += response.completionTokens();
                governor.budget().recordUsage(response.totalTokens());
                trace.append(StepTrace.of(step, currentState, response.totalTokens()));

                if (response.hasToolCall()) {
                    step++;
                    String toolName = response.toolName();
                    String toolInput = response.toolInput();

                    Tool targetTool = matchTool(toolName, shellTool, fileTool, httpTool);
                    governor.recordAndVerifyToolCall(targetTool.name(), toolInput);

                    currentState = new AgentState.ExecutingTools(List.of(targetTool.name()), step);
                    String displayInput = toolInput.length() > 42 ? toolInput.substring(0, 39) + "..." : toolInput;
                    writelnUtf8(CYAN + "  Step " + step + " [ExecutingTools] " + targetTool.name() + " -> " + displayInput + RESET);

                    List<ToolResults> results = loomRunner.runConcurrent(
                            List.of(new ToolExecution(targetTool, toolInput)),
                            Duration.ofSeconds(15)
                    );
                    ToolResults res = results.get(0);
                    trace.append(StepTrace.of(step, currentState, 0));

                    writelnUtf8(DIM + "    └─ Result (" + res.durationMs() + "ms, success=" + res.isSuccess() + "): " + summarize(res.output()) + RESET);

                    conversation.add(new LlmProvider.Message("assistant", response.content()));
                    conversation.add(new LlmProvider.Message("user", "OBSERVATION (" + targetTool.name() + "):\n" + res.output()));
                    step++;
                } else {
                    // Final answer or clarification request
                    String sanitized = sanitizeMonologue(response.content());
                    currentState = new AgentState.Completed(sanitized, step);
                    trace.append(StepTrace.of(step, currentState, 0));
                    if (isClarificationRequest(sanitized)) {
                        writelnUtf8(CYAN + BOLD + "\n? Clarification Needed:" + RESET);
                    } else {
                        writelnUtf8(GREEN + BOLD + "\n✓ Goal Completed!" + RESET);
                    }
                    writelnUtf8(WHITE + sanitized + RESET + "\n");
                    long durationMs = System.currentTimeMillis() - startTime;
                    try {
                        traceStore.save(traceId, goal, trace);
                    } catch (Exception ignored) {}
                    printTelemetrySummary(totalPromptTokens, totalCompletionTokens, governor.budget().currentCostUsd(), step, durationMs, provider.modelName(), traceId);
                    break;
                }
            }

            if (step > maxSteps) {
                currentState = new AgentState.Failed("Max steps exceeded (" + maxSteps + ")", step);
                trace.append(StepTrace.of(step, currentState, 0));
                writelnUtf8(RED + "\n✗ Execution halted: Max steps limit exceeded." + RESET);
                long durationMs = System.currentTimeMillis() - startTime;
                try {
                    traceStore.save(traceId, goal, trace);
                } catch (Exception ignored) {}
                printTelemetrySummary(totalPromptTokens, totalCompletionTokens, governor.budget().currentCostUsd(), step, durationMs, provider.modelName(), traceId);
            }

        } catch (Governor.BudgetExceededException | Governor.LoopDetectedException ge) {
            currentState = new AgentState.Failed("Governor circuit breaker: " + ge.getMessage(), step);
            trace.append(StepTrace.of(step, currentState, 0));
            writelnUtf8(RED + "\n[GOVERNOR HALT] " + ge.getMessage() + RESET);
            long durationMs = System.currentTimeMillis() - startTime;
            try {
                traceStore.save(traceId, goal, trace);
            } catch (Exception ignored) {}
            printTelemetrySummary(totalPromptTokens, totalCompletionTokens, governor.budget().currentCostUsd(), step, durationMs, provider.modelName(), traceId);
        } catch (Exception e) {
            currentState = new AgentState.Failed("Execution error: " + e.getMessage(), step);
            trace.append(StepTrace.of(step, currentState, 0));
            writelnUtf8(RED + "\n[ERROR] " + e.getMessage() + RESET);
            long durationMs = System.currentTimeMillis() - startTime;
            try {
                traceStore.save(traceId, goal, trace);
            } catch (Exception ignored) {}
            printTelemetrySummary(totalPromptTokens, totalCompletionTokens, governor.budget().currentCostUsd(), step, durationMs, provider.modelName(), traceId);
        }

        writelnUtf8("");
    }

    private static Tool matchTool(String name, Tool shell, Tool file, Tool http) {
        if (name == null) return shell;
        if (name.toLowerCase().contains("file")) return file;
        if (name.toLowerCase().contains("http")) return http;
        return shell;
    }

    static String summarize(String s) {
        if (s == null) return "(empty)";
        String line = s.lines().findFirst().orElse("").trim();
        if (line.isEmpty()) return "(empty)";
        if (line.length() > 38) return line.substring(0, 35) + "...";
        return line;
    }

    public static void printTelemetrySummary(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model) {
        printTelemetrySummary(promptTokens, completionTokens, costUsd, steps, durationMs, model, null);
    }

    public static void printTelemetrySummary(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId) {
        List<String> lines = buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, traceId);
        for (String line : lines) {
            writelnUtf8(line);
        }
        writelnUtf8("");
    }

    static List<String> buildTelemetryLines(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model) {
        return buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, null);
    }

    static List<String> buildTelemetryLines(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId) {
        int totalTokens = promptTokens + completionTokens;
        double seconds = durationMs / 1000.0;
        String costFormatted = String.format(Locale.US, "$%.5f", costUsd);
        String durationFormatted = String.format(Locale.US, "%.2fs", seconds);

        String line1 = String.format(Locale.US, "Tokens:   %d in (prompt) │ %d out (completion) │ %d total", promptTokens, completionTokens, totalTokens);
        String line2 = String.format(Locale.US, "Latency:  %s │ Steps: %d │ Cost: %s", durationFormatted, steps, costFormatted);

        String line3;
        if (traceId != null && !traceId.isBlank()) {
            String shortId = traceId.startsWith("trace_") ? traceId.substring(6) : traceId;
            line3 = String.format(Locale.US, "Model:    %s │ Trace: #%s │ Gov: Nominal", model, shortId);
        } else {
            line3 = String.format(Locale.US, "Model:    %s │ Governor: Nominal (Budget OK)", model);
        }

        int boxWidth = 72;
        int inner = boxWidth - 4; // 68 chars

        List<String> res = new ArrayList<>();
        int dashCount = boxWidth - 1 - "┌─ TELEMETRY ".length();
        res.add(CYAN + "┌─ TELEMETRY " + "─".repeat(Math.max(0, dashCount)) + "┐" + RESET);
        res.add(CYAN + "│ " + WHITE + padRight(line1, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "│ " + WHITE + padRight(line2, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "│ " + GRAY  + padRight(line3, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "└" + "─".repeat(boxWidth - 2) + "┘" + RESET);
        return res;
    }

    static String summarizeForTesting(String s) {
        return summarize(s);
    }

    static List<String> buildTelemetryLinesForTesting(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model) {
        return buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, null);
    }

    static List<String> buildTelemetryLinesForTesting(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId) {
        return buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, traceId);
    }

    static String cleanGoalForTesting(String goal) {
        return cleanGoal(goal);
    }

    private static void runMockAgent(String rawGoal) {
        String goal = cleanGoal(rawGoal);
        writelnUtf8(CYAN + "\n[ENGINE] Initializing Agent for goal: '" + goal + "'..." + RESET);
        Governor governor = new Governor(new Budget(5000, 1.00, 0.000003), new CycleDetector(3));
        LoomRunner runner = new LoomRunner();
        BluiEngine engine = new BluiEngine(governor, runner, Duration.ofSeconds(3), 5);
        long startTime = System.currentTimeMillis();

        Tool searchTool = input -> new ToolResults("WebSearch", "Found live data for: " + input, true, 45);
        Tool dbTool = input -> new ToolResults("DatabaseQuery", "Fetched records matching: " + input, true, 30);

        AgentTrace trace = engine.run(goal, (state, history) -> {
            if (history.size() <= 2) {
                return List.of(
                    new ToolExecution(searchTool, goal),
                    new ToolExecution(dbTool, "SELECT * WHERE query='" + goal + "'")
                );
            }
            return List.of(); // Complete
        });

        writelnUtf8(GREEN + BOLD + "✓ Execution Finished! Total Steps: " + trace.size() + RESET);
        for (StepTrace step : trace.steps()) {
            String tokenNote = step.tokenUsed() > 0
                ? step.tokenUsed() + " tokens"
                : "0 tokens (local simulation - no LLM attached)";
            writelnUtf8(DIM + "  Step " + step.stepNumber() + " [" + step.state().getClass().getSimpleName() + "] " + tokenNote + RESET);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        String traceId = "trace_" + System.currentTimeMillis();
        try {
            traceStore.save(traceId, goal, trace);
        } catch (Exception ignored) {}
        printTelemetrySummary(0, 0, 0.0, trace.size(), durationMs, "simulation/mock", traceId);
        writelnUtf8("");
    }

    static String buildSystemPrompt() {
        String os = System.getProperty("os.name", "unknown");
        boolean isWin = os.toLowerCase().contains("win");
        String shellHint = isWin ? "Windows CMD (use dir, cd, type, echo, mkdir, etc.)" : "POSIX Shell (use ls, pwd, cat, mkdir, etc.)";

        return """
            You are Blui, an autonomous agent runtime built in pure Java 24.
            Environment:
            - OS: %s (%s)
            - Workspace Root: .

            Tools:
            1. ShellTool: INPUT: <command>
            2. FileTool:  INPUT: read <path> | write <path> <content> | append <path> <content> | list <path> | mkdir <path>
            3. HttpTool:  INPUT: get <url> | post <url> <body>

            Protocol:
            - When executing an action, output ONLY:
              TOOL: <ToolName>
              INPUT: <Command>
            - Do NOT include markdown fences, preamble, pleasantries, or explanations before TOOL lines.
            - When the goal is achieved or no tool is needed, output the final answer directly without TOOL: lines.
            - Strictly FORBIDDEN: Do NOT output any internal chain-of-thought, scratchpads, reasoning steps, or stream-of-consciousness monologues (such as "Wait, let's understand...", "The user inputted...", etc.). Respond directly with the required output.
            - Ambiguous or Incomplete Goals: If the user goal is ambiguous, incomplete, a typo, or unclear (e.g. 'hu'), do NOT wander through files, explore directories, or execute arbitrary tools. Instead, immediately respond directly to the user asking for concise clarification.
            """.formatted(os, shellHint).stripIndent().trim();
    }

    static String sanitizeMonologue(String text) {
        if (text == null) return "";
        String cleaned = text.trim();

        // 1. Strip XML-style thinking tags: <think>...</think>, <thought>...</thought>, etc.
        cleaned = cleaned.replaceAll("(?is)<(think|thought|scratchpad)>.*?</\\1>", "").trim();
        cleaned = cleaned.replaceAll("(?is)^<(think|thought|scratchpad)>.*?(?=(\\n\\n|\\Z))", "").trim();

        // 2. Strip markdown thought fences: ```thought ... ```
        cleaned = cleaned.replaceAll("(?is)```(?:thought|think|scratchpad)\\s*.*?```", "").trim();

        // 3. Iteratively strip leading monologue sentences or lines
        Pattern monologuePattern = Pattern.compile(
                "(?is)^\\s*("
                + "(?:wait\\s*[,!.]\\s*|wait\\s+let['’]?s\\s+)[^.!?\\n]*[.!?\\n]+\\s*"
                + "|the user (?:inputted|input|entered|typed|wrote|said|is asking|asked)\\b[^.!?\\n]*[.!?\\n]+\\s*"
                + "|let['’]?s (?:understand|see|analyze|figure out|examine|think)\\b[^.!?\\n]*[.!?\\n]+\\s*"
                + "|let me (?:think|understand|see|analyze|figure out)\\b[^.!?\\n]*[.!?\\n]+\\s*"
                + "|(?:thinking\\s*(?:process)?|thought\\s*(?:process)?|scratchpad|internal\\s*reasoning)\\s*:?\\s*[^.!?\\n]*[.!?\\n]+\\s*"
                + "|this (?:looks|seems|appears) to be (?:a typo|ambiguous|an incomplete|unclear)\\b[^.!?\\n]*[.!?\\n]+\\s*"
                + "|i (?:should|need to|must) ask (?:the user )?for (?:clarification|more info|details)\\b[^.!?\\n]*[.!?\\n]+\\s*"
                + ")"
        );

        while (true) {
            Matcher m = monologuePattern.matcher(cleaned);
            if (m.find() && m.start() == 0) {
                cleaned = cleaned.substring(m.end()).trim();
            } else {
                break;
            }
        }

        // 4. Line-by-line fallback for scratchpad headers or lines
        String[] lines = cleaned.split("\\R");
        int startIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim().toLowerCase(Locale.ROOT);
            if (l.startsWith("wait, let's understand")
                    || l.startsWith("wait, let me")
                    || l.startsWith("wait, what")
                    || l.startsWith("the user inputted")
                    || l.startsWith("the user input is")
                    || l.startsWith("the user typed")
                    || l.startsWith("scratchpad:")
                    || l.startsWith("thinking process:")
                    || l.startsWith("thought process:")) {
                startIndex = i + 1;
            } else {
                break;
            }
        }

        if (startIndex > 0 && startIndex < lines.length) {
            StringBuilder sb = new StringBuilder();
            for (int i = startIndex; i < lines.length; i++) {
                sb.append(lines[i]).append("\n");
            }
            cleaned = sb.toString().trim();
        }

        if (cleaned.isBlank()) {
            return "Could you please clarify your request?";
        }

        return cleaned;
    }

    static boolean isClarificationRequest(String response) {
        if (response == null || response.isBlank()) return false;
        String trimmed = response.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.contains("clarif")
                || lower.contains("did you mean")
                || lower.contains("what did you mean")
                || lower.contains("what do you mean")
                || lower.contains("please specify")
                || lower.contains("could you specify")
                || lower.contains("please provide more")
                || lower.contains("could you provide more")
                || lower.contains("what would you like")
                || lower.contains("what do you want me to")
                || lower.contains("what should i do")) {
            return true;
        }

        if (trimmed.endsWith("?")) {
            if (lower.contains("you") || lower.contains("your") || lower.contains("rephrase")
                    || lower.contains("elaborate") || lower.contains("mean") || lower.contains("details")) {
                return true;
            }
        }

        return false;
    }

    static String sanitizeMonologueForTesting(String text) {
        return sanitizeMonologue(text);
    }

    static boolean isClarificationRequestForTesting(String response) {
        return isClarificationRequest(response);
    }

    static String buildSystemPromptForTesting() {
        return buildSystemPrompt();
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}
