package blui.app;

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

        System.out.println("All 9 BluiCli tests passed successfully!");
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
                225, 55, 0.00084, 2, 1240, "gemini-3.8-flash"
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
        assertCondition(contentJoined.contains("gemini-3.8-flash"), "Must show model name");

        // Test with embedded trace ID
        List<String> withTrace = BluiCli.buildTelemetryLinesForTesting(
                225, 55, 0.00084, 2, 1240, "gemini-3.8-flash", "trace_1791726451987"
        );
        for (String line : withTrace) {
            int visualLen = stripAnsi(line).length();
            assertCondition(visualLen == boxWidth,
                    "With-trace line visual length must be " + boxWidth + " but got " + visualLen + ": '" + stripAnsi(line) + "'");
        }
        String withTraceJoined = String.join("\n", withTrace);
        assertCondition(withTraceJoined.contains("Trace: #1791726451987"), "Must show embedded trace ID");
        assertCondition(withTraceJoined.contains("Gov: Nominal"), "Must show Gov: Nominal");

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

        // 3. Multiline with think tags
        String leak3 = "<think>\nThe user inputted 'hu'. This is ambiguous.\n</think>\nWhat would you like me to do?";
        assertCondition("What would you like me to do?".equals(BluiCli.sanitizeMonologueForTesting(leak3)),
                "Must strip <think> XML tags");

        // 4. Scratchpad header leak
        String leak4 = "Scratchpad: The user inputted 'hu'.\nPlease clarify your goal.";
        assertCondition("Please clarify your goal.".equals(BluiCli.sanitizeMonologueForTesting(leak4)),
                "Must strip scratchpad header leak");

        // 5. Normal text starting with 'Wait for' or 'The user profile' must NOT be stripped
        String normal1 = "Wait for 5 seconds before checking again.";
        assertCondition(normal1.equals(BluiCli.sanitizeMonologueForTesting(normal1)),
                "Must NOT strip legitimate 'Wait for' instructions");

        String normal2 = "The user profile feature was implemented successfully.";
        assertCondition(normal2.equals(BluiCli.sanitizeMonologueForTesting(normal2)),
                "Must NOT strip legitimate 'The user profile...' sentences");

        // 6. Complete monologue with no question falls back to clarification prompt
        String pureMonologue = "Wait, let's understand the user. The user inputted 'hu'.";
        String fallback = BluiCli.sanitizeMonologueForTesting(pureMonologue);
        assertCondition(fallback != null && !fallback.isBlank() && fallback.contains("clarif"),
                "Pure monologue should fallback to clean clarification prompt");

        // 7. Edge cases: null, empty, whitespace, and CRLF
        assertCondition("".equals(BluiCli.sanitizeMonologueForTesting(null)), "Null input should return empty string");
        assertCondition("Could you please clarify your request?".equals(BluiCli.sanitizeMonologueForTesting("   ")), "Whitespace input should return clarification prompt");
        String crlfLeak = "Wait, let's understand.\r\nCould you clarify what you need?";
        assertCondition("Could you clarify what you need?".equals(BluiCli.sanitizeMonologueForTesting(crlfLeak)), "CRLF line endings should be handled correctly");

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
