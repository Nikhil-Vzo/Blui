package blui.governor;


import java.util.concurrent.atomic.AtomicInteger;

public class Budget {
    private final int maxTokens;
    private final double maxCostUsd;
    private final double costPerTokenUsd;
    private final AtomicInteger usedTokens;

    public Budget(int maxTokens, double maxCostUsd, double costPerTokenUsd){
        if(maxTokens <= 0 || maxCostUsd <= 0.0 || costPerTokenUsd < 0.0)
        {
            throw new IllegalArgumentException("Budget limits and costs must be positive values " );
        }
        this.maxTokens = maxTokens;
        this.maxCostUsd = maxCostUsd;
        this.costPerTokenUsd = costPerTokenUsd;
        this.usedTokens = new AtomicInteger(0);
    }

    public void recordUsage(int tokens)
    {
        if(tokens < 0)
        {
            throw new IllegalArgumentException("tokens used cannot be negative");
        }
        this.usedTokens.addAndGet(tokens);
    }

    public int usedTokens()
    {
        return usedTokens.get();
    }

    public int remainingTokens()
    {
        return Math.max(0, maxTokens-usedTokens.get());
    }

    public double currentCostUsd()
    {
        return usedTokens.get()*costPerTokenUsd;
    }

    public boolean isExceeded()
    {
        return usedTokens.get() >= maxTokens || currentCostUsd() >= maxCostUsd;
    }
}
