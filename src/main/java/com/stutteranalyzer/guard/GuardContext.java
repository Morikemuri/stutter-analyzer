package com.stutteranalyzer.guard;

public class GuardContext {

    public final String stackTrace;
    public final String logFragment;
    public final String blockFluidContext;
    public final boolean isClient;
    public final boolean isDedicatedServer;

    public GuardContext(String stackTrace, String logFragment, String blockFluidContext,
                        boolean isClient, boolean isDedicatedServer) {
        this.stackTrace       = stackTrace;
        this.logFragment      = logFragment;
        this.blockFluidContext = blockFluidContext;
        this.isClient         = isClient;
        this.isDedicatedServer = isDedicatedServer;
    }

    public String combined() {
        return (stackTrace + " " + logFragment + " " + blockFluidContext).toLowerCase();
    }
}
