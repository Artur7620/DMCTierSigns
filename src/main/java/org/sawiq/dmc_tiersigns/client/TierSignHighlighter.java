package org.sawiq.dmc_tiersigns.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public final class TierSignHighlighter {

    private static final Identifier OUTLINE_TEXTURE =
        Identifier.of("dmc_tiersigns", "textures/gui/outline.png");

    private static final Map<BlockPos, Float>  alphaMap       = new LinkedHashMap<>();
    // Анимация появления: scale от 1.3 → 1.0
    private static final Map<BlockPos, Float>  scaleMap       = new LinkedHashMap<>();
    public static final List<Marker>          CACHED_MARKERS = new ArrayList<>();
    // Уже проигранные звуки чтобы не спамить
    private static final Set<BlockPos>         soundPlayed    = new HashSet<>();

    private static int    nearestTier  = -1;
    private static double nearestDist  = 0;
    private static float  toastAlpha   = 0F;
    private static float  toastSlide   = 0F;
    private static long   lastScanTick = -1L;

    private TierSignHighlighter() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TierSignHighlighter::tickScan);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TierSignHighlighter::render);
        HudRenderCallback.EVENT.register(TierSignHighlighter::renderHud);
    }

    // ── Сканирование ─────────────────────────────────────────────────────────
    private static void tickScan(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            CACHED_MARKERS.clear(); alphaMap.clear(); scaleMap.clear();
            soundPlayed.clear(); nearestTier = -1; return;
        }
        long now = client.world.getTime();
        if (lastScanTick >= 0 && now - lastScanTick < 20) return;
        lastScanTick = now;

        TierConfig cfg   = TierConfigManager.get();
        double maxDistSq = (double) cfg.scanRadius * cfg.scanRadius;
        Vec3d  playerPos = client.player.getPos();
        int pcx = client.player.getBlockX() >> 4;
        int pcz = client.player.getBlockZ() >> 4;
        int cr  = Math.max(1, (cfg.scanRadius + 15) / 16 + 1);

        Set<BlockPos> found   = new HashSet<>();
        List<Marker>  fresh   = new ArrayList<>();
        nearestTier = -1;
        double bestDist    = Double.MAX_VALUE;
        double toastDistSq = cfg.toastDistance * cfg.toastDistance;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                WorldChunk chunk = client.world.getChunk(cx, cz);
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof SignBlockEntity sign)) continue;
                    BlockPos pos  = sign.getPos();
                    double   dist = playerPos.squaredDistanceTo(pos.toCenterPos());
                    if (dist > maxDistSq) continue;
                    int tier = parseTier(sign);
                    if (tier < 0 || !cfg.isTierVisible(tier)) continue;
                    BlockPos imm = pos.toImmutable();
                    found.add(imm);
                    fresh.add(new Marker(imm, tier));

                    // Звук при первом обнаружении
                    if (cfg.soundEnabled && !soundPlayed.contains(imm)) {
                        soundPlayed.add(imm);
                        client.player.playSound(
                            SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                            0.4F, tier == 0 ? 0.5F : 0.8F + tier * 0.1F
                        );
                    }

                    if (dist < bestDist && dist <= toastDistSq) {
                        bestDist    = dist;
                        nearestTier = tier;
                        nearestDist = Math.sqrt(dist);
                    }
                }
            }
        }

        // Убираем звуки для исчезнувших маркеров
        soundPlayed.retainAll(found);

        CACHED_MARKERS.clear();
        CACHED_MARKERS.addAll(fresh);
        for (BlockPos p : found) {
            alphaMap.putIfAbsent(p, 0F);
            scaleMap.putIfAbsent(p, 1.3F); // начинаем с увеличенного
        }
        alphaMap.keySet().retainAll(found);
        scaleMap.keySet().retainAll(found);
    }

    // ── 3D Билборд ───────────────────────────────────────────────────────────
    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        TierConfig cfg = TierConfigManager.get();
        if (cfg.hiddenMode) return;

        Camera camera = context.camera();
        Vec3d  camPos = camera.getPos();
        float  time   = client.world.getTime() + context.tickCounter().getTickProgress(false);
        VertexConsumerProvider.Immediate consumers =
            client.getBufferBuilders().getEntityVertexConsumers();

        for (Marker mk : CACHED_MARKERS) {
            TierVisualStyle style = TierVisualStyle.byTier(mk.tier());

            // Плавное появление alpha
            float alpha = alphaMap.getOrDefault(mk.pos(), 0F);
            alpha += (1F - alpha) * 0.08F;
            alphaMap.put(mk.pos(), alpha);

            // Анимация scale: 1.3 → 1.0
            float scale = scaleMap.getOrDefault(mk.pos(), 1.0F);
            scale += (1.0F - scale) * 0.10F;
            scaleMap.put(mk.pos(), scale);

            // Полупрозрачность вблизи
            float proximity = 1F;
            if (cfg.fadeWhenClose) {
                double dist = camPos.distanceTo(mk.pos().toCenterPos());
                float raw = (float) MathHelper.clamp((dist - 1.0) / cfg.fadeDistance, 0.0, 1.0);
                proximity = raw * raw;
            }

            float fa = alpha * proximity * (cfg.iconOpacityPercent / 100F);
            if (fa < 0.01F) continue;

            // Пульсация
            float pulse = 0.97F + 0.03F * MathHelper.sin(time * style.pulseSpeed + mk.tier() * 0.6F);
            float sz    = 0.75F * pulse * scale;

            // Определяем позицию: под табличкой если 2 блока воздуха снизу, иначе над
            double iconOffsetY;
            if (client.world != null) {
                net.minecraft.util.math.BlockPos below1 = mk.pos().down(1);
                net.minecraft.util.math.BlockPos below2 = mk.pos().down(2);
                boolean air1 = client.world.getBlockState(below1).isAir();
                boolean air2 = client.world.getBlockState(below2).isAir();
                iconOffsetY = ((air1 && air2) ? -0.85 : 1.65) + cfg.iconHeightOffset;
            } else {
                iconOffsetY = 1.65 + cfg.iconHeightOffset;
            }
            matrices.push();
            matrices.translate(
                mk.pos().getX() + 0.5 - camPos.x,
                mk.pos().getY() + iconOffsetY - camPos.y,
                mk.pos().getZ() + 0.5  - camPos.z
            );
            matrices.multiply(camera.getRotation());
            matrices.scale(sz, sz, sz);
            Matrix4f mat = matrices.peek().getPositionMatrix();

            RenderLayer layer = cfg.wallhack
                ? RenderLayer.getEntityTranslucentEmissive(style.texture, true)
                : RenderLayer.getEntityTranslucent(style.texture);

            // Основная иконка
            addQuad(consumers.getBuffer(layer), mat, 1F, 1F, 1F, fa);

            // Контур поверх иконки
            if (cfg.outlineEnabled) {
                float outlineA = (cfg.outlineOpacity / 100F) * fa;
                // Tier 0: контур пульсирует
                if (mk.tier() == 0) {
                    outlineA *= 0.7F + 0.3F * MathHelper.sin(time * 0.08F);
                }
                addQuad(
                    consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(OUTLINE_TEXTURE, true)),
                    mat, 1F, 1F, 1F, outlineA
                );
            }

            // Tier 0: glow
            if (mk.tier() == 0) {
                float gA = 0.25F * (0.8F + 0.2F * MathHelper.sin(time * 0.04F)) * fa;
                matrices.push();
                matrices.scale(1.65F, 1.65F, 1F);
                addQuad(
                    consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(style.texture, true)),
                    matrices.peek().getPositionMatrix(),
                    style.red(), style.green(), style.blue(), gA
                );
                matrices.pop();
            }

            matrices.pop();
        }
        consumers.draw();
    }

    private static void addQuad(VertexConsumer vc, Matrix4f mat,
                                  float r, float g, float b, float a) {
        vc.vertex(mat, -0.5F,  0.5F, 0).color(r,g,b,a).texture(0F,0F)
          .overlay(OverlayTexture.DEFAULT_UV)
          .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
        vc.vertex(mat,  0.5F,  0.5F, 0).color(r,g,b,a).texture(1F,0F)
          .overlay(OverlayTexture.DEFAULT_UV)
          .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
        vc.vertex(mat,  0.5F, -0.5F, 0).color(r,g,b,a).texture(1F,1F)
          .overlay(OverlayTexture.DEFAULT_UV)
          .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
        vc.vertex(mat, -0.5F, -0.5F, 0).color(r,g,b,a).texture(0F,1F)
          .overlay(OverlayTexture.DEFAULT_UV)
          .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0,1,0);
    }

    // ── HUD ──────────────────────────────────────────────────────────────────
    private static void renderHud(DrawContext ctx,
                                   net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        TierConfig cfg = TierConfigManager.get();
        if (cfg.hiddenMode) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        float time = client.world.getTime() + tickCounter.getTickProgress(false);
        // ── Всплывающая подсказка ─────────────────────────────────────────
        if (nearestTier >= 0) {
            toastAlpha = Math.min(1F, toastAlpha + 0.07F);
            toastSlide = Math.min(1F, toastSlide + 0.09F);
        } else {
            toastAlpha = Math.max(0F, toastAlpha - 0.05F);
            toastSlide = Math.max(0F, toastSlide - 0.07F);
        }

        if (toastAlpha > 0.01F) {
            int displayTier = nearestTier >= 0 ? nearestTier : 0;
            TierVisualStyle ts = TierVisualStyle.byTier(displayTier);

            String dist = String.format("%.0fм", nearestDist);
            String msg  = "TIER " + displayTier + "  ·  " + dist;

            int tw   = client.textRenderer.getWidth(msg);
            int padX = 12, padY = 5;
            int bw   = tw + padX * 2;
            int bh   = 9  + padY * 2;
            int bx   = sw / 2 - bw / 2;

            float ease = toastSlide * toastSlide * (3F - 2F * toastSlide);
            int by = (sh - 56 - bh) + Math.round((1F - ease) * 12F);

            int bgA  = MathHelper.clamp(Math.round(toastAlpha * 150), 0, 255);
            int txtA = MathHelper.clamp(Math.round(toastAlpha * 255), 0, 255);
            int barA = MathHelper.clamp(Math.round(toastAlpha * 230), 0, 255);

            ctx.fill(bx, by, bx + bw, by + bh, (bgA << 24) | 0x000000);
            ctx.fill(bx, by, bx + bw, by + 2,  (barA << 24) | (ts.toastColor & 0x00FFFFFF));
            ctx.drawText(client.textRenderer, msg,
                bx + padX, by + padY,
                (txtA << 24) | (ts.toastColor & 0x00FFFFFF), true);
        }
    }

    // Импорт для предупреждения
    @SuppressWarnings("unused")
    private static void _callWarning(DrawContext ctx, MinecraftClient client) {
        renderWarning(ctx, client);
    }

    private static int parseTier(SignBlockEntity sign) {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 4; i++)
            sb.append(sign.getFrontText().getMessage(i, false).getString()).append(' ');
        for (int i = 0; i < 4; i++)
            sb.append(sign.getBackText().getMessage(i, false).getString()).append(' ');
        return TierSignParser.parseTier(sb.toString());
    }

    private static void renderWarning(DrawContext ctx, MinecraftClient client) {
        if (InteractWarningHandler.warningAlpha < 0.01F) return;
        int tier = InteractWarningHandler.nearestTierForWarn;
        TierVisualStyle ts = TierVisualStyle.byTier(Math.max(0, tier));
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        float a = InteractWarningHandler.warningAlpha;

        String line1 = "⚠ Рядом TIER " + tier;
        String line2 = "Вы уверены? Нажмите ещё раз для открытия";
        int tw1 = client.textRenderer.getWidth(line1);
        int tw2 = client.textRenderer.getWidth(line2);
        int bw = Math.max(tw1, tw2) + 24;
        int bh = 32;
        int bx = sw / 2 - bw / 2;
        int by = sh / 2 - bh / 2;

        int bgA  = Math.round(a * 180);
        int txtA = Math.round(a * 255);
        int barA = Math.round(a * 255);

        ctx.fill(bx, by, bx + bw, by + bh, (bgA << 24) | 0x000000);
        ctx.fill(bx, by, bx + bw, by + 2,  (barA << 24) | (ts.toastColor & 0x00FFFFFF));
        ctx.fill(bx, by + bh - 2, bx + bw, by + bh, (barA << 24) | (ts.toastColor & 0x00FFFFFF));
        ctx.drawText(client.textRenderer, line1,
            bx + 12, by + 6,  (txtA << 24) | (ts.toastColor & 0x00FFFFFF), true);
        ctx.drawText(client.textRenderer, line2,
            bx + 12, by + 18, (txtA << 24) | 0xAAAAAA, true);
    }

    public record Marker(BlockPos pos, int tier) {}
}
