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
    callHistory.add(toolName + "::"+ input);
    }

/** chekcs if last tool calls are identical */

public boolean isLoopDetected()
{
    if(callHistory.size() < maxRepeats)
    {
    return false;
    }
    int lastIndex = callHistory.size() - 1;
    String latestCall = callHistory.get(lastIndex);

    for (int i =1; i < maxRepeats ;i++)
    {
        if (!latestCall.equals(callHistory.get(lastIndex - i))) {
            return false;
        }
    }
    return true;
}

public List<String> history()
{
    return List.copyOf(callHistory);
}
}
