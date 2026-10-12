package blui.governor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** detects infi. hallucinations loops */

public class CycleDetector
{
    private final int maxRepeats;
    private final List<String> callHistory;

    public CycleDetector(int maxRepeats)
    {
        if(maxRepeats <= 1)
        {
            throw new IllegalArgumentException("maxRepeats threshold must be greter than 1");
        }
        this.maxRepeats = maxRepeats;
        this.callHistory = Collections.synchronizedList(new ArrayList<>());
    }
    /**records a tool call signature (e.g. ToolNAme::inputPayload)  */

    public void recordCall(String toolName, String input)
    {
        if (toolName == null || input == null)
        {
            throw new IllegalArgumentException("toolName and input cannot be null");
        }
        String normalizedTool = toolName.strip();
        String normalizedInput = input.strip().replaceAll("\\s+", " ");
        callHistory.add(normalizedTool + "::" + normalizedInput);
    }

    /** Checks if sliding-window repetition pattern of any period k >= 1 has repeated maxRepeats times */
    public boolean isLoopDetected()
    {
        synchronized (callHistory) {
            int n = callHistory.size();
            if (n < maxRepeats)
            {
                return false;
            }
            for (int k = 1; k <= n / maxRepeats; k++) {
                boolean match = true;
                for (int rep = 1; rep < maxRepeats; rep++) {
                    for (int offset = 0; offset < k; offset++) {
                        String current = callHistory.get(n - 1 - offset);
                        String prev = callHistory.get(n - 1 - (rep * k) - offset);
                        if (!current.equals(prev)) {
                            match = false;
                            break;
                        }
                    }
                    if (!match) break;
                }
                if (match) {
                    return true;
                }
            }
            return false;
        }
    }

public List<String> history()
{
    return List.copyOf(callHistory);
}
}
