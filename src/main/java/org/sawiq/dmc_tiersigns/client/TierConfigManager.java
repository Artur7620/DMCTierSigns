package org.sawiq.dmc_tiersigns.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.*;

public final class TierConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("tiersigns.json");
    private static TierConfig config = new TierConfig();

    private TierConfigManager() {}
    public static TierConfig get() { return config; }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) { save(); return; }
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            TierConfig loaded = GSON.fromJson(r, TierConfig.class);
            config = loaded == null ? new TierConfig() : loaded;
            clamp();
        } catch (IOException | JsonParseException e) {
            config = new TierConfig(); save();
        }
    }

    public static void save() {
        clamp();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, w);
            }
        } catch (IOException ignored) {}
    }

    private static void clamp() {
        config.scanRadius         = clamp(config.scanRadius,         8,   96);
        config.iconOpacityPercent = clamp(config.iconOpacityPercent, 10, 100);
        config.outlineOpacity     = clamp(config.outlineOpacity,     0,  100);
        config.toastDistance      = Math.max(1F, Math.min(20F, config.toastDistance));
        config.fadeDistance       = Math.max(1F, Math.min(10F, config.fadeDistance));
        if (!config.showTier0 && !config.showTier1 && !config.showTier2 && !config.showTier3)
            config.showTier0 = config.showTier1 = config.showTier2 = config.showTier3 = true;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
