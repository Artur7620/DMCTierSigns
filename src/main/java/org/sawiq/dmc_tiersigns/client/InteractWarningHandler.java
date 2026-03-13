package org.sawiq.dmc_tiersigns.client;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import java.util.HashSet;
import java.util.Set;

public class InteractWarningHandler {

    public static float warningAlpha = 0.0F;
    public static int nearestTierForWarn = -1;
    private static final Set<BlockPos> warned = new HashSet<>();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient) return ActionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            if (!checkInteract(MinecraftClient.getInstance(), pos)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    public static boolean checkInteract(MinecraftClient client, BlockPos pos) {
        TierConfig cfg = TierConfigManager.get();
        if (!cfg.warnOnInteract) return true;
        if (TierSignHighlighter.CACHED_MARKERS.isEmpty()) return true;
        for (TierSignHighlighter.Marker mk : TierSignHighlighter.CACHED_MARKERS) {
            if (Math.sqrt(pos.getSquaredDistance(mk.pos())) <= cfg.warnRadius) {
                if (warned.contains(pos)) {
                    warned.remove(pos);
                    warningAlpha = 0.0F;
                    return true;
                } else {
                    warned.add(pos);
                    warningAlpha = 1.0F;
                    nearestTierForWarn = mk.tier();
                    return false;
                }
            }
        }
        return true;
    }

    public static void tick() {
        if (warningAlpha > 0.0F) warningAlpha = Math.max(0.0F, warningAlpha - 0.02F);
    }
}
