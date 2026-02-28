package org.sawiq.dmc_tiersigns.client;

import net.fabricmc.api.ClientModInitializer;

public class DmcTierSignsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TierConfigManager.load();
        TierSignHighlighter.init();
    }
}
