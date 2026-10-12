package blui.app;

import blui.swarm.SwarmEngine;
import blui.swarm.SwarmResult;
import blui.swarm.SwarmTask;

import java.util.List;

/**
 * Verification test suite for Blui CLI layout, responsive box widths, and telemetry formatting.
 */
public class BluiCliTest {

    public static void main(String[] args) {
        System.out.println("Running BluiCli verification suite...");

        testCardWidthsAndAlignment();
        testRightSideHeaderWidth();
        testSummarizeBoundary();
        testCleanGoal();
        testTelemetryCardFormatting();
        testMonologueSanitization();
        testClarificationDetection();
        testSystemPromptDirectives();
        testGeminiModelDefaults();
        testOpenRouterModelDefaultsAndProviderResolution();
        testSwarmHeaderCommandIncluded();
        testBenchCommandRemovedFromHeader();
        testSwarmCommandParsing();
        testSwarmTelemetryBoxFormatting();
        testExecutiveSynthesisBoxFormatting();
        testAmbiguousGoalValidationInCli();
        testBliTypoAndSwarmParsing();
        testDynamicSwarmSynthesisBox();
        testSwarmInvocationRouting();
        testTelemetryCardDynamicBoxWidth();
        testToolBadgeAndIconResolution();
        testHttpStatusPhraseResolution();

        System.out.println("All 22 BluiCli tests passed successfully!");
    }

    private static void testCardWidthsAndAlignment() {
        List<String[]> entries = List.of(
                new String[]{"JVM", "OpenJDK 24.0.2"},
                new String[]{"Loom", "Virtual Threads"},
                new String[]{"Governor", "Circuit Breaker"}
        );

        int cardWidth = 32;
        List<String> card = BluiCli.buildCardForTesting("RUNTIME STATUS", entries, cardWidth);

        assertCondition(card.size() == entries.size() + 2, "Card should have header, entries, and footer");
        for (String line : card) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == cardWidth, 
                    "Card line visual length must be exactly " + cardWidth + " but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }

        // Test 3 cards side-by-side: 32 + 1 + 32 + 1 + 32 = 98 cols
        int totalWidth = (cardWidth * 3) + 2;
        assertCondition(totalWidth == 98, "Three cards side-by-side must be exactly 98 columns");

        System.out.println("  ✓ testCardWidthsAndAlignment passed");
    }

