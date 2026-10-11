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
import blui.swarm.SwarmAgent;
import blui.swarm.SwarmEngine;
import blui.swarm.SwarmGovernor;
import blui.swarm.SwarmResult;
import blui.swarm.SwarmTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
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
            try {
                new ProcessBuilder("cmd", "/c", "reg add HKCU\\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f > nul 2>&1").start();
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
        lines.add(CYAN + " blui swarm " + GRAY + "<goal>     " + DIM + "# parallel agent swarm" + RESET);
        lines.add(CYAN + " blui env              " + DIM + "# configure environment ('k' to keep)" + RESET);
        lines.add(CYAN + " blui traces           " + DIM + "# list saved flight ledgers" + RESET);
        lines.add(CYAN + " blui replay " + GRAY + "<id>      " + DIM + "# replay past flight trace" + RESET);
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
        } else if (input.matches("^(blui\\s+)?(swarm|/swarm|boost|/boost|goal|/goal)(\\s+.*)?$")) {
            executeSwarm(input, scanner);
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
        writelnUtf8("  " + CYAN + "run <goal>" + RESET + "            - Execute an agent loop with Governor and Loom");
        writelnUtf8("  " + CYAN + "swarm <goal>" + RESET + "          - Execute parallel multi-agent swarm on virtual threads");
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
            provider = new GeminiClient(apiKey, customModel != null ? customModel : GeminiClient.DEFAULT_MODEL, Duration.ofMinutes(2));
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

        // 1. Strip XML-style thinking tags: <think>...</think>, <thought>...</thought>, <reasoning>...</reasoning>, etc.
        cleaned = cleaned.replaceAll("(?is)<(think|thought|thinking|scratchpad|reasoning|reflection|analysis)(\\s+[^>]*)?>.*?</\\1>", "").trim();
        cleaned = cleaned.replaceAll("(?is)^<(think|thought|thinking|scratchpad|reasoning|reflection|analysis)(\\s+[^>]*)?>.*?(?=(\\n\\n|\\Z))", "").trim();

        // 2. Strip markdown thought fences: ```thought ... ``` or ```thinking ... ```
        cleaned = cleaned.replaceAll("(?is)```(?:thought|think|thinking|scratchpad|reasoning|analysis)\\s*.*?```", "").trim();

        // 3. Strip markdown headers for thinking/scratchpads: ### Thinking, ## Thought Process, # Scratchpad, etc.
        cleaned = cleaned.replaceAll("(?im)^#{1,4}\\s*(?:thinking(?:\\s+process)?|thought(?:\\s+process)?|scratchpad|reasoning|analysis|reflection)\\s*$\\R?", "").trim();

        // 4. Strip italic/bold-wrapped whole monologue clauses: *Wait, let's understand...* or **Thinking: ...**
        cleaned = cleaned.replaceAll("(?is)^\\s*\\*{1,2}(?:wait\\b|the user\\b|let['’]?s\\b|let me\\b|thinking|thought|scratchpad|reasoning).*?\\*{1,2}\\s*", "").trim();

        // 5. Iteratively strip leading monologue sentences or lines
        Pattern monologuePattern = Pattern.compile(
                "(?is)^\\s*(?:\\*{1,2}|#{1,4}\\s*)?(?:(?:\\d+\\.|[-*•])\\s+)?("
                + "(?:wait\\s*[,!.:-]+\\s*|wait\\s+let['’]?s\\s+)[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
                + "|the user (?:inputted|input|entered|typed|wrote|said|is asking|asked|provided|gave|supplied|wants|is trying|is looking|needs|means)\\b[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
                + "|let['’]?s (?:understand|see|analyze|figure out|examine|think|ask|clarify)\\b[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
                + "|let me (?:think|understand|see|analyze|figure out|ask|clarify)\\b[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
                + "|(?:thinking\\s*(?:process)?|thought\\s*(?:process)?|scratchpad|internal\\s*reasoning|analysis)\\s*:?\\s*[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
                + "|this (?:looks|seems|appears) to be (?:a typo|ambiguous|an incomplete|unclear)\\b[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
                + "|i (?:should|need to|must) (?:ask|clarify)(?:\\s+(?:the user|for|with))?[^.!?\\n]*[.!?\\n]+[\"'\u201D\u2019*_]*\\s*"
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

        // 6. Line-by-line fallback for scratchpad headers or lines
        String[] lines = cleaned.split("\\R");
        int startIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].trim().toLowerCase(Locale.ROOT)
                    .replaceAll("^[#*_\\-\\d.\\s]+", "");
            if (l.startsWith("wait, let's understand")
                    || l.startsWith("wait, let me")
                    || l.startsWith("wait, what")
                    || l.startsWith("wait:")
                    || l.startsWith("wait -")
                    || l.startsWith("the user inputted")
                    || l.startsWith("the user input is")
                    || l.startsWith("the user entered")
                    || l.startsWith("the user typed")
                    || l.startsWith("the user provided")
                    || l.startsWith("the user wants")
                    || l.startsWith("the user needs")
                    || l.startsWith("the user is trying")
                    || l.startsWith("scratchpad:")
                    || l.startsWith("scratchpad")
                    || l.startsWith("thinking:")
                    || l.startsWith("thinking")
                    || l.startsWith("thinking process:")
                    || l.startsWith("thinking process")
                    || l.startsWith("thought:")
                    || l.startsWith("thought")
                    || l.startsWith("thought process:")
                    || l.startsWith("thought process")
                    || l.startsWith("reasoning:")
                    || l.startsWith("reasoning")
                    || l.startsWith("internal reasoning")) {
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

        cleaned = cleaned.replaceFirst("^[*_]+\\s*", "");

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
                || lower.contains("mean by")
                || lower.contains("please specify")
                || lower.contains("could you specify")
                || lower.contains("can you specify")
                || lower.contains("please provide more")
                || lower.contains("could you provide more")
                || lower.contains("can you provide more")
                || lower.contains("please elaborate")
                || lower.contains("could you elaborate")
                || lower.contains("can you elaborate")
                || lower.contains("please rephrase")
                || lower.contains("could you rephrase")
                || lower.contains("can you rephrase")
                || lower.contains("please be more specific")
                || lower.contains("could you be more specific")
                || lower.contains("can you be more specific")
                || lower.contains("what would you like")
                || lower.contains("what do you want me to")
                || lower.contains("what should i do")
                || lower.contains("what did you intend")) {
            return true;
        }

        String withoutQuotes = trimmed.replaceAll("[\"'\u201D\u2019]+$", "");
        if (withoutQuotes.endsWith("?")) {
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

    static record SwarmOptions(String goal, boolean boost) {}

    static SwarmOptions parseSwarmCommand(String input) {
        if (input == null || input.isBlank()) {
            return new SwarmOptions("audit blui codebase and runtime architecture", false);
        }

        String rest = input.trim();
        // Strip leading blui swarm, blui, or swarm keywords
        rest = rest.replaceFirst("(?i)^(blui\\s+)?(swarm|/swarm)\\s*", "").trim();

        // Check for boost flag (/boost, --boost, -boost, or standalone boost keyword)
        boolean boost = false;
        Pattern boostFlag = Pattern.compile("(?i)(^|\\s)(/(?:boost)|--(?:boost)|-(?:boost))(\\s+|$)");
        Matcher mBoost = boostFlag.matcher(rest);
        if (mBoost.find()) {
            boost = true;
            rest = (rest.substring(0, mBoost.start()) + " " + rest.substring(mBoost.end())).trim();
        } else if (rest.matches("(?i)^(/boost|boost)(\\s+.*|$)")) {
            boost = true;
            rest = rest.replaceFirst("(?i)^(/boost|boost)\\s*", "").trim();
        }

        // Check for goal flag (/goal, --goal, -goal, or goal keyword)
        Pattern goalFlag = Pattern.compile("(?i)(^|\\s)(/(?:goal)|--(?:goal)|-(?:goal)|goal)(\\s+|$)");
        Matcher mGoal = goalFlag.matcher(rest);
        if (mGoal.find()) {
            rest = rest.substring(mGoal.end()).trim();
        }

        String cleaned = cleanGoal(rest);
        if (cleaned.isBlank()) {
            cleaned = "audit blui codebase and runtime architecture";
        }
        return new SwarmOptions(cleaned, boost);
    }

    public static final SwarmAgent CONCURRENCY_TESTER = new SwarmAgent(
        "ConcurrencyTester",
        "High-Throughput Loom Stress",
        "Carrier thread saturation, virtual thread spawning overhead, and SLA join verification",
        "You are the Concurrency Tester specialist. Audit high-throughput virtual thread orchestration, task saturation, and thread lifecycle overhead."
    );
    public static final SwarmAgent MEMORY_AUDITOR = new SwarmAgent(
        "MemoryAuditor",
        "JVM Heap & Allocation Profile",
        "Zero-allocation streaming, garbage collection pressure, and footprint efficiency",
        "You are the Memory Auditor specialist. Audit JVM heap retention, memory footprint overhead, and garbage collection pressure."
    );
    public static final SwarmAgent DEPENDENCY_VERIFIER = new SwarmAgent(
        "DependencyVerifier",
        "Zero External Deps Hardening",
        "Standard library encapsulation, transitive hygiene, and artifact minimalism",
        "You are the Dependency Verifier specialist. Verify 100% pure OpenJDK standard library compliance and zero external dependencies."
    );
    public static final SwarmAgent LATENCY_OPTIMIZER = new SwarmAgent(
        "LatencyOptimizer",
        "Sub-Millisecond Wall Clock",
        "Kernel execution scheduling, IO round-trips, and critical path latency reduction",
        "You are the Latency Optimizer specialist. Profile wall-clock latency paths and identify sub-millisecond optimization opportunities."
    );

    private static void executeSwarm(String input, Scanner scanner) {
        SwarmOptions options = parseSwarmCommand(input);
        String goal = options.goal();
        boolean isBoost = options.boost();

        writelnUtf8(CYAN + BOLD + "\n[SWARM] Multi-Agent Swarm Orchestrator (Project Loom Virtual Threads)" + RESET);
        writelnUtf8(GRAY + "Target Goal: '" + WHITE + goal + GRAY + "'" + RESET);

        List<SwarmAgent> agents = new ArrayList<>(SwarmAgent.defaultPersonas());
        if (isBoost) {
            agents.addAll(List.of(CONCURRENCY_TESTER, MEMORY_AUDITOR, DEPENDENCY_VERIFIER, LATENCY_OPTIMIZER));
            writelnUtf8(PURPLE + BOLD + "[BOOST] ⚡ Turbo Concurrency Active: 8 Specialist Personas running on Loom Virtual Threads!" + RESET);
        } else {
            writelnUtf8(CYAN + "[LOOM] Concurrently dispatching " + agents.size() + " specialist personas with structured join SLA..." + RESET);
        }

        // Detect LLM provider if available
        String apiKey = sessionApiKey != null ? sessionApiKey : EnvConfig.get("GEMINI_API_KEY");
        LlmProvider provider = null;
        if (apiKey != null && !apiKey.isBlank()) {
            String customModel = EnvConfig.get("GEMINI_MODEL");
            provider = new GeminiClient(apiKey, customModel != null ? customModel : GeminiClient.DEFAULT_MODEL, Duration.ofSeconds(8));
            writelnUtf8(CYAN + "[BRAIN] Provider: Google Gemini (" + provider.modelName() + " via Google AI Studio)\n" + RESET);
        } else {
            writelnUtf8(GRAY + "[BRAIN] Simulation Mode: Zero-dependency virtual thread evaluation (pure JDK standard library)\n" + RESET);
        }

        SwarmGovernor governor = SwarmGovernor.standard();
        Duration joinTimeout = Duration.ofSeconds(20);
        SwarmEngine engine = new SwarmEngine(governor, provider, joinTimeout);
        SwarmTask task = SwarmTask.of(goal);

        final int agentCount = agents.size();
        class AgentUIState {
            final SwarmAgent agent;
            final AtomicInteger status = new AtomicInteger(0); // 0 = WAITING, 1 = RUNNING, 2 = SUCCESS, 3 = FAILED
            final AtomicLong startMs = new AtomicLong(0);
            final AtomicLong durationMs = new AtomicLong(0);
            AgentUIState(SwarmAgent agent) { this.agent = agent; }
        }

        List<AgentUIState> uiStates = new ArrayList<>();
        Map<String, AgentUIState> stateMap = new java.util.concurrent.ConcurrentHashMap<>();
        for (SwarmAgent a : agents) {
            AgentUIState state = new AgentUIState(a);
            uiStates.add(state);
            stateMap.put(a.name(), state);
        }

        boolean isInteractive = (System.console() != null && System.console().isTerminal())
                || "true".equalsIgnoreCase(System.getProperty("blui.interactive"))
                || "true".equalsIgnoreCase(System.getenv("BLUI_INTERACTIVE"));
        char[] spinnerFrames = {'⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏'};

        if (isInteractive) {
            // Print initial lines for animated terminal display
            for (AgentUIState s : uiStates) {
                writeUtf8("  " + CYAN + spinnerFrames[0] + RESET + " " + BOLD + "[" + padRight(s.agent.name(), 20) + "]" + RESET + " " + padRight(s.agent.role(), 34) + " " + DIM + "(0.00s)" + RESET + "\n");
            }
        }

        AtomicBoolean animating = new AtomicBoolean(isInteractive);
        long t0 = System.currentTimeMillis();

        Thread animator = isInteractive ? Thread.ofVirtual().start(() -> {
            int frame = 1;
            while (animating.get()) {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    break;
                }
                if (!animating.get()) break;

                StringBuilder sb = new StringBuilder();
                sb.append("\033[").append(agentCount).append("A");

                for (int i = 0; i < agentCount; i++) {
                    AgentUIState s = uiStates.get(i);
                    int st = s.status.get();
                    char icon = spinnerFrames[frame % spinnerFrames.length];
                    double elapsed = (st == 0 ? 0.0 : (st == 1 ? (System.currentTimeMillis() - (s.startMs.get() > 0 ? s.startMs.get() : t0)) : s.durationMs.get())) / 1000.0;

                    sb.append("\r\033[K");
                    if (st == 2) {
                        sb.append("  ").append(GREEN).append("✓").append(RESET).append(" ")
                          .append(BOLD).append("[").append(padRight(s.agent.name(), 20)).append("]").append(RESET).append(" ")
                          .append(padRight(s.agent.role(), 34)).append(" ")
                          .append(GREEN).append(String.format(Locale.US, "PASSED (%.2fs)", elapsed)).append(RESET).append("\n");
                    } else if (st == 3) {
                        sb.append("  ").append(RED).append("✗").append(RESET).append(" ")
                          .append(BOLD).append("[").append(padRight(s.agent.name(), 20)).append("]").append(RESET).append(" ")
                          .append(padRight(s.agent.role(), 34)).append(" ")
                          .append(RED).append("HALTED").append(RESET).append("\n");
                    } else {
                        sb.append("  ").append(CYAN).append(icon).append(RESET).append(" ")
                          .append(BOLD).append("[").append(padRight(s.agent.name(), 20)).append("]").append(RESET).append(" ")
                          .append(padRight(s.agent.role(), 34)).append(" ")
                          .append(DIM).append(String.format(Locale.US, "(%.2fs)", elapsed)).append(RESET).append("\n");
                    }
                }
                writeUtf8(sb.toString());
                frame++;
            }
        }) : null;

        SwarmEngine.SwarmListener listener = new SwarmEngine.SwarmListener() {
            @Override
            public void onAgentStarted(SwarmAgent agent) {
                AgentUIState s = stateMap.get(agent.name());
                if (s != null) {
                    s.startMs.set(System.currentTimeMillis());
                    s.status.set(1);
                }
            }

            @Override
            public void onAgentCompleted(SwarmAgent agent, SwarmResult result) {
                AgentUIState s = stateMap.get(agent.name());
                if (s != null) {
                    s.durationMs.set(result.durationMs());
                    s.status.set(result.success() ? 2 : 3);
                }
                if (!isInteractive) {
                    double elapsed = result.durationMs() / 1000.0;
                    if (result.success()) {
                        writelnUtf8("  " + GREEN + "✓" + RESET + " " + BOLD + "[" + padRight(agent.name(), 20) + "]" + RESET + " " + padRight(agent.role(), 34) + " " + GREEN + String.format(Locale.US, "PASSED (%.2fs)", elapsed) + RESET);
                    } else {
                        writelnUtf8("  " + RED + "✗" + RESET + " " + BOLD + "[" + padRight(agent.name(), 20) + "]" + RESET + " " + padRight(agent.role(), 34) + " " + RED + "HALTED" + RESET);
                    }
                }
            }

            @Override
            public void onAgentFailed(SwarmAgent agent, Throwable error) {
                AgentUIState s = stateMap.get(agent.name());
                if (s != null) {
                    s.durationMs.set(System.currentTimeMillis() - s.startMs.get());
                    s.status.set(3);
                }
                if (!isInteractive) {
                    writelnUtf8("  " + RED + "✗" + RESET + " " + BOLD + "[" + padRight(agent.name(), 20) + "]" + RESET + " " + padRight(agent.role(), 34) + " " + RED + "HALTED" + RESET);
                }
            }
        };

        SwarmEngine.SwarmReport report;
        try {
            report = engine.runSwarm(task, agents, listener);
        } finally {
            if (animator != null) {
                animating.set(false);
                animator.interrupt();
                try { animator.join(200); } catch (InterruptedException ignored) {}
            }
        }

        if (isInteractive) {
            // Cleanly settle all agent lines to final state using authoritative report results
            StringBuilder finalSb = new StringBuilder();
            finalSb.append("\033[").append(agentCount).append("A");
            for (int i = 0; i < agentCount; i++) {
                AgentUIState s = uiStates.get(i);
                SwarmResult res = report.results().stream()
                    .filter(r -> r.agentName().equals(s.agent.name()))
                    .findFirst().orElse(null);
                boolean pass = res != null && res.success();
                double elapsed = (res != null ? res.durationMs() : s.durationMs.get()) / 1000.0;
                finalSb.append("\r\033[K");
                if (pass) {
                    finalSb.append("  ").append(GREEN).append("✓").append(RESET).append(" ")
                           .append(BOLD).append("[").append(padRight(s.agent.name(), 20)).append("]").append(RESET).append(" ")
                           .append(padRight(s.agent.role(), 34)).append(" ")
                           .append(GREEN).append(String.format(Locale.US, "PASSED (%.2fs)", elapsed)).append(RESET).append("\n");
                } else {
                    finalSb.append("  ").append(RED).append("✗").append(RESET).append(" ")
                           .append(BOLD).append("[").append(padRight(s.agent.name(), 20)).append("]").append(RESET).append(" ")
                           .append(padRight(s.agent.role(), 34)).append(" ")
                           .append(RED).append("HALTED").append(RESET).append("\n");
                }
            }
            writeUtf8(finalSb.toString());
        }
        writelnUtf8("");

        // Display Executive Swarm Synthesis Box
        List<String> synthesisBox = buildExecutiveSynthesisBox(report, 76);
        for (String line : synthesisBox) {
            writelnUtf8(line);
        }
        writelnUtf8("");

        // Display Swarm Telemetry Box
        List<String> telemetryBox = buildSwarmTelemetryBox(report, 76);
        for (String line : telemetryBox) {
            writelnUtf8(line);
        }
        writelnUtf8("");
    }

    static List<String> buildExecutiveSynthesisBox(SwarmEngine.SwarmReport report, int boxWidth) {
        int inner = boxWidth - 4;
        List<String> res = new ArrayList<>();

        String title = "EXECUTIVE SWARM SYNTHESIS";
        int dashCount = boxWidth - 5 - title.length();
        res.add(CYAN + "┌─ " + BOLD + title + RESET + CYAN + " " + "─".repeat(Math.max(0, dashCount)) + "┐" + RESET);

        String targetDesc = report.task().description().replace("\r", " ").replace("\n", " ").trim();
        String targetLine = "Target:    '" + (targetDesc.length() > inner - 14 ? targetDesc.substring(0, inner - 17) + "..." : targetDesc) + "'";
        res.add(CYAN + "│ " + WHITE + padRight(targetLine, inner) + CYAN + " │" + RESET);

        long passCount = report.results().stream().filter(SwarmResult::success).count();
        String consensus;
        if (report.governorTripped()) {
            consensus = "Consensus: SWARM HALT - Governor Hard Budget Tripwire Tripped";
        } else {
            consensus = String.format(Locale.US, "Consensus: %d/%d Specialist Reviews Passed (Full System Clearance)",
                passCount, report.results().size());
        }
        res.add(CYAN + "│ " + (report.governorTripped() ? RED : GREEN) + padRight(consensus, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "├" + "─".repeat(boxWidth - 2) + "┤" + RESET);

        for (SwarmResult r : report.results()) {
            String marker = r.success() ? "[✓ PASS]" : "[✗ FAIL]";
            String header = marker + " " + r.agentName() + " (" + r.role() + ")";
            if (header.length() > inner) header = header.substring(0, inner);
            res.add(CYAN + "│ " + (r.success() ? GREEN : RED) + padRight(header, inner) + CYAN + " │" + RESET);

            for (String findingLine : r.findings().lines().toList()) {
                String clean = findingLine.trim();
                if (clean.isEmpty()) continue;
                for (String chunk : wrapText(clean, inner - 2)) {
                    res.add(CYAN + "│   " + GRAY + padRight(chunk, inner - 2) + CYAN + " │" + RESET);
                }
            }
            res.add(CYAN + "│ " + padRight("", inner) + CYAN + " │" + RESET);
        }

        if (res.size() > 5 && res.get(res.size() - 1).contains(CYAN + "│ " + padRight("", inner) + CYAN + " │")) {
            res.remove(res.size() - 1);
        }

        res.add(CYAN + "└" + "─".repeat(boxWidth - 2) + "┘" + RESET);
        return res;
    }

    static List<String> buildSwarmTelemetryBox(SwarmEngine.SwarmReport report, int boxWidth) {
        int inner = boxWidth - 4;
        List<String> res = new ArrayList<>();

        String title = "SWARM TELEMETRY";
        int dashCount = boxWidth - 5 - title.length();
        res.add(CYAN + "┌─ " + BOLD + title + RESET + CYAN + " " + "─".repeat(Math.max(0, dashCount)) + "┐" + RESET);

        String line1 = String.format(Locale.US, "Concurrent Agents: %d personas │ Virtual Threads: %d Loom threads",
            report.results().size(), report.virtualThreadsSpawned());
        String line2 = String.format(Locale.US, "Swarm Join Time:   %dms (SLA: 3s) │ Tokens: %d total",
            report.joinDurationMs(), report.totalTokens());

        String govStatus = report.governorTripped() ? "TRIPPED (Budget Cap Exceeded)" : "Nominal (Budget OK)";
        String costFormatted = String.format(Locale.US, "$%.5f", report.totalCostUsd());
        String line3 = String.format(Locale.US, "Governor Status:   %s │ Cost: %s",
            govStatus, costFormatted);

        res.add(CYAN + "│ " + WHITE + padRight(line1, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "│ " + WHITE + padRight(line2, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "│ " + (report.governorTripped() ? RED : GRAY) + padRight(line3, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "└" + "─".repeat(boxWidth - 2) + "┘" + RESET);

        return res;
    }

    static List<String> wrapText(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        if (text.length() <= maxLen) {
            result.add(text);
            return result;
        }
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() + w.length() + 1 > maxLen) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
                if (w.length() > maxLen) {
                    result.add(w.substring(0, Math.max(0, maxLen - 3)) + "...");
                } else {
                    current.append(w);
                }
            } else {
                if (current.length() > 0) current.append(" ");
                current.append(w);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    static List<String> buildExecutiveSynthesisBoxForTesting(SwarmEngine.SwarmReport report, int boxWidth) {
        return buildExecutiveSynthesisBox(report, boxWidth);
    }

    static List<String> buildSwarmTelemetryBoxForTesting(SwarmEngine.SwarmReport report, int boxWidth) {
        return buildSwarmTelemetryBox(report, boxWidth);
    }

    static SwarmOptions parseSwarmCommandForTesting(String input) {
        return parseSwarmCommand(input);
    }

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
}
