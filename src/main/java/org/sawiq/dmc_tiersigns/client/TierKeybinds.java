package org.sawiq.dmc_tiersigns.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class TierKeybinds {
    public static KeyBinding hideKey;

    private TierKeybinds() {}

    public static void register() {
        hideKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tiersigns.hide",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "TierSigns"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (hideKey.wasPressed()) {
                TierConfig cfg = TierConfigManager.get();
                cfg.hiddenMode = !cfg.hiddenMode;
                if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal(
                            cfg.hiddenMode ? "§7[TierSigns] §cМетки скрыты" : "§7[TierSigns] §aМетки показаны"
                        ), true
                    );
                }
            }
        });
    }
}
