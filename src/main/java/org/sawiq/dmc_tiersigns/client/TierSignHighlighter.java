package org.sawiq.dmc_tiersigns.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.sawiq.dmc_tiersigns.Dmc_tiersigns;

import java.util.ArrayList;
import java.util.List;

public final class TierSignHighlighter {
    private static final Identifier MARKER_TEXTURE = Identifier.of(Dmc_tiersigns.MOD_ID, "textures/gui/tier_marker.png");
    private static final int MARKER_TEXTURE_SIZE = 1024;
    private static final List<Marker> CACHED_MARKERS = new ArrayList<>();
    private static long lastScanTick = -1L;

    private TierSignHighlighter() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(TierSignHighlighter::tickScan);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(TierSignHighlighter::render);
        HudRenderCallback.EVENT.register(TierSignHighlighter::renderHudEsp);
    }

    private static void tickScan(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            CACHED_MARKERS.clear();
            return;
        }

        long now = client.world.getTime();
        if (lastScanTick >= 0 && now - lastScanTick < 20) {
            return;
        }
        lastScanTick = now;
        CACHED_MARKERS.clear();

        TierConfig config = TierConfigManager.get();
        double maxDistanceSq = (double) config.scanRadius * config.scanRadius;
        Vec3d playerPos = client.player.getPos();
        int playerChunkX = client.player.getBlockX() >> 4;
        int playerChunkZ = client.player.getBlockZ() >> 4;
        int chunkRadius = Math.max(1, (config.scanRadius + 15) / 16 + 1);

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                WorldChunk chunk = client.world.getChunk(cx, cz);
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof SignBlockEntity sign)) {
                        continue;
                    }

                    BlockPos pos = sign.getPos();
                    if (playerPos.squaredDistanceTo(pos.toCenterPos()) > maxDistanceSq) {
                        continue;
                    }

                    int tier = parseTier(sign);
                    if (tier < 0 || !config.isTierVisible(tier)) {
                        continue;
                    }
                    CACHED_MARKERS.add(new Marker(pos.toImmutable(), tier));
                }
            }
        }
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || CACHED_MARKERS.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }

        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        // 3D highlight disabled: keep only HUD ESP for reliable through-wall visibility.
        consumers.draw();
    }

    private static int parseTier(SignBlockEntity sign) {
        StringBuilder allText = new StringBuilder(32);
        appendSignSide(allText, sign.getFrontText());
        appendSignSide(allText, sign.getBackText());
        return TierSignParser.parseTier(allText.toString());
    }

    private static void appendSignSide(StringBuilder builder, net.minecraft.block.entity.SignText signText) {
        for (int i = 0; i < 4; i++) {
            Text text = signText.getMessage(i, false);
            builder.append(text.getString()).append(' ');
        }
    }

    private static void drawIcon(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d camPos, BlockPos pos, TierVisualStyle style, float pulse, float worldTime) {
        TierConfig config = TierConfigManager.get();
        matrices.push();
        matrices.translate(pos.getX() + 0.5 - camPos.x, pos.getY() + 1.45 - camPos.y, pos.getZ() + 0.5 - camPos.z);
        matrices.multiply(camera.getRotation());

        float opacity = MathHelper.clamp(config.iconOpacityPercent / 100.0F, 0.10F, 1.0F);
        float coreSize = style.coreScale * pulse * 1.35F;
        float glowSize = coreSize * style.glowScale;
        float haloDrift = 0.04F * MathHelper.sin(worldTime * 0.08F + style.tier);

        drawQuad(matrices, consumers, glowSize, style.red(), style.green(), style.blue(), Math.min(0.95F, style.alpha) * opacity, true);
        matrices.translate(0.0F, haloDrift, 0.0F);
        drawQuad(matrices, consumers, coreSize * 1.05F, style.red(), style.green(), style.blue(), Math.min(1.0F, style.alpha + 0.20F) * opacity, true);
        drawQuad(matrices, consumers, coreSize, style.red(), style.green(), style.blue(), Math.min(1.0F, style.alpha + 0.35F) * opacity, false);

        if (style.tier <= 1) {
            matrices.multiply(new Quaternionf().rotateAxis(worldTime * 0.015F + style.tier, new Vector3f(0.0F, 0.0F, 1.0F)));
            drawQuad(matrices, consumers, coreSize * 0.72F, 1.0F, 1.0F, 1.0F, 0.28F * opacity, true);
        }
        matrices.pop();
    }

    private static void drawQuad(MatrixStack matrices, VertexConsumerProvider consumers, float size, float red, float green, float blue, float alpha, boolean glowLayer) {
        matrices.push();
        matrices.scale(-size, -size, size);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderLayer layer = glowLayer
                ? RenderLayer.getEntityTranslucentEmissive(MARKER_TEXTURE, true)
                : RenderLayer.getEntityTranslucent(MARKER_TEXTURE);
        VertexConsumer iconConsumer = consumers.getBuffer(layer);

        iconConsumer.vertex(matrix, -0.5F, -0.5F, 0.0F).color(red, green, blue, alpha).texture(0.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
        iconConsumer.vertex(matrix, 0.5F, -0.5F, 0.0F).color(red, green, blue, alpha).texture(1.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
        iconConsumer.vertex(matrix, 0.5F, 0.5F, 0.0F).color(red, green, blue, alpha).texture(1.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
        iconConsumer.vertex(matrix, -0.5F, 0.5F, 0.0F).color(red, green, blue, alpha).texture(0.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0.0F, 1.0F, 0.0F);
        matrices.pop();
    }

    private static void drawTierLabel(TextRenderer textRenderer, MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d camPos, BlockPos pos, TierVisualStyle style, float pulse) {
        matrices.push();
        matrices.translate(pos.getX() + 0.5 - camPos.x, pos.getY() + 2.12 - camPos.y, pos.getZ() + 0.5 - camPos.z);
        matrices.multiply(camera.getRotation());

        float baseScale = style.tier <= 1 ? 0.052F : 0.047F;
        float scale = baseScale * (0.96F + pulse * 0.06F);
        matrices.scale(-scale, -scale, scale);

        String main = ">> " + style.labelMain + " <<";
        String sub = style.labelSub;
        float mainX = -textRenderer.getWidth(main) / 2.0F;
        float subX = -textRenderer.getWidth(sub) / 2.0F;

        textRenderer.draw(main, mainX + 1.2F, 1.2F, 0xCC000000, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x00000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        textRenderer.draw(main, mainX, 0.0F, style.mainColor(), false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0xC0000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        textRenderer.draw(sub, subX + 1.2F, 12.2F, 0xBB000000, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x00000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        textRenderer.draw(sub, subX, 11.0F, style.accentColor(), false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0xA0000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    private static void drawTierWallhackTag(TextRenderer textRenderer, MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d camPos, BlockPos pos, TierVisualStyle style, float pulse) {
        String tag = "TIER " + style.tier;
        float[] heights = new float[] {1.9F, 2.7F, 3.5F};
        float[] scales = new float[] {0.16F, 0.13F, 0.10F};
        int coreColor = style.mainColor() | 0xFF000000;

        for (int i = 0; i < heights.length; i++) {
            matrices.push();
            matrices.translate(pos.getX() + 0.5 - camPos.x, pos.getY() + heights[i] - camPos.y, pos.getZ() + 0.5 - camPos.z);
            matrices.multiply(camera.getRotation());

            float scale = scales[i] * (0.94F + pulse * 0.10F);
            matrices.scale(-scale, -scale, scale);

            float x = -textRenderer.getWidth(tag) / 2.0F;
            textRenderer.draw(tag, x + 1.8F, 1.8F, 0xFF000000, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0x00000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            textRenderer.draw(tag, x, 0.0F, coreColor, false, matrices.peek().getPositionMatrix(), consumers, TextRenderer.TextLayerType.SEE_THROUGH, 0xE0000000, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }
    }

    private static void renderHudEsp(DrawContext drawContext, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || CACHED_MARKERS.isEmpty()) return;
        TierConfig config = TierConfigManager.get();
        float time = client.world.getTime() + tickCounter.getTickProgress(false);

        Camera camera = client.gameRenderer.getCamera();
        Vec3d camPos = camera.getPos();
        Quaternionf invCamRot = new Quaternionf(camera.getRotation()).conjugate();
        float fov = Math.max(1.0F, client.options.getFov().getValue().floatValue());

        int sw = drawContext.getScaledWindowWidth();
        int sh = drawContext.getScaledWindowHeight();
        float focal = (float) (sh / (2.0 * Math.tan(Math.toRadians(fov) / 2.0)));

        for (Marker marker : CACHED_MARKERS) {
            Vec3d base = marker.pos.toCenterPos();
            Vec3d top = base.add(0.0, 1.1, 0.0);

            Vector3f baseCam = new Vector3f((float) (base.x - camPos.x), (float) (base.y - camPos.y), (float) (base.z - camPos.z)).rotate(invCamRot);
            Vector3f topCam = new Vector3f((float) (top.x - camPos.x), (float) (top.y - camPos.y), (float) (top.z - camPos.z)).rotate(invCamRot);

            if (baseCam.z >= -0.05F && topCam.z >= -0.05F) continue;

            float z = Math.min(baseCam.z, topCam.z);
            float sx = sw * 0.5F + (baseCam.x * focal / -z);
            float syBase = sh * 0.5F - (baseCam.y * focal / -z);
            float syTop = sh * 0.5F - (topCam.y * focal / -z);

            float scalePercent = (config.hudEspHeightPercent + config.hudEspWidthPercent) * 0.5F / 100.0F;
            float iconSizeF = Math.max(18.0F, Math.abs(syBase - syTop)) * scalePercent;
            int iconSize = Math.round(iconSizeF);
            int ix = Math.round(sx - iconSize * 0.5F);
            int iy = Math.round(Math.min(syTop, syBase) - iconSize - 6.0F + config.hudEspYOffset);

            if (ix + iconSize < 0 || ix > sw || iy + iconSize < 0 || iy > sh) continue;

            TierVisualStyle style = TierVisualStyle.byTier(marker.tier);
            int color = style.mainColor() | 0xFF000000;
            float iconAlpha = MathHelper.clamp(config.iconOpacityPercent / 100.0F, 0.10F, 1.0F);
            int iconColor = (((int) (iconAlpha * 255.0F)) << 24) | (style.mainColor() & 0x00FFFFFF);

            // Soft tier-colored glow behind the icon.
            float pulse = 0.55F + 0.45F * MathHelper.sin(time * 0.13F + marker.tier * 0.7F);
            int glowPad1 = Math.max(3, Math.round(iconSize * 0.16F));
            int glowPad2 = Math.max(6, Math.round(iconSize * 0.30F));
            int glowAlpha1 = Math.round((28 + 42 * pulse) * iconAlpha);
            int glowAlpha2 = Math.round((16 + 26 * pulse) * iconAlpha);
            int glowColor1 = (glowAlpha1 << 24) | (style.mainColor() & 0x00FFFFFF);
            int glowColor2 = (glowAlpha2 << 24) | (style.mainColor() & 0x00FFFFFF);
            drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, MARKER_TEXTURE, ix - glowPad2, iy - glowPad2, 0.0F, 0.0F, iconSize + glowPad2 * 2, iconSize + glowPad2 * 2, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE, glowColor2);
            drawContext.drawTexture(RenderPipelines.GUI_TEXTURED, MARKER_TEXTURE, ix - glowPad1, iy - glowPad1, 0.0F, 0.0F, iconSize + glowPad1 * 2, iconSize + glowPad1 * 2, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE, MARKER_TEXTURE_SIZE, glowColor1);

            drawContext.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    MARKER_TEXTURE,
                    ix,
                    iy,
                    0.0F,
                    0.0F,
                    iconSize,
                    iconSize,
                    MARKER_TEXTURE_SIZE,
                    MARKER_TEXTURE_SIZE,
                    MARKER_TEXTURE_SIZE,
                    MARKER_TEXTURE_SIZE,
                    iconColor
            );

            // Tiny orbit particles around the icon (also tier-colored).
            int centerX = ix + iconSize / 2;
            int centerY = iy + iconSize / 2;
            float radius = iconSize * 0.66F;
            for (int p = 0; p < 4; p++) {
                float ang = time * 0.12F + marker.tier * 0.5F + p * 1.5707964F;
                int px = Math.round(centerX + MathHelper.cos(ang) * radius);
                int py = Math.round(centerY + MathHelper.sin(ang) * radius * 0.72F);
                int sparkAlpha = Math.round((95 + 70 * (0.5F + 0.5F * MathHelper.sin(ang * 1.7F))) * iconAlpha);
                int sparkColor = (MathHelper.clamp(sparkAlpha, 30, 255) << 24) | (style.mainColor() & 0x00FFFFFF);
                drawContext.fill(px - 1, py - 1, px + 2, py + 2, sparkColor);
            }

            String label = "T" + style.tier;
            int tw = client.textRenderer.getWidth(label);
            int tx = Math.round(sx - tw / 2.0F);
            int ty = iy + iconSize + 2;
            drawContext.fill(tx - 2, ty - 1, tx + tw + 2, ty + 9, 0xA0000000);
            drawContext.drawText(client.textRenderer, label, tx, ty, color, true);
        }
    }

    public record Marker(BlockPos pos, int tier) {
    }
}