    private static void testRightSideHeaderWidth() {
        List<String> headerLines = BluiCli.buildRightSideHeaderForTesting();
        for (String line : headerLines) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen <= 62, 
                    "Header line must be <= 62 columns. Got " + visualLen + ": '" + stripAnsi(line) + "'");
        }
        System.out.println("  ✓ testRightSideHeaderWidth passed");
    }

    private static void testSummarizeBoundary() {
        String shortLine = "hello world";
        assertCondition("hello world".equals(BluiCli.summarizeForTesting(shortLine)), "Short line should remain unchanged");

        String longLine = "a".repeat(100);
        String summarized = BluiCli.summarizeForTesting(longLine);
        assertCondition(summarized.length() <= 40, "Summarized line must not exceed 40 characters");
        assertCondition(summarized.endsWith("..."), "Truncated line must end with '...'");

        assertCondition("(empty)".equals(BluiCli.summarizeForTesting(null)), "Null input should return (empty)");
        assertCondition("(empty)".equals(BluiCli.summarizeForTesting("   ")), "Blank input should return (empty)");
        System.out.println("  ✓ testSummarizeBoundary passed");
    }

    private static void testCleanGoal() {
        assertCondition("what time is it".equals(BluiCli.cleanGoalForTesting("<what time is it>")), "Must strip angle brackets");
        assertCondition("what time is it".equals(BluiCli.cleanGoalForTesting("\"what time is it\"")), "Must strip double quotes");
        assertCondition("what time is it".equals(BluiCli.cleanGoalForTesting("'what time is it'")), "Must strip single quotes");
        assertCondition("what time is it".equals(BluiCli.cleanGoalForTesting("<\"what time is it\">")), "Must strip nested angle brackets and quotes");
        assertCondition("what time is it".equals(BluiCli.cleanGoalForTesting("  <what time is it>  ")), "Must trim outer whitespace");
        assertCondition("".equals(BluiCli.cleanGoalForTesting(null)), "Null should return empty string");
        assertCondition("".equals(BluiCli.cleanGoalForTesting("<>")), "Empty angle brackets should return empty string");
        System.out.println("  ✓ testCleanGoal passed");
    }

    private static void testTelemetryCardFormatting() {
        // Test standard formatting without trace ID
        List<String> telemetryLines = BluiCli.buildTelemetryLinesForTesting(
                225, 55, 0.00084, 2, 1240, "gemini-3.5-flash"
        );

        assertCondition(telemetryLines.size() == 5, "Telemetry box must have 5 lines (top, 3 content, bottom)");
        int boxWidth = 72;
        for (String line : telemetryLines) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == boxWidth, 
                    "Telemetry line visual length must be " + boxWidth + " but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }

        String contentJoined = String.join("\n", telemetryLines);
        assertCondition(contentJoined.contains("225 in (prompt)"), "Must show prompt tokens");
        assertCondition(contentJoined.contains("55 out (completion)"), "Must show completion tokens");
        assertCondition(contentJoined.contains("280 total"), "Must show total tokens");
        assertCondition(contentJoined.contains("1.24s"), "Must format latency as seconds (1.24s)");
        assertCondition(contentJoined.contains("gemini-3.5-flash"), "Must show model name");

        // Test with embedded trace ID
        List<String> withTrace = BluiCli.buildTelemetryLinesForTesting(
                225, 55, 0.00084, 2, 1240, "gemini-3.5-flash", "trace_1791726451987"
        );
        for (String line : withTrace) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == boxWidth,
                    "With-trace line visual length must be " + boxWidth + " but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }
        String withTraceJoined = String.join("\n", withTrace);
        assertCondition(withTraceJoined.contains("Trace: #1791726451987"), "Must show embedded trace ID");
        assertCondition(withTraceJoined.contains("Gov: Nominal"), "Must show Gov: Nominal");

        // Verify accidental API key in model is sanitized in telemetry card
        List<String> withLeak = BluiCli.buildTelemetryLinesForTesting(
                100, 20, 0.0001, 1, 500, "sk-or-v1-accidental-api-key"
        );
        String leakJoined = String.join("\n", withLeak);
        assertCondition(!leakJoined.contains("sk-or-v1-accidental-api-key"), "Telemetry box must never print API key in model field");
        assertCondition(leakJoined.contains("nvidia/nemotron-3.5-lightning:free"), "Telemetry box must fallback to default model name");

        // Verify bullet masked model is sanitized in telemetry card
        List<String> withBullets = BluiCli.buildTelemetryLinesForTesting(
                100, 20, 0.0001, 1, 500, "sk-or-••••••••abcd"
        );
        String bulletsJoined = String.join("\n", withBullets);
        assertCondition(!bulletsJoined.contains("••••"), "Telemetry box must never print bullet tokens in model field");
        assertCondition(bulletsJoined.contains("nvidia/nemotron-3.5-lightning:free"), "Telemetry box with bullets must fallback to default model name");

        System.out.println("  ✓ testTelemetryCardFormatting passed");
    }

    private static void testMonologueSanitization() {
        // 1. Single sentence monologue leak
        String leak1 = "Wait, let's understand what the user wants. Could you please clarify what you mean by 'hu'?";
        assertCondition("Could you please clarify what you mean by 'hu'?".equals(BluiCli.sanitizeMonologueForTesting(leak1)),
                "Must strip 'Wait, let's understand...' leak");

        // 2. Multi-sentence monologue leak
        String leak2 = "Wait, let's understand what the user wants. The user inputted \"hu\". Could you please clarify your request?";
        assertCondition("Could you please clarify your request?".equals(BluiCli.sanitizeMonologueForTesting(leak2)),
                "Must strip multiple monologue sentences");

        // 3. Multiline with think and thinking tags
        String leak3 = "<think>\nThe user inputted 'hu'. This is ambiguous.\n</think>\nWhat would you like me to do?";
        assertCondition("What would you like me to do?".equals(BluiCli.sanitizeMonologueForTesting(leak3)),
                "Must strip <think> XML tags");

        String leak3b = "<thinking>\nThe user wants something unclear.\n</thinking>\nCould you clarify?";
        assertCondition("Could you clarify?".equals(BluiCli.sanitizeMonologueForTesting(leak3b)),
                "Must strip <thinking> XML tags");

        // 4. Scratchpad header leak and markdown code fence
        String leak4 = "Scratchpad: The user inputted 'hu'.\nPlease clarify your goal.";
        assertCondition("Please clarify your goal.".equals(BluiCli.sanitizeMonologueForTesting(leak4)),
                "Must strip scratchpad header leak");

        String leak4b = "```thinking\nAnalyzing input...\n```\nPlease specify your destination.";
        assertCondition("Please specify your destination.".equals(BluiCli.sanitizeMonologueForTesting(leak4b)),
                "Must strip ```thinking``` code fence");

        // 5. Quote with punctuation inside
        String leak5 = "The user inputted \"hu.\" Could you please clarify your request?";
        assertCondition("Could you please clarify your request?".equals(BluiCli.sanitizeMonologueForTesting(leak5)),
                "Must strip sentence with punctuation inside quotation");

        // 6. Normal text starting with 'Wait for' or 'The user profile' must NOT be stripped
        String normal1 = "Wait for 5 seconds before checking again.";
        assertCondition(normal1.equals(BluiCli.sanitizeMonologueForTesting(normal1)),
                "Must NOT strip legitimate 'Wait for' instructions");

        String normal2 = "The user profile feature was implemented successfully.";
        assertCondition(normal2.equals(BluiCli.sanitizeMonologueForTesting(normal2)),
                "Must NOT strip legitimate 'The user profile...' sentences");

        // 7. Complete monologue with no question falls back to clarification prompt
        String pureMonologue = "Wait, let's understand the user. The user inputted 'hu'.";
        String fallback = BluiCli.sanitizeMonologueForTesting(pureMonologue);
        assertCondition(fallback != null && !fallback.isBlank() && fallback.contains("clarif"),
                "Pure monologue should fallback to clean clarification prompt");

        // 8. Edge cases: null, empty, whitespace, and CRLF
        assertCondition("".equals(BluiCli.sanitizeMonologueForTesting(null)), "Null input should return empty string");
        assertCondition("Could you please clarify your request?".equals(BluiCli.sanitizeMonologueForTesting("   ")), "Whitespace input should return clarification prompt");
        String crlfLeak = "Wait, let's understand.\r\nCould you clarify what you need?";
        assertCondition("Could you clarify what you need?".equals(BluiCli.sanitizeMonologueForTesting(crlfLeak)), "CRLF line endings should be handled correctly");

        // 9. Italic and bold monologue
        assertCondition("Could you clarify?".equals(BluiCli.sanitizeMonologueForTesting("*Wait, let's understand what the user is asking.* Could you clarify?")),
                "Must strip italic monologue without leaving dangling asterisks");
        assertCondition("Could you clarify?".equals(BluiCli.sanitizeMonologueForTesting("**Thinking:** The user wants \"hu\".\nCould you clarify?")),
                "Must strip bold thinking header");
        assertCondition("Could you clarify?".equals(BluiCli.sanitizeMonologueForTesting("### Thinking\nThe user entered 'hu'.\nCould you clarify?")),
                "Must strip markdown h3 thinking header and monologue");
        assertCondition("Could you clarify?".equals(BluiCli.sanitizeMonologueForTesting("<reasoning>Analyzing...</reasoning>Could you clarify?")),
                "Must strip reasoning XML tags");
        assertCondition("Could you clarify?".equals(BluiCli.sanitizeMonologueForTesting("Wait, let's understand:\n1. The user entered \"hu\".\nCould you clarify?")),
                "Must strip numbered scratchpad lists");
        assertCondition("What did you mean?".equals(BluiCli.sanitizeMonologueForTesting("The user provided \"hu\". What did you mean?")),
                "Must strip 'The user provided...' monologue");

        System.out.println("  ✓ testMonologueSanitization passed");
    }

    private static void testClarificationDetection() {
        assertCondition(BluiCli.isClarificationRequestForTesting("Could you please clarify what you mean by 'hu'?"),
                "Should detect 'Could you please clarify...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Please clarify your request."),
                "Should detect 'Please clarify...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Did you mean 'help'?"),
                "Should detect 'Did you mean...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("What would you like me to do?"),
                "Should detect 'What would you like...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Could you provide more details about what you want to achieve?"),
                "Should detect 'Could you provide more details...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Could you clarify what you mean by \"hu\"?"),
                "Should detect question with quoted word as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Please elaborate on your request."),
                "Should detect 'Please elaborate...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Please rephrase your goal."),
                "Should detect 'Please rephrase...' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Please be more specific."),
                "Should detect 'Please be more specific.' as clarification");
        assertCondition(BluiCli.isClarificationRequestForTesting("Could you be more specific?"),
                "Should detect 'Could you be more specific?' as clarification");

        // Non-clarifications (completed goals)
        assertCondition(!BluiCli.isClarificationRequestForTesting("The capital of France is Paris."),
                "Factual answer must NOT be marked as clarification");
        assertCondition(!BluiCli.isClarificationRequestForTesting("Successfully created 3 files in ./src."),
                "Completion statement must NOT be marked as clarification");
        assertCondition(!BluiCli.isClarificationRequestForTesting("In French, 'How are you?' is translated as 'Comment allez-vous ?'."),
                "Answer containing question mark in quotes must NOT be marked as clarification");

        // Edge cases: null, empty, whitespace
        assertCondition(!BluiCli.isClarificationRequestForTesting(null), "Null should not be clarification");
        assertCondition(!BluiCli.isClarificationRequestForTesting(""), "Empty should not be clarification");
        assertCondition(!BluiCli.isClarificationRequestForTesting("   "), "Whitespace should not be clarification");

        System.out.println("  ✓ testClarificationDetection passed");
    }

    private static void testSystemPromptDirectives() {
        String prompt = BluiCli.buildSystemPromptForTesting();
        assertCondition(prompt.contains("chain-of-thought") || prompt.contains("monologue") || prompt.contains("scratchpad"),
                "System prompt must explicitly forbid internal chain-of-thought/scratchpads/monologues");
        assertCondition(prompt.contains("ambiguous") || prompt.contains("typo") || prompt.contains("clarification"),
                "System prompt must instruct agent to request clarification on ambiguous inputs/typos");

        System.out.println("  ✓ testSystemPromptDirectives passed");
    }

    private static void testGeminiModelDefaults() {
        assertCondition("gemini-3.5-flash".equals(blui.llm.GeminiClient.DEFAULT_MODEL),
                "DEFAULT_MODEL must be gemini-3.5-flash");
        assertCondition("gemini-3.7-flash".equals(blui.llm.GeminiClient.FALLBACK_MODEL),
                "FALLBACK_MODEL must be gemini-3.7-flash");

        System.out.println("  ✓ testGeminiModelDefaults passed");
    }

    private static void testOpenRouterModelDefaultsAndProviderResolution() {
        assertCondition("nvidia/nemotron-3.5-lightning:free".equals(blui.llm.OpenRouterClient.DEFAULT_MODEL),
                "DEFAULT_MODEL must be nvidia/nemotron-3.5-lightning:free");
        assertCondition("high".equals(blui.llm.OpenRouterClient.DEFAULT_REASONING_EFFORT),
                "DEFAULT_REASONING_EFFORT must be high");

        blui.llm.OpenRouterClient client = new blui.llm.OpenRouterClient("test-key");
        assertCondition("nvidia/nemotron-3.5-lightning:free".equals(client.modelName()),
                "Client model name must match default model");
        assertCondition("high".equals(client.reasoningEffort()),
                "Client reasoning effort must match default");

        // Verify client constructed with corrupted model string reverts to default
        blui.llm.OpenRouterClient clientCorrupt = new blui.llm.OpenRouterClient("test-key", "sk-or-v1-accidental-paste");
        assertCondition("nvidia/nemotron-3.5-lightning:free".equals(clientCorrupt.modelName()),
                "Client constructed with accidental key in model must revert to default model");

        blui.llm.OpenRouterClient clientKeyMatch = new blui.llm.OpenRouterClient("my-secret-key", "my-secret-key");
        assertCondition("nvidia/nemotron-3.5-lightning:free".equals(clientKeyMatch.modelName()),
                "Client with model matching API key must revert to default model");

        System.out.println("  ✓ testOpenRouterModelDefaultsAndProviderResolution passed");
    }

    private static void testSwarmHeaderCommandIncluded() {
        List<String> headerLines = BluiCli.buildRightSideHeaderForTesting();
        boolean foundSwarm = false;
        for (String line : headerLines) {
            String stripped = stripAnsi(line);
            if (stripped.contains("blui swarm") && stripped.contains("<goal>")) {
                foundSwarm = true;
                break;
            }
        }
        assertCondition(foundSwarm, "Right side header COMMANDS must include 'blui swarm <goal>'");
        System.out.println("  ✓ testSwarmHeaderCommandIncluded passed");
    }

    private static void testBenchCommandRemovedFromHeader() {
        List<String> headerLines = BluiCli.buildRightSideHeaderForTesting();
        for (String line : headerLines) {
            String stripped = stripAnsi(line);
            assertCondition(!stripped.contains("blui bench"), "Header commands must not include deprecated 'blui bench'");
        }
        System.out.println("  ✓ testBenchCommandRemovedFromHeader passed");
    }

    private static void testSwarmCommandParsing() {
        BluiCli.SwarmOptions opt1 = BluiCli.parseSwarmCommandForTesting("swarm audit blui codebase");
        assertCondition("audit blui codebase".equals(opt1.goal()), "Parsed goal should match: " + opt1.goal());
        assertCondition(!opt1.boost(), "Boost should be false by default");

        BluiCli.SwarmOptions opt2 = BluiCli.parseSwarmCommandForTesting("blui swarm /boost \"review security\"");
        assertCondition("review security".equals(opt2.goal()), "Parsed goal should match: " + opt2.goal());
        assertCondition(opt2.boost(), "Boost should be true for /boost");

        BluiCli.SwarmOptions opt3 = BluiCli.parseSwarmCommandForTesting("swarm /goal 'check loom'");
        assertCondition("check loom".equals(opt3.goal()), "Parsed goal should match: " + opt3.goal());

        BluiCli.SwarmOptions opt4 = BluiCli.parseSwarmCommandForTesting("");
        assertCondition(!opt4.goal().isBlank(), "Default goal must be provided for empty input");

        // Bug reproduction: goal containing the word "boost" without flag should NOT activate boost mode
        BluiCli.SwarmOptions optBoostInGoal = BluiCli.parseSwarmCommandForTesting("swarm \"boost throughput of virtual threads\"");
        assertCondition(!optBoostInGoal.boost(), "Goal containing word 'boost' without /boost flag must not enable boost mode");
        assertCondition("boost throughput of virtual threads".equals(optBoostInGoal.goal()), "Goal text should preserve 'boost': " + optBoostInGoal.goal());

        // Combination /boost /goal
        BluiCli.SwarmOptions optBoth = BluiCli.parseSwarmCommandForTesting("swarm /boost /goal \"audit blui codebase\"");
        assertCondition(optBoth.boost(), "Boost must be true for /boost /goal");
        assertCondition("audit blui codebase".equals(optBoth.goal()), "Goal must match: " + optBoth.goal());

        // Bug reproduction: blui /boost command stripping blui prefix
        BluiCli.SwarmOptions optBluiBoost = BluiCli.parseSwarmCommandForTesting("blui /boost \"audit blui codebase\"");
        assertCondition(optBluiBoost.boost(), "Boost must be true for blui /boost");
        assertCondition("audit blui codebase".equals(optBluiBoost.goal()),
                "Goal should not retain 'blui' prefix: " + optBluiBoost.goal());

        // Bug reproduction: goal containing the word 'goal' without flag must NOT be chopped
        BluiCli.SwarmOptions optGoalInSentence = BluiCli.parseSwarmCommandForTesting("swarm \"our primary goal is performance\"");
        assertCondition("our primary goal is performance".equals(optGoalInSentence.goal()),
                "Goal text containing 'goal' must be preserved in full: " + optGoalInSentence.goal());

        // MinimalJson extractString for non-string key
        String jsonWithNumber = "{\"key\": 123, \"other\": \"hello\"}";
        assertCondition(blui.llm.MinimalJson.extractString(jsonWithNumber, "key") == null,
                "extractString for non-string key must return null but got: " + blui.llm.MinimalJson.extractString(jsonWithNumber, "key"));

        System.out.println("  ✓ testSwarmCommandParsing passed");
    }

    private static void testSwarmTelemetryBoxFormatting() {
        SwarmTask task = new SwarmTask("t1", "Audit blui", "src");
        List<SwarmResult> results = List.of(
            new SwarmResult("SecurityAuditor", "Security", "All clean", 250, 45, true),
            new SwarmResult("PerformanceProfiler", "Performance", "Loom optimal", 280, 50, true)
        );
        SwarmEngine.SwarmReport report = new SwarmEngine.SwarmReport(task, results, "Consensus OK", 530, 0.00159, 85, false, 2);
        List<String> box = BluiCli.buildSwarmTelemetryBoxForTesting(report, 76);

        assertCondition(!box.isEmpty(), "Telemetry box must not be empty");
        for (String line : box) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == 76, "Box line visual length must be 76 but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }

        String joined = String.join("\n", box);
        assertCondition(joined.contains("Concurrent Agents"), "Box must contain 'Concurrent Agents'");
        assertCondition(joined.contains("Virtual Threads"), "Box must contain 'Virtual Threads'");
        assertCondition(joined.contains("Swarm Join Time"), "Box must contain 'Swarm Join Time'");
        assertCondition(joined.contains("Tokens"), "Box must contain 'Tokens'");
        assertCondition(joined.contains("Governor Status"), "Box must contain 'Governor Status'");

        System.out.println("  ✓ testSwarmTelemetryBoxFormatting passed");
    }

    private static void testExecutiveSynthesisBoxFormatting() {
        SwarmTask task = new SwarmTask("t1", "Audit blui codebase", "src");
        List<SwarmResult> results = List.of(
            new SwarmResult("SecurityAuditor", "Application & Sandbox Security", "FileTool workspace sandbox verified.", 250, 45, true),
            new SwarmResult("PerformanceProfiler", "Virtual Threads & High Throughput", "Project Loom virtual threads optimal.", 280, 50, true)
        );
        SwarmEngine.SwarmReport report = new SwarmEngine.SwarmReport(task, results, "Consensus OK", 530, 0.00159, 85, false, 2);
        List<String> box = BluiCli.buildExecutiveSynthesisBoxForTesting(report, 76);

        assertCondition(!box.isEmpty(), "Synthesis box must not be empty");
        for (String line : box) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == 76, "Synthesis box line visual length must be 76 but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }

        String joined = String.join("\n", box);
        assertCondition(joined.contains("Target:"), "Synthesis box must contain Target");
        assertCondition(joined.contains("Consensus:"), "Synthesis box must contain Consensus");
        assertCondition(joined.contains("SecurityAuditor"), "Synthesis box must contain SecurityAuditor");
        assertCondition(joined.contains("PerformanceProfiler"), "Synthesis box must contain PerformanceProfiler");

        System.out.println("  ✓ testExecutiveSynthesisBoxFormatting passed");
    }

    private static void testAmbiguousGoalValidationInCli() {
        assertCondition(SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("h")), "'h' must be detected as ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("hu")), "'hu' must be detected as ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("?")), "'?' must be detected as ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("   ")), "Whitespace must be ambiguous");
        assertCondition(SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("\"h\"")), "Quoted 'h' must be ambiguous");
        assertCondition(!SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("audit ./src")), "'audit ./src' must be valid");
        assertCondition(!SwarmEngine.isAmbiguousGoal(BluiCli.cleanGoalForTesting("design distributed rate limiter")), "Valid goal must pass");

        System.out.println("  ✓ testAmbiguousGoalValidationInCli passed");
    }

    private static void testBliTypoAndSwarmParsing() {
        // User typo 'bli' in swarm boost h
        BluiCli.SwarmOptions opt1 = BluiCli.parseSwarmCommandForTesting("bli swarm boost h");
        assertCondition("h".equals(opt1.goal()), "Parsed goal should be 'h': " + opt1.goal());
        assertCondition(opt1.boost(), "Boost must be true for 'bli swarm boost h'");

        // User typo 'bli' in swarm /boost with full goal
        BluiCli.SwarmOptions opt2 = BluiCli.parseSwarmCommandForTesting("bli swarm /boost \"design distributed rate limiter\"");
        assertCondition("design distributed rate limiter".equals(opt2.goal()), "Goal should match: " + opt2.goal());
        assertCondition(opt2.boost(), "Boost must be true for 'bli swarm /boost ...'");

        // Typo 'blu' prefix
        BluiCli.SwarmOptions opt3 = BluiCli.parseSwarmCommandForTesting("blu swarm \"check security and tokens\"");
        assertCondition("check security and tokens".equals(opt3.goal()), "Goal should match: " + opt3.goal());

        // ProcessCommand handles 'bli' commands cleanly without throwing
        java.util.Scanner dummyScanner = new java.util.Scanner("");
        boolean helpRes = BluiCli.processCommand("bli help", dummyScanner);
        assertCondition(helpRes, "processCommand must handle 'bli help'");

        boolean statusRes = BluiCli.processCommand("bli status", dummyScanner);
        assertCondition(statusRes, "processCommand must handle 'bli status'");

        boolean unknownRes = BluiCli.processCommand("bli unknowncmd", dummyScanner);
        assertCondition(unknownRes, "processCommand must handle unknown command without error");

        System.out.println("  ✓ testBliTypoAndSwarmParsing passed");
    }

    private static void testDynamicSwarmSynthesisBox() {
        SwarmTask task = new SwarmTask("task_dyn", "design distributed rate limiter", "redis");
        List<SwarmResult> results = List.of(
            new SwarmResult("SystemArchitect", "Distributed Architecture & Topology", "[RISK] Cluster split brain\n[PASS] Partition tolerance\n[REMEDY] Quorum checks", 250, 45, true),
            new SwarmResult("ProtocolDesigner", "API Contract & Transport Protocols", "[RISK] Payload bloat\n[PASS] Idempotency keys\n[REMEDY] Compact schema", 280, 50, true)
        );
        SwarmEngine.SwarmReport report = new SwarmEngine.SwarmReport(task, results, "Consensus OK", 530, 0.00159, 85, false, 2);
        List<String> box = BluiCli.buildExecutiveSynthesisBoxForTesting(report, 80);

        assertCondition(!box.isEmpty(), "Synthesis box must not be empty");
        for (String line : box) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == 80, "Box visual length must be 80: " + visualLen);
        }

        String joined = String.join("\n", box);
        assertCondition(joined.contains("SystemArchitect"), "Box must contain SystemArchitect");
        assertCondition(joined.contains("ProtocolDesigner"), "Box must contain ProtocolDesigner");

        System.out.println("  ✓ testDynamicSwarmSynthesisBox passed");
    }

    private static void testSwarmInvocationRouting() {
        // Natural language queries containing 'swarm' must NOT trigger swarm
        assertCondition(!BluiCli.isSwarmInvocationForTesting("what is docker swarm?"),
                "'what is docker swarm?' must NOT be recognized as swarm command");
        assertCondition(!BluiCli.isSwarmInvocationForTesting("how does a bee swarm communicate?"),
                "'how does a bee swarm communicate?' must NOT be recognized as swarm command");

        // Bare words 'boost' or 'goal' must NOT trigger swarm
        assertCondition(!BluiCli.isSwarmInvocationForTesting("boost throughput of database"),
                "'boost throughput of database' must NOT be recognized as swarm command");
        assertCondition(!BluiCli.isSwarmInvocationForTesting("goal is to reduce latency"),
                "'goal is to reduce latency' must NOT be recognized as swarm command");
        assertCondition(!BluiCli.isSwarmInvocationForTesting("our primary goal is correctness"),
                "'our primary goal is correctness' must NOT be recognized as swarm command");

        // Explicit swarm / audit commands MUST trigger swarm
        assertCondition(BluiCli.isSwarmInvocationForTesting("swarm"), "'swarm' must be recognized as swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("/swarm"), "'/swarm' must be recognized as swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("swarm audit blui codebase"), "'swarm audit blui codebase' must be swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("audit ./src"), "'audit ./src' must be swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("/audit"), "'/audit' must be swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("blui swarm"), "'blui swarm' must be swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("blui audit"), "'blui audit' must be swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("bli swarm"), "'bli swarm' must be swarm command");

        // Slash command flags MUST trigger swarm
        assertCondition(BluiCli.isSwarmInvocationForTesting("yeah /boost /goal"),
                "'yeah /boost /goal' must be recognized as swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("blui /boost 'audit blui codebase'"),
                "'blui /boost' must be recognized as swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("/boost 'audit blui codebase'"),
                "'/boost' must be recognized as swarm command");
        assertCondition(BluiCli.isSwarmInvocationForTesting("/goal 'audit blui codebase'"),
                "'/goal' must be recognized as swarm command");

        // Test swarm command parsing for 'yeah /boost /goal'
        BluiCli.SwarmOptions opt = BluiCli.parseSwarmCommandForTesting("yeah /boost /goal");
        assertCondition(opt.boost(), "'yeah /boost /goal' must have boost enabled");
        assertCondition("yeah".equals(opt.goal()), "Parsed goal must be 'yeah' but got: " + opt.goal());

        System.out.println("  ✓ testSwarmInvocationRouting passed");
    }

    private static void testTelemetryCardDynamicBoxWidth() {
        int customWidth = 96;
        List<String> lines = BluiCli.buildTelemetryLinesForTesting(
                150, 40, 0.0005, 2, 850, "gemini-3.5-flash", "trace_12345", customWidth
        );

        assertCondition(lines.size() == 5, "Telemetry card must have exactly 5 lines");
        for (String line : lines) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == customWidth,
                    "Telemetry line visual length must be " + customWidth + " but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }

        String joined = String.join("\n", lines);
        assertCondition(joined.contains("150 in (prompt)"), "Must contain prompt tokens");
        assertCondition(joined.contains("40 out (completion)"), "Must contain completion tokens");
        assertCondition(joined.contains("190 total"), "Must contain total tokens");
        assertCondition(joined.contains("0.85s"), "Must format duration as seconds (0.85s)");
        assertCondition(joined.contains("Trace: #12345"), "Must contain short trace ID");

        System.out.println("  ✓ testTelemetryCardDynamicBoxWidth passed");
    }

    private static void testToolBadgeAndIconResolution() {
        // 1. Web Fetch (HTTP GET)
        BluiCli.ToolBadge b1 = BluiCli.resolveToolBadge("HttpTool", "get https://news.ycombinator.com");
        assertCondition("↗".equals(b1.icon()), "HTTP fetch icon must be ↗");
        assertCondition(b1.label().contains("HTTP"), "HTTP label must contain 'HTTP'");

        // 2. API Call (HTTP POST / PUT / DELETE)
        BluiCli.ToolBadge b2 = BluiCli.resolveToolBadge("HttpTool", "post https://api.github.com/repos {\"name\":\"test\"}");
        assertCondition("⇋".equals(b2.icon()), "API call icon must be ⇋");
        assertCondition(b2.label().contains("API"), "API label must contain 'API'");

        // 3. Filesystem Tool
        BluiCli.ToolBadge b3 = BluiCli.resolveToolBadge("FileTool", "read src/blui/core/BluiEngine.java");
        assertCondition("≡".equals(b3.icon()), "Filesystem icon must be ≡");
        assertCondition(b3.label().contains("FILE"), "Filesystem label must contain 'FILE'");

        // 4. Shell Subprocess Tool
        BluiCli.ToolBadge b4 = BluiCli.resolveToolBadge("ShellTool", "git status");
        assertCondition("›".equals(b4.icon()), "Shell icon must be ›");
        assertCondition(b4.label().contains("SHELL"), "Shell label must contain 'SHELL'");

        System.out.println("  ✓ testToolBadgeAndIconResolution passed");
    }

    private static void testHttpStatusPhraseResolution() {
        assertCondition("OK".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(200)), "200 must resolve to OK");
        assertCondition("Created".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(201)), "201 must resolve to Created");
        assertCondition("Bad Request".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(400)), "400 must resolve to Bad Request");
        assertCondition("Unauthorized".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(401)), "401 must resolve to Unauthorized");
        assertCondition("Forbidden".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(403)), "403 must resolve to Forbidden");
        assertCondition("Not Found".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(404)), "404 must resolve to Not Found");
        assertCondition("Internal Server Error".equals(blui.tools.HttpTool.resolveHttpStatusPhrase(500)), "500 must resolve to Internal Server Error");
        System.out.println("  ✓ testHttpStatusPhraseResolution passed");
    }

    private static String stripAnsi(String s) {
        if (s == null) return "";
        return s.replaceAll("\u001B\\[[;\\d]*m", "");
    }

    private static void assertCondition(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }
}
