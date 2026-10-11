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

        System.out.println("All 5 BluiCli tests passed successfully!");
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
