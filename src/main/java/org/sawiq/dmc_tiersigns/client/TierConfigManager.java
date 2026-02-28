package org.sawiq.dmc_tiersigns.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TierConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("dmc_tiersigns.json");

    private static TierConfig config = new TierConfig();

    private TierConfigManager() {
    }

    public static TierConfig get() {
        return config;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            TierConfig loaded = GSON.fromJson(reader, TierConfig.class);
            config = loaded == null ? new TierConfig() : loaded;
            clamp();
        } catch (IOException | JsonParseException e) {
            config = new TierConfig();
            save();
        }
    }

    public static void save() {
        clamp();
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static void clamp() {
        if (config.scanRadius < 8) {
            config.scanRadius = 8;
        }
        if (config.scanRadius > 96) {
            config.scanRadius = 96;
        }
        if (config.hudEspHeightPercent < 10) {
            config.hudEspHeightPercent = 10;
        }
        if (config.hudEspHeightPercent > 140) {
            config.hudEspHeightPercent = 140;
        }
        if (config.hudEspWidthPercent < 10) {
            config.hudEspWidthPercent = 10;
        }
        if (config.hudEspWidthPercent > 120) {
            config.hudEspWidthPercent = 120;
        }
        if (config.hudEspYOffset < -40) {
            config.hudEspYOffset = -40;
        }
        if (config.hudEspYOffset > 40) {
            config.hudEspYOffset = 40;
        }
        if (config.iconOpacityPercent < 10) {
            config.iconOpacityPercent = 10;
        }
        if (config.iconOpacityPercent > 100) {
            config.iconOpacityPercent = 100;
        }
        if (!config.showTier0 && !config.showTier1 && !config.showTier2 && !config.showTier3) {
            config.showTier0 = true;
            config.showTier1 = true;
            config.showTier2 = true;
            config.showTier3 = true;
        }
    }
}
