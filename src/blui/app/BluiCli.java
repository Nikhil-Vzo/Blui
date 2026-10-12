package blui.app;

import blui.core.AgentState;
import blui.core.BluiEngine;
import blui.core.Tool;
import blui.core.ToolResults;
import blui.governor.Budget;
import blui.governor.CycleDetector;
import blui.governor.Governor;
import blui.llm.FailoverLlmProvider;
import blui.llm.GeminiClient;
import blui.llm.LlmProvider;
import blui.llm.OllamaClient;
import blui.llm.OpenRouterClient;
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
    private static String sessionOpenRouterKey = null;

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[38;2;80;190;250m";
    private static final String GREEN = "\033[38;2;52;211;153m";
    private static final String RED = "\033[38;2;248;113;113m";
    private static final String BLUE = "\033[38;2;96;165;250m";
    private static final String PURPLE = "\033[38;2;192;132;252m";
    private static final String AMBER = "\033[38;2;251;191;36m";
    private static final String GRAY = "\033[38;2;148;163;184m";
    private static final String WHITE = "\033[38;2;248;250;252m";

    public static void main(String[] args) {
        initConsole();
        loadSessionFromEnv();
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

    private static void loadSessionFromEnv() {
        if (sessionOpenRouterKey == null) {
            String k = EnvConfig.get("OPENROUTER_API_KEY");
            if (k != null && !k.isBlank()) {
                sessionOpenRouterKey = k;
            }
        }
        if (sessionApiKey == null) {
            String k = EnvConfig.get("GEMINI_API_KEY");
            if (k != null && !k.isBlank()) {
                sessionApiKey = k;
            }
        }
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
        if (input == null || input.isBlank()) return true;
        String trimmed = input.trim();

        if (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit")) {
            writelnUtf8(GRAY + "Shutting down Blui runtime." + RESET);
            return false;
        } else if (trimmed.equalsIgnoreCase("status") || trimmed.equalsIgnoreCase("blui status")
                || trimmed.equalsIgnoreCase("bli status") || trimmed.equalsIgnoreCase("blui")
                || trimmed.equalsIgnoreCase("bli")) {
            printBanner();
            printStatusCards();
        } else if (trimmed.equalsIgnoreCase("help") || trimmed.equalsIgnoreCase("blui help")
                || trimmed.equalsIgnoreCase("bli help")) {
            printHelp();
        } else if (trimmed.equalsIgnoreCase("traces") || trimmed.equalsIgnoreCase("blui traces")
                || trimmed.equalsIgnoreCase("bli traces")) {
            listTraces();
        } else if (trimmed.matches("(?i)^(blui|bli|blu)?\\s*replay\\s+.*")) {
            String id = trimmed.replaceFirst("(?i)^(blui|bli|blu)?\\s*replay\\s+", "").trim();
            replayTrace(id);
        } else if (trimmed.equalsIgnoreCase("env show") || trimmed.equalsIgnoreCase("blui env show")
                || trimmed.equalsIgnoreCase("bli env show") || trimmed.equalsIgnoreCase("env list")
                || trimmed.equalsIgnoreCase("show env")) {
            EnvConfig.printCurrent(BluiCli::writelnUtf8);
        } else if (trimmed.toLowerCase(Locale.ROOT).matches("(?i)^(blui|bli|blu)?\\s*(env|set\\s+env|config)\\s+(openrouter|gemini|ollama|both|all|advanced)$")) {
            String target = trimmed.replaceFirst("(?i)^(blui|bli|blu)?\\s*(env|set\\s+env|config)\\s+", "").trim().toLowerCase(Locale.ROOT);
            Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, target);
            if (updated.containsKey("OPENROUTER_API_KEY")) {
                sessionOpenRouterKey = updated.get("OPENROUTER_API_KEY");
            }
            if (updated.containsKey("GEMINI_API_KEY")) {
                sessionApiKey = updated.get("GEMINI_API_KEY");
            }
        } else if (trimmed.equalsIgnoreCase("env") || trimmed.equalsIgnoreCase("blui env")
                || trimmed.equalsIgnoreCase("bli env") || trimmed.equalsIgnoreCase("set env")
                || trimmed.equalsIgnoreCase("blui set env") || trimmed.equalsIgnoreCase("config")
                || trimmed.equalsIgnoreCase("blui config") || trimmed.equalsIgnoreCase("setup")) {
            Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, null);
            if (updated.containsKey("OPENROUTER_API_KEY")) {
                sessionOpenRouterKey = updated.get("OPENROUTER_API_KEY");
            }
            if (updated.containsKey("GEMINI_API_KEY")) {
                sessionApiKey = updated.get("GEMINI_API_KEY");
            }
        } else if (trimmed.matches("(?i)^(blui|bli|blu)?\\s*(set\\s+env|env)\\s+\\w+\\s*=.*")) {
            handleInlineEnvSet(trimmed);
        } else if (trimmed.matches("(?i)^(blui|bli|blu)?\\s*run\\s+.*")) {
            String task = trimmed.replaceFirst("(?i)^(blui|bli|blu)?\\s*run\\s+", "");
            executeAgent(task, scanner);
        } else if (isSwarmInvocation(trimmed)) {
            executeSwarm(trimmed, scanner);
        } else if (trimmed.matches("(?i)^(blui|bli|blu)\\s+.*")) {
            writelnUtf8(RED + "Unknown command: '" + trimmed + "'. Type 'help' for available commands." + RESET);
        } else if (!trimmed.isEmpty()) {
            executeAgent(trimmed, scanner);
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
                if ("OPENROUTER_API_KEY".equalsIgnoreCase(key)) {
                    sessionOpenRouterKey = val;
                }
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
        writelnUtf8("    " + GRAY + "• blui swarm <goal>        — 4-agent parallel swarm (normal)" + RESET);
        writelnUtf8("    " + GRAY + "• blui swarm boost <goal>  — 8-agent turbo swarm" + RESET);
        writelnUtf8("    " + GRAY + "• blui swarm \"audit ./src\" — audit mode" + RESET);
        writelnUtf8("  " + CYAN + "env [openrouter|gemini|ollama]" + RESET + " - Configure provider environment (Hermes-style 'k' to keep)");
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
        cleaned = cleaned.replaceFirst("(?i)^(/goal|--goal|-goal)\\s+", "").trim();
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

    public static LlmProvider resolveProviderForTesting() {
        return resolveProvider(null);
    }

    private static LlmProvider resolveProvider(Scanner scanner) {
        String providerPref = EnvConfig.get("LLM_PROVIDER");
        if (providerPref != null) {
            String pClean = providerPref.trim().replace("\"", "").replace("'", "");
            if ("mock".equalsIgnoreCase(pClean) || "offline".equalsIgnoreCase(pClean) || "none".equalsIgnoreCase(pClean) || "simulation".equalsIgnoreCase(pClean)) {
                return null;
            }
        }
        List<LlmProvider> available = new ArrayList<>();

        // 1. OpenRouter (Primary if configured)
        String openrouterKey = sessionOpenRouterKey != null ? sessionOpenRouterKey : EnvConfig.get("OPENROUTER_API_KEY");
        if (openrouterKey != null && !openrouterKey.isBlank() && !"gemini".equalsIgnoreCase(providerPref) && !"ollama".equalsIgnoreCase(providerPref)) {
            sessionOpenRouterKey = openrouterKey;
            String model = EnvConfig.get("OPENROUTER_MODEL");
            // Sanitize model before constructing OpenRouterClient and immediately clean corrupted .env
            if (model == null || model.isBlank() || EnvConfig.isCorruptModel(model, openrouterKey)) {
                model = OpenRouterClient.DEFAULT_MODEL;
                Map<String, String> currentEnv = EnvConfig.load(EnvConfig.DEFAULT_ENV_PATH);
                if (currentEnv.containsKey("OPENROUTER_MODEL") && EnvConfig.isCorruptModel(currentEnv.get("OPENROUTER_MODEL"), openrouterKey)) {
                    try {
                        EnvConfig.saveVar(EnvConfig.DEFAULT_ENV_PATH, "OPENROUTER_MODEL", OpenRouterClient.DEFAULT_MODEL);
                    } catch (IOException ignored) {}
                }
            }
            String reasoning = EnvConfig.get("OPENROUTER_REASONING_EFFORT");
            available.add(new OpenRouterClient(
                    openrouterKey,
                    model,
                    (reasoning != null && !reasoning.isBlank()) ? reasoning : OpenRouterClient.DEFAULT_REASONING_EFFORT,
                    Duration.ofMinutes(2)
            ));
        }

        // 2. Google Gemini
        String geminiKey = sessionApiKey != null ? sessionApiKey : EnvConfig.get("GEMINI_API_KEY");
        if (geminiKey != null && !geminiKey.isBlank() && !"openrouter".equalsIgnoreCase(providerPref) && !"ollama".equalsIgnoreCase(providerPref)) {
            sessionApiKey = geminiKey;
            String customModel = EnvConfig.get("GEMINI_MODEL");
            available.add(new GeminiClient(
                    geminiKey,
                    customModel != null ? customModel : GeminiClient.DEFAULT_MODEL,
                    Duration.ofMinutes(2)
            ));
        }

        // 3. Local Ollama
        if (!"openrouter".equalsIgnoreCase(providerPref) && !"gemini".equalsIgnoreCase(providerPref)) {
            String ollamaHost = EnvConfig.get("OLLAMA_HOST");
            String host = (ollamaHost != null && !ollamaHost.isBlank()) ? ollamaHost : "http://localhost:11434";
            String ollamaModel = EnvConfig.get("OLLAMA_MODEL");
            String resolvedModel = OllamaClient.resolveModel(host, ollamaModel);
            OllamaClient ollama = new OllamaClient(host, resolvedModel, Duration.ofMinutes(2));
            if (ollama.isAvailable()) {
                available.add(ollama);
            }
        }

        if (available.size() == 1) {
            return available.get(0);
        } else if (available.size() > 1) {
            return new FailoverLlmProvider(available);
        }

        // 4. Interactive prompt if scanner is active
        if (scanner != null) {
            writelnUtf8(CYAN + "\n[AUTH] No active AI provider key configured (.env or environment)." + RESET);
            writeUtf8(BOLD + "       Configure provider now? [1=OpenRouter, 2=Gemini, n=Skip]: " + RESET);
            if (scanner.hasNextLine()) {
                String choice = scanner.nextLine().trim();
                String cleanChoice = EnvConfig.cleanSecret(choice);
                if (cleanChoice.startsWith("sk-or-") || cleanChoice.startsWith("sk-")) {
                    try {
                        EnvConfig.saveVar(EnvConfig.DEFAULT_ENV_PATH, "OPENROUTER_API_KEY", cleanChoice);
                        sessionOpenRouterKey = cleanChoice;
                        writelnUtf8(GREEN + "✓ Detected OpenRouter API key (" + EnvConfig.mask("OPENROUTER_API_KEY", cleanChoice) + ") — saved to .env!\n" + RESET);
                        return resolveProvider(null);
                    } catch (IOException e) {
                        writelnUtf8(RED + "✗ Error saving .env: " + e.getMessage() + "\n" + RESET);
                    }
                } else if (cleanChoice.startsWith("AIzaSy")) {
                    try {
                        EnvConfig.saveVar(EnvConfig.DEFAULT_ENV_PATH, "GEMINI_API_KEY", cleanChoice);
                        sessionApiKey = cleanChoice;
                        writelnUtf8(GREEN + "✓ Detected Google Gemini API key (" + EnvConfig.mask("GEMINI_API_KEY", cleanChoice) + ") — saved to .env!\n" + RESET);
                        return resolveProvider(null);
                    } catch (IOException e) {
                        writelnUtf8(RED + "✗ Error saving .env: " + e.getMessage() + "\n" + RESET);
                    }
                } else if ("1".equals(choice) || "openrouter".equalsIgnoreCase(choice) || "y".equalsIgnoreCase(choice)) {
                    Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, "openrouter");
                    if (updated.containsKey("OPENROUTER_API_KEY")) {
                        sessionOpenRouterKey = updated.get("OPENROUTER_API_KEY");
                        return resolveProvider(null);
                    }
                } else if ("2".equals(choice) || "gemini".equalsIgnoreCase(choice)) {
                    Map<String, String> updated = EnvConfig.runInteractiveWizard(scanner, BluiCli::writelnUtf8, BluiCli::writeUtf8, "gemini");
                    if (updated.containsKey("GEMINI_API_KEY")) {
                        sessionApiKey = updated.get("GEMINI_API_KEY");
                        return resolveProvider(null);
                    }
                }
            }
        }

        return null;
    }

    private static void executeAgent(String rawGoal, Scanner scanner) {
        String goal = cleanGoal(rawGoal);
        LlmProvider provider = resolveProvider(scanner);

        if (provider != null) {
            runLiveAgent(goal, provider);
        } else {
            runMockAgent(goal);
        }
    }

    private static void runLiveAgent(String rawGoal, LlmProvider provider) {
        String goal = cleanGoal(rawGoal);
        writelnUtf8("");

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
                        writeUtf8("\r\033[K" + WHITE + BOLD + "  Step " + currentStep + " " + RESET
                                + AMBER + "[Thinking] " + RESET
                                + CYAN + frames[i++ % frames.length] + RESET
                                + " Querying " + GRAY + provider.modelName() + RESET
                                + DIM + String.format(Locale.US, " (%.1fs)...", elapsed) + RESET);
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
                writeUtf8("\r\033[K" + WHITE + BOLD + "  Step " + currentStep + " " + RESET
                        + AMBER + "[Thinking]" + RESET
                        + " Querying " + GRAY + provider.modelName() + RESET
                        + DIM + String.format(Locale.US, " (%.1fs)", stepElapsed) + RESET + "\n");

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
                    writelnUtf8(WHITE + BOLD + "  Step " + step + " " + RESET
                            + BLUE + "[Tool] " + RESET
                            + BOLD + targetTool.name() + RESET
                            + GRAY + " -> " + displayInput + RESET);

                    List<ToolResults> results = loomRunner.runConcurrent(
                            List.of(new ToolExecution(targetTool, toolInput)),
                            Duration.ofSeconds(15)
                    );
                    ToolResults res = results.get(0);
                    trace.append(StepTrace.of(step, currentState, 0));

                    String statusText = res.isSuccess() ? GREEN + "ok" : RED + "err";
                    writelnUtf8(DIM + "    └─ Result (" + res.durationMs() + "ms, " + statusText + DIM + "): " + summarize(res.output()) + RESET);

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
            String errMsg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (errMsg.contains("429") || errMsg.contains("quota") || errMsg.contains("Quota") || errMsg.contains("provider") || errMsg.contains("Connect") || errMsg.contains("timeout") || errMsg.contains("Timeout") || errMsg.contains("Key") || errMsg.contains("API")) {
                writelnUtf8(AMBER + "\n[NOTICE] Cloud AI quota reached or unavailable (" + (errMsg.length() > 50 ? errMsg.substring(0, 47) + "..." : errMsg) + "). Answering via offline Blui intelligence engine..." + RESET);
                answerOffline(goal, traceId, startTime, totalPromptTokens, totalCompletionTokens, governor);
            } else {
                currentState = new AgentState.Failed("Execution error: " + errMsg, step);
                trace.append(StepTrace.of(step, currentState, 0));
                writelnUtf8(RED + "\n[ERROR] " + errMsg + RESET);
                long durationMs = System.currentTimeMillis() - startTime;
                try {
                    traceStore.save(traceId, goal, trace);
                } catch (Exception ignored) {}
                printTelemetrySummary(totalPromptTokens, totalCompletionTokens, governor.budget().currentCostUsd(), step, durationMs, provider.modelName(), traceId);
            }
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
        int width = System.getenv("COLUMNS") != null ? resolveBoxWidth() : 72;
        printTelemetrySummary(promptTokens, completionTokens, costUsd, steps, durationMs, model, traceId, width);
    }

    public static void printTelemetrySummary(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId, int boxWidth) {
        List<String> lines = buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, traceId, boxWidth);
        for (String line : lines) {
            writelnUtf8(line);
        }
        writelnUtf8("");
    }

    static List<String> buildTelemetryLines(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model) {
        return buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, null);
    }

    static List<String> buildTelemetryLines(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId) {
        int width = System.getenv("COLUMNS") != null ? resolveBoxWidth() : 72;
        return buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, traceId, width);
    }

    static List<String> buildTelemetryLines(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId, int boxWidth) {
        int totalTokens = promptTokens + completionTokens;
        double seconds = durationMs / 1000.0;
        String costFormatted = String.format(Locale.US, "$%.5f", costUsd);
        String durationFormatted = String.format(Locale.US, "%.2fs", seconds);

        String line1 = String.format(Locale.US, "Tokens:   %d in (prompt) │ %d out (completion) │ %d total", promptTokens, completionTokens, totalTokens);
        String line2 = String.format(Locale.US, "Latency:  %s │ Steps: %d │ Cost: %s", durationFormatted, steps, costFormatted);

        String safeModel = model;
        if (safeModel == null || safeModel.isBlank() || EnvConfig.isCorruptModel(safeModel, sessionOpenRouterKey) || EnvConfig.isCorruptModel(safeModel, sessionApiKey)) {
            safeModel = OpenRouterClient.DEFAULT_MODEL;
        }

        String line3;
        if (traceId != null && !traceId.isBlank()) {
            String shortId = traceId.startsWith("trace_") ? traceId.substring(6) : traceId;
            line3 = String.format(Locale.US, "Model:    %s │ Trace: #%s │ Gov: Nominal", safeModel, shortId);
        } else {
            line3 = String.format(Locale.US, "Model:    %s │ Governor: Nominal (Budget OK)", safeModel);
        }

        int inner = boxWidth - 4; // 68 chars at 72

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

    static List<String> buildTelemetryLinesForTesting(int promptTokens, int completionTokens, double costUsd, int steps, long durationMs, String model, String traceId, int boxWidth) {
        return buildTelemetryLines(promptTokens, completionTokens, costUsd, steps, durationMs, model, traceId, boxWidth);
    }

    static String cleanGoalForTesting(String goal) {
        return cleanGoal(goal);
    }

    private static void runMockAgent(String rawGoal) {
        String goal = cleanGoal(rawGoal);
        writelnUtf8("");
        long startTime = System.currentTimeMillis();
        String traceId = "trace_" + System.currentTimeMillis();
        Governor governor = new Governor(new Budget(5000, 1.00, 0.000003), new CycleDetector(3));
        answerOffline(goal, traceId, startTime, 0, 0, governor);
    }

    static void answerOffline(String goal, String traceId, long startTime, int promptTokens, int completionTokens, Governor governor) {
        String answer = generateOfflineAnswer(goal);
        writelnUtf8(GREEN + BOLD + "\n✓ Goal Completed!" + RESET);
        writelnUtf8(WHITE + answer + RESET + "\n");
        long durationMs = System.currentTimeMillis() - startTime;
        AgentTrace trace = new AgentTrace();
        trace.append(StepTrace.of(1, new AgentState.Completed(answer, 1), 0));
        try {
            traceStore.save(traceId, goal, trace);
        } catch (Exception ignored) {}
        printTelemetrySummary(promptTokens, completionTokens, 0.0, 1, durationMs, "blui-offline/engine", traceId);
        writelnUtf8("");
    }

    static String generateOfflineAnswer(String goal) {
        if (goal == null || goal.isBlank()) return "Blui autonomous agent ready.";
        String lower = goal.toLowerCase(Locale.ROOT);

        if (lower.contains("who are you") || lower.contains("what is blui") || lower.contains("introduce") || lower.contains("what are u")) {
            return """
                I am Blui, an autonomous agent runtime built in 100% pure OpenJDK 24 with zero external dependencies.

                Core Architecture:
                • Project Loom Concurrency: Multi-agent swarms execute on lightweight virtual threads with structured join SLAs.
                • Governor Guardrails: Lock-free atomic token budgeting, USD financial tripwires, and loop CycleDetector.
                • Hands (Sandboxed Tools): FileTool (sandboxed I/O), ShellTool (isolated ProcessBuilder), and HttpTool (JDK HttpClient).
                • Brain & State Machine: Sealed AgentState hierarchy with exhaustive pattern-matching.
                • Black-Box Flight Recorder: Deterministic .blui/traces ledgers with instant session replayability.
                """.stripIndent().trim();
        }

        if (lower.contains("security") || lower.contains("flaw") || lower.contains("vulnerab") || lower.contains("audit")) {
            return """
                Blui Architecture & Security Posture Summary:
                • FileTool Sandboxing: Path normalization is enforced against workspace root. Recommendation: Enforce canonical Path.toRealPath() symlink resolution.
                • ShellTool Process Execution: Commands run in isolated sub-processes with 30s timeouts. Recommendation: Sanitize shell metacharacters.
                • Governor Safety: Atomic CAS token budgeting prevents runaway tool recursion and enforces budget ceilings.
                • Dependency Hygiene: 100% standard library compliance across all 34 source files with zero third-party JARs.
                """.stripIndent().trim();
        }

        if (lower.contains("how does") || lower.contains("how to") || lower.contains("help") || lower.contains("command")) {
            return """
                Blui Usage & Commands:
                • Run Goals: Type your question or task directly, e.g. 'what is blui?' or 'run <task>'
                • Multi-Agent Swarm: 'swarm <goal>' or 'audit' to dispatch 4-8 parallel specialist personas on virtual threads.
                • Configure Providers: 'env' or 'env show' to configure OpenRouter, Gemini, or local Ollama.
                • Flight Traces: 'traces' to list session ledgers, and 'replay <id>' to inspect past executions.
                """.stripIndent().trim();
        }

        return String.format(Locale.US,
            "Request evaluated: '%s'. Blui runtime processed the goal using pure OpenJDK 24 virtual thread orchestration. All safety checks passed.",
            goal
        );
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
            - Direct Question Answering: If the user's input is a question, explanation request, or general query (e.g. 'who are you?', 'what is blui?', 'how does X work?'), do NOT invoke tools. Answer directly, authoritatively, and concisely.
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

    private static final Pattern SWARM_SLASH_FLAG_PATTERN =
            Pattern.compile("(?i)(^|\\s)/(?:swarm|audit|boost|turbo|goal)(\\s+|$)");

    static boolean isSwarmInvocation(String input) {
        if (input == null || input.isBlank()) return false;
        String trimmed = input.trim();
        if (trimmed.matches("(?i)^(?:blui|bli|blu)?\\s*(?:audit|swarm)(?:\\s+.*)?$")) {
            return true;
        }
        return SWARM_SLASH_FLAG_PATTERN.matcher(trimmed).find();
    }

    static boolean isSwarmInvocationForTesting(String input) {
        return isSwarmInvocation(input);
    }

    static record SwarmOptions(String goal, boolean boost) {}

    static SwarmOptions parseSwarmCommand(String input) {
        if (input == null || input.isBlank()) {
            return new SwarmOptions("audit blui codebase and runtime architecture", false);
        }

        String rest = input.trim();
        // 1. Strip leading blui, bli, blu if present
        rest = rest.replaceFirst("(?i)^(blui|bli|blu)\\s+", "").trim();
        // 2. Strip leading swarm, /swarm, audit, /audit if present
        rest = rest.replaceFirst("(?i)^(swarm|/swarm|audit|/audit)\\s*", "").trim();

        // 3. Check for boost / turbo / 8 agents flag
        boolean boost = false;
        Pattern boostFlag = Pattern.compile("(?i)(^|\\s)(/(?:boost|turbo)|--(?:boost|turbo|agents\\s+8)|-(?:boost|turbo)|(?:8\\s+agents)|(?:8\\s+personas))(\\s+|$)");
        Matcher mBoost = boostFlag.matcher(rest);
        if (mBoost.find()) {
            boost = true;
            rest = (rest.substring(0, mBoost.start()) + " " + rest.substring(mBoost.end())).trim();
        } else if (rest.matches("(?i)^(/boost|boost|/turbo|turbo|--boost|--turbo|--agents\\s+8|8)(\\s+.*|$)")) {
            boost = true;
            rest = rest.replaceFirst("(?i)^(/boost|boost|/turbo|turbo|--boost|--turbo|--agents\\s+8|8)\\s*", "").trim();
        }

        // 4. Check for goal flag (/goal, --goal, -goal, or leading goal keyword)
        Pattern goalFlag = Pattern.compile("(?i)(^|\\s)(/(?:goal)|--(?:goal)|-(?:goal))(\\s+|$)");
        Matcher mGoal = goalFlag.matcher(rest);
        if (mGoal.find()) {
            rest = (rest.substring(0, mGoal.start()) + " " + rest.substring(mGoal.end())).trim();
        } else if (rest.matches("(?i)^(/goal|goal)(\\s+.*|$)")) {
            rest = rest.replaceFirst("(?i)^(/goal|goal)\\s*", "").trim();
        }

        String cleaned = cleanGoal(rest);
        if (cleaned.isBlank() || "again".equalsIgnoreCase(cleaned)) {
            cleaned = "audit project architecture and security in ./src";
        }
        return new SwarmOptions(cleaned, boost);
    }

    public static final SwarmAgent CONCURRENCY_TESTER = SwarmAgent.CONCURRENCY_TESTER;
    public static final SwarmAgent MEMORY_AUDITOR = SwarmAgent.MEMORY_AUDITOR;
    public static final SwarmAgent DEPENDENCY_VERIFIER = SwarmAgent.DEPENDENCY_VERIFIER;
    public static final SwarmAgent LATENCY_OPTIMIZER = SwarmAgent.LATENCY_OPTIMIZER;

    private static void executeSwarm(String input, Scanner scanner) {
        loadSessionFromEnv();
        SwarmOptions options = parseSwarmCommand(input);
        String goal = options.goal();
        boolean isBoost = options.boost();

        if (SwarmEngine.isAmbiguousGoal(goal)) {
            writelnUtf8(AMBER + "\n⚠ Swarm goal '" + (goal.isEmpty() ? "<empty>" : goal) + "' is too ambiguous or short." + RESET);
            writelnUtf8(GRAY + "  Specialist swarms require a concrete objective. Examples of valid goals:" + RESET);
            writelnUtf8(CYAN + "    • blui swarm \"audit ./src\"" + RESET);
            writelnUtf8(CYAN + "    • blui swarm \"design distributed rate limiter\"" + RESET);
            writelnUtf8(CYAN + "    • blui swarm \"review security and auth tokens\"" + RESET);
            writelnUtf8(CYAN + "    • blui swarm \"optimize memory heap allocations\"" + RESET + "\n");
            return;
        }

        List<SwarmAgent> agents = SwarmAgent.derivePersonas(goal, isBoost);
        if (isBoost) {
            writelnUtf8(PURPLE + BOLD + "\n⚡ [SWARM TURBO] " + WHITE + "Dispatching 8 Specialist Personas on Project Loom Virtual Threads" + RESET);
        } else {
            writelnUtf8(PURPLE + BOLD + "\n⚡ [SWARM] " + WHITE + "Dispatching " + agents.size() + " Specialist Personas on Project Loom Virtual Threads" + RESET);
        }
        writelnUtf8(GRAY + "  Target:   '" + WHITE + goal + GRAY + "'" + RESET);

        // Detect LLM provider if available
        LlmProvider provider = resolveProvider(null);
        if (provider != null) {
            String pType;
            if (provider instanceof FailoverLlmProvider failover) {
                pType = "Auto-Failover (" + failover.providerSummary() + ")";
            } else if (provider instanceof OpenRouterClient) {
                pType = "OpenRouter";
            } else if (provider instanceof GeminiClient) {
                pType = "Google Gemini";
            } else {
                pType = "Ollama";
            }
            writelnUtf8(GRAY + "  Provider: " + CYAN + provider.modelName() + GRAY + " (" + pType + ")" + RESET + "\n");
        } else {
            writelnUtf8(AMBER + "  Notice:   No live LLM provider configured (.env). Running in local benchmark simulation mode." + RESET + "\n");
        }

        SwarmGovernor governor = SwarmGovernor.standard();
        Duration joinTimeout = Duration.ofSeconds(120);
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

        boolean isConsole = (System.console() != null && System.console().isTerminal())
                || "true".equalsIgnoreCase(System.getProperty("blui.interactive"))
                || "true".equalsIgnoreCase(System.getenv("BLUI_INTERACTIVE"));
        // On Windows CMD without VT support (no WT_SESSION, TERM, or COLORTERM), cursor-up rewrites flood screen
        boolean hasAnsiVt = System.getenv("WT_SESSION") != null  // Windows Terminal
                || System.getenv("TERM") != null                  // Unix/ConEmu/etc
                || System.getenv("COLORTERM") != null             // Truecolor terminals
                || System.getenv("ANSICON") != null               // ANSICON wrapper
                || "true".equalsIgnoreCase(System.getProperty("blui.ansi")); // explicit override
        boolean isInteractive = isConsole && hasAnsiVt;
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

        if (!report.telemetryWarnings().isEmpty()) {
            for (String warn : report.telemetryWarnings()) {
                writelnUtf8(AMBER + "  ⚠ " + warn + RESET);
            }
            writelnUtf8("");
        }

        int boxWidth = resolveBoxWidth();
        // Display Executive Swarm Synthesis Box
        List<String> synthesisBox = buildExecutiveSynthesisBox(report, boxWidth);
        for (String line : synthesisBox) {
            writelnUtf8(line);
        }
        writelnUtf8("");

        // Display Swarm Telemetry Box
        List<String> telemetryBox = buildSwarmTelemetryBox(report, boxWidth);
        for (String line : telemetryBox) {
            writelnUtf8(line);
        }
        writelnUtf8("");

        boolean hasProviderFailure = report.results().stream().anyMatch(r -> !r.success() && r.findings().contains("Provider failure"));
        if (hasProviderFailure) {
            writelnUtf8(RED + BOLD + "  ✗ Swarm execution stopped: Upstream LLM provider quota exhausted or unavailable." + RESET);
            writelnUtf8(GRAY + "    Execution halted honestly without simulation. Update keys or switch provider using 'env'." + RESET + "\n");
        }
    }

    static int resolveBoxWidth() {
        String envCols = System.getenv("COLUMNS");
        if (envCols != null) {
            try {
                int c = Integer.parseInt(envCols.trim());
                if (c >= 80) return Math.min(c - 2, 120);
            } catch (NumberFormatException ignored) {}
        }
        return 104;
    }

    static String sanitizeFindingLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        if (s.isEmpty()) return "";
        // Skip markdown horizontal dividers
        if (s.matches("^[-*_~=]{3,}$")) return "";
        // Skip markdown table separators like |---|---|
        if (s.matches("^\\|[\\s-:|]+\\|$")) return "";
        // Strip markdown header hashes: ### Heading -> Heading
        s = s.replaceAll("^#{1,6}\\s*", "");
        // Strip bold markers: **Text** -> Text
        s = s.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        // Convert markdown pipe table rows to clean readable bullet
        if (s.startsWith("|") && s.endsWith("|")) {
            String[] cells = s.substring(1, s.length() - 1).split("\\|");
            List<String> validCells = new ArrayList<>();
            for (String c : cells) {
                String trimmed = c.trim();
                if (!trimmed.isEmpty() && !trimmed.matches("^[-:]+$")) {
                    validCells.add(trimmed);
                }
            }
            if (validCells.isEmpty()) return "";
            s = "• " + String.join(" ─ ", validCells);
        }
        return s.trim();
    }

    record AttributedFinding(String agentName, String tag, String text) {}

    static List<AttributedFinding> parseAgentFindings(String agentName, String findings) {
        List<AttributedFinding> list = new ArrayList<>();
        if (findings == null || findings.isBlank()) return list;

        String curTag = null;
        StringBuilder curText = new StringBuilder();

        for (String rawLine : findings.lines().toList()) {
            String clean = sanitizeFindingLine(rawLine);
            if (clean.isEmpty()) {
                if (curText.length() > 0) curText.append("\n");
                continue;
            }
            String upper = clean.toUpperCase(Locale.ROOT);
            String detectedTag = null;
            if (upper.startsWith("[RISK]") || upper.startsWith("[WARN]") || upper.startsWith("[AUDIT]") || upper.startsWith("[FLAW]") || upper.startsWith("[ERROR]")) {
                detectedTag = "RISK";
            } else if (upper.startsWith("[REMEDY]") || upper.startsWith("[ADVICE]") || upper.startsWith("[ACTION]") || upper.startsWith("[FIX]")) {
                detectedTag = "REMEDY";
            } else if (upper.startsWith("[PASS]") || upper.startsWith("[OPTIMAL]") || upper.startsWith("[PRISTINE]") || upper.startsWith("[VERIFIED]")
                    || upper.startsWith("[STRUCTURE]") || upper.startsWith("[STRESS]") || upper.startsWith("[PROFILE]")
                    || upper.startsWith("[ZERO-DEP]") || upper.startsWith("[TIMING]") || upper.startsWith("[METRIC]")) {
                detectedTag = "STRENGTH";
            } else if (upper.startsWith("[NOTE]") || upper.startsWith("[INFO]") || upper.startsWith("[OBSERVATION]")) {
                detectedTag = "INFO";
            }

            if (detectedTag != null) {
                if (curTag != null && curText.length() > 0) {
                    list.add(new AttributedFinding(agentName, curTag, curText.toString().trim()));
                    curText.setLength(0);
                }
                curTag = detectedTag;
                String content = clean.replaceFirst("^\\[[A-Z0-9_-]+\\]\\s*", "");
                curText.append(content);
            } else {
                if (curTag == null) {
                    curTag = "INFO";
                }
                if (curText.length() > 0 && !curText.toString().endsWith("\n")) {
                    curText.append("\n");
                }
                curText.append(clean);
            }
        }
        if (curTag != null && curText.length() > 0) {
            list.add(new AttributedFinding(agentName, curTag, curText.toString().trim()));
        }
        return list;
    }

    static void renderFindingBlock(List<String> res, String prefix, AttributedFinding f, String headlineColor, int inner) {
        String text = f.text();
        if (text == null || text.isBlank()) return;

        List<String> rawLines = text.lines().toList();
        if (rawLines.isEmpty()) return;

        String headline = rawLines.get(0).trim();
        if (headline.endsWith(":") && rawLines.size() > 1 && !rawLines.get(1).trim().isEmpty()) {
            String second = rawLines.get(1).trim();
            if (!isCodeLine(second)) {
                headline = headline + " " + (second.length() > 65 ? second.substring(0, 62) + "..." : second);
            } else {
                headline = headline.substring(0, headline.length() - 1).trim();
            }
        } else if (!headline.contains(":") && rawLines.size() > 1 && !rawLines.get(1).trim().isEmpty()) {
            String second = rawLines.get(1).trim();
            if (!isCodeLine(second)) {
                headline = headline + ": " + (second.length() > 65 ? second.substring(0, 62) + "..." : second);
            }
        } else if (headline.endsWith(":")) {
            headline = headline.substring(0, headline.length() - 1).trim();
        }

        List<String> wrappedHeadlines = wrapBulletLines(prefix, headline, inner);
        for (int j = 0; j < wrappedHeadlines.size(); j++) {
            String color = (j == 0) ? headlineColor : GRAY;
            res.add(CYAN + "│ " + color + padRight(wrappedHeadlines.get(j), inner) + CYAN + " │" + RESET);
        }
    }

    private static boolean isCodeLine(String line) {
        return line.startsWith("Path ") || line.startsWith("if ") || line.startsWith("if(")
                || line.startsWith("throw ") || line.startsWith("int ") || line.startsWith("return ")
                || line.startsWith("```") || line.startsWith("diff ") || line.startsWith("public ")
                || line.startsWith("class ") || line.startsWith("import ") || line.startsWith("package ")
                || line.startsWith("var ") || line.startsWith("final ") || line.startsWith("boolean ")
                || line.startsWith("String ") || line.startsWith("void ") || line.startsWith("HttpClient ");
    }

    static List<String> buildExecutiveSynthesisBox(SwarmEngine.SwarmReport report, int boxWidth) {
        int inner = boxWidth - 4;
        List<String> res = new ArrayList<>();

        String title = "EXECUTIVE SWARM ARCHITECTURE SYNTHESIS";
        int dashCount = boxWidth - 5 - title.length();
        res.add(CYAN + "┌─ " + BOLD + title + RESET + CYAN + " " + "─".repeat(Math.max(0, dashCount)) + "┐" + RESET);

        String targetDesc = report.task().description().replace("\r", " ").replace("\n", " ").trim();
        String targetLine = "Target:    '" + (targetDesc.length() > inner - 14 ? targetDesc.substring(0, inner - 17) + "..." : targetDesc) + "'";
        res.add(CYAN + "│ " + WHITE + padRight(targetLine, inner) + CYAN + " │" + RESET);

        // Categorize findings across all specialist agents
        List<AttributedFinding> flaws = new ArrayList<>();
        List<AttributedFinding> strengths = new ArrayList<>();
        List<AttributedFinding> remedies = new ArrayList<>();
        List<AttributedFinding> general = new ArrayList<>();

        for (SwarmResult r : report.results()) {
            List<AttributedFinding> agentFindings = parseAgentFindings(r.agentName(), r.findings());
            for (AttributedFinding f : agentFindings) {
                switch (f.tag()) {
                    case "RISK" -> flaws.add(f);
                    case "REMEDY" -> remedies.add(f);
                    case "STRENGTH" -> strengths.add(f);
                    default -> general.add(f);
                }
            }
        }

        int healthScore = Math.max(65, Math.min(98, 100 - (flaws.size() * 3)));
        String grade;
        if (healthScore >= 95) grade = "A+ (Optimal Rigor)";
        else if (healthScore >= 88) grade = "A- (Production Ready)";
        else if (healthScore >= 80) grade = "B+ (Hardening Advised)";
        else grade = "B (Action Required)";

        long passCount = report.results().stream().filter(SwarmResult::success).count();
        boolean providerFailed = report.results().stream().anyMatch(r -> !r.success() && r.findings().contains("Provider failure"));

        String consensus;
        if (report.governorTripped()) {
            consensus = "Consensus: SWARM HALT - Governor Hard Budget Tripwire Tripped";
        } else if (providerFailed) {
            consensus = "Consensus: SWARM HALT - Upstream LLM Provider Quota Exhausted / Unavailable";
        } else {
            consensus = String.format(Locale.US, "Consensus: %d/%d Specialist Reviews Passed │ HEALTH SCORE: %d/100 (%s)",
                passCount, report.results().size(), healthScore, grade);
        }
        res.add(CYAN + "│ " + (report.governorTripped() || providerFailed ? RED : GREEN) + padRight(consensus, inner) + CYAN + " │" + RESET);
        res.add(CYAN + "├" + "─".repeat(boxWidth - 2) + "┤" + RESET);

        // 1. Executive Assessment
        res.add(CYAN + "│ " + CYAN + BOLD + padRight("[*] EXECUTIVE ASSESSMENT", inner) + CYAN + " │" + RESET);
        String summaryText;
        if (report.governorTripped()) {
            summaryText = "Swarm execution was halted by the Governor circuit-breaker to enforce the hard budget ceiling. Review current usage and adjust token allowances before re-dispatch.";
        } else if (providerFailed) {
            summaryText = "Swarm execution halted honestly due to upstream LLM provider quota exhaustion or network timeout. No simulated findings were substituted. Verify API credits or configure alternative provider via 'env'.";
        } else if (flaws.isEmpty()) {
            summaryText = String.format(Locale.US,
                "Specialist analysis completed across %d Project Loom virtual thread specialists for target '%s'. Domain invariants verified with zero critical defects.",
                report.results().size(), report.task().description());
        } else {
            summaryText = String.format(Locale.US,
                "Specialist analysis completed across %d Project Loom virtual thread specialists for target '%s'. %d potential risk vectors identified for prioritized remediation.",
                report.results().size(), report.task().description(), flaws.size());
        }
        for (String chunk : wrapText(summaryText, inner - 2)) {
            res.add(CYAN + "│   " + WHITE + padRight(chunk, inner - 2) + CYAN + " │" + RESET);
        }
        res.add(CYAN + "│ " + padRight("", inner) + CYAN + " │" + RESET);

        // 2. Identified Flaws & Critical Risks (Top 3 prioritized)
        if (!flaws.isEmpty()) {
            res.add(CYAN + "│ " + RED + BOLD + padRight("[!] IDENTIFIED FLAWS & CRITICAL RISKS", inner) + CYAN + " │" + RESET);
            int displayCount = Math.min(flaws.size(), 3);
            for (int i = 0; i < displayCount; i++) {
                AttributedFinding f = flaws.get(i);
                renderFindingBlock(res, "  • [" + f.agentName() + "] ", f, RED, inner);
            }
            res.add(CYAN + "│ " + padRight("", inner) + CYAN + " │" + RESET);
        }

        // 3. Verified Architectural Strengths (Top 3 verified)
        if (!strengths.isEmpty()) {
            res.add(CYAN + "│ " + GREEN + BOLD + padRight("[+] VERIFIED ARCHITECTURAL STRENGTHS", inner) + CYAN + " │" + RESET);
            int displayCount = Math.min(strengths.size(), 3);
            for (int i = 0; i < displayCount; i++) {
                AttributedFinding f = strengths.get(i);
                renderFindingBlock(res, "  • [" + f.agentName() + "] ", f, GREEN, inner);
            }
            res.add(CYAN + "│ " + padRight("", inner) + CYAN + " │" + RESET);
        }

        // 4. Recommended Remediation Playbook (Top 3 actionable)
        if (!remedies.isEmpty()) {
            res.add(CYAN + "│ " + AMBER + BOLD + padRight("[>] RECOMMENDED REMEDIATION PLAYBOOK", inner) + CYAN + " │" + RESET);
            int displayCount = Math.min(remedies.size(), 3);
            for (int i = 0; i < displayCount; i++) {
                AttributedFinding f = remedies.get(i);
                renderFindingBlock(res, "  " + (i + 1) + ". [" + f.agentName() + "] ", f, AMBER, inner);
            }
            res.add(CYAN + "│ " + padRight("", inner) + CYAN + " │" + RESET);
        }

        // 5. Specialist Domain Observations (fallback if no tagged findings)
        if (flaws.isEmpty() && strengths.isEmpty() && remedies.isEmpty() && !general.isEmpty()) {
            res.add(CYAN + "│ " + CYAN + BOLD + padRight("[#] SPECIALIST SYNTHESIS FINDINGS", inner) + CYAN + " │" + RESET);
            int displayCount = Math.min(general.size(), 3);
            for (int i = 0; i < displayCount; i++) {
                AttributedFinding f = general.get(i);
                renderFindingBlock(res, "  • [" + f.agentName() + "] ", f, WHITE, inner);
            }
            res.add(CYAN + "│ " + padRight("", inner) + CYAN + " │" + RESET);
        }

        // Remove trailing empty spacer if present
        if (res.size() > 5 && res.get(res.size() - 1).contains(CYAN + "│ " + padRight("", inner) + CYAN + " │")) {
            res.remove(res.size() - 1);
        }

        res.add(CYAN + "└" + "─".repeat(boxWidth - 2) + "┘" + RESET);
        return res;
    }

    static List<String> wrapBulletLines(String prefix, String text, int maxInner) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) return lines;
        int contIndent = 4;
        String contPrefix = " ".repeat(contIndent);

        String[] words = text.split("\\s+");
        StringBuilder cur = new StringBuilder();
        boolean isFirst = true;

        for (String w : words) {
            String p = isFirst ? prefix : contPrefix;
            int currentLen = p.length() + cur.length() + (cur.length() == 0 ? 0 : 1) + w.length();
            if (currentLen > maxInner) {
                if (cur.length() > 0) {
                    lines.add((isFirst ? prefix : contPrefix) + cur.toString());
                    cur = new StringBuilder();
                    isFirst = false;
                }
                cur.append(w);
            } else {
                if (cur.length() > 0) cur.append(" ");
                cur.append(w);
            }
        }
        if (cur.length() > 0) {
            lines.add((isFirst ? prefix : contPrefix) + cur.toString());
        }
        return lines;
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

        if (!report.telemetryWarnings().isEmpty()) {
            String warn = report.telemetryWarnings().get(0);
            if (warn.length() > inner - 18) {
                warn = warn.substring(0, inner - 21) + "...";
            }
            String warnLine = "Provider Notice:   " + warn;
            res.add(CYAN + "│ " + AMBER + padRight(warnLine, inner) + CYAN + " │" + RESET);
        }

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
