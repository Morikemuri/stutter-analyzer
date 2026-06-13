package io.github.morikemuri.stutteranalyzer;

import io.github.morikemuri.stutteranalyzer.client.FabricClientSetup;
import io.github.morikemuri.stutteranalyzer.config.SAConfig;
import io.github.morikemuri.stutteranalyzer.core.MetricsCollector;
import io.github.morikemuri.stutteranalyzer.core.SubsystemHealth;
import io.github.morikemuri.stutteranalyzer.events.RecentEventBuffer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class StutterAnalyzerFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Ensure client flag is set even if main init ran first in a different env type
        SAEnvironment.setClientSide(true);

        // Client tick - frame time tracking
        ClientTickEvents.END_CLIENT_TICK.register(FabricClientSetup::onClientTick);

        // Player login on client (for startup message / update notification)
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> FabricClientSetup.onPlayerLoggedIn());

        // Track world loads for HighLevelClassifier
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
            MetricsCollector.eventBuffer().push(RecentEventBuffer.EventType.WORLD_LOAD, "client joined"));

        // Client shutdown
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (SAConfig.INSTANCE.verboseModeSessionOnly.get()) {
                io.github.morikemuri.stutteranalyzer.core.VerboseMode.setEnabled(false);
            }
        });

        SubsystemHealth.setStatus("F3StatusLineRenderer", SubsystemHealth.Status.OK, null);
        StutterAnalyzerFabric.LOGGER.info("[StutterAnalyzer] Client setup complete.");
    }
}
