package org.sawiq.dmc_tiersigns.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

public class TierConfigScreen extends Screen {
    private final Screen parent;

    public TierConfigScreen(Screen parent) {
        super(Text.literal("TierSigns"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TierConfig cfg = TierConfigManager.get();
        int cx = this.width / 2, W = 220, row = 22;
        int y  = this.height / 2 - 145;

        // Тиры
        for (int t = 0; t < 4; t++) {
            final int tier = t;
            addDrawableChild(ButtonWidget.builder(
                tierText(tier, cfg.isTierVisible(tier)), btn -> {
                    boolean now = !cfg.isTierVisible(tier);
                    switch (tier) {
                        case 0 -> cfg.showTier0 = now;
                        case 1 -> cfg.showTier1 = now;
                        case 2 -> cfg.showTier2 = now;
                        case 3 -> cfg.showTier3 = now;
                    }
                    btn.setMessage(tierText(tier, now));
                }).dimensions(cx - W/2, y + tier * row, W, 20).build());
        }

        int wy = y + 4 * row + 6;

        addDrawableChild(ButtonWidget.builder(onOff("Сквозь стены", cfg.wallhack), btn -> {
            cfg.wallhack = !cfg.wallhack;
            btn.setMessage(onOff("Сквозь стены", cfg.wallhack));
        }).dimensions(cx - W/2, wy, W, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Контур", cfg.outlineEnabled), btn -> {
            cfg.outlineEnabled = !cfg.outlineEnabled;
            btn.setMessage(onOff("Контур", cfg.outlineEnabled));
        }).dimensions(cx - W/2, wy + row, W, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Звук", cfg.soundEnabled), btn -> {
            cfg.soundEnabled = !cfg.soundEnabled;
            btn.setMessage(onOff("Звук", cfg.soundEnabled));
        }).dimensions(cx - W/2, wy + row*2, W, 20).build());

        addDrawableChild(ButtonWidget.builder(onOff("Предупреждение у блоков", cfg.warnOnInteract), btn -> {
            cfg.warnOnInteract = !cfg.warnOnInteract;
            btn.setMessage(onOff("Предупреждение у блоков", cfg.warnOnInteract));
        }).dimensions(cx - W/2, wy + row*3, W, 20).build());

        // Слайдеры
        addDrawableChild(new CfgSlider(cx-W/2, wy+row*4, W, 8, 96,
            cfg.scanRadius,
            v -> cfg.scanRadius = v,
            v -> "Радиус: " + v + " блоков"));

        addDrawableChild(new CfgSlider(cx-W/2, wy+row*5, W, 10, 100,
            cfg.iconOpacityPercent,
            v -> cfg.iconOpacityPercent = v,
            v -> "Прозрачность иконки: " + v + "%"));

        addDrawableChild(new CfgSlider(cx-W/2, wy+row*6, W, 0, 100,
            cfg.outlineOpacity,
            v -> cfg.outlineOpacity = v,
            v -> "Прозрачность контура: " + v + "%"));

        addDrawableChild(new CfgSlider(cx-W/2, wy+row*7, W, 1, 20,
            Math.round(cfg.toastDistance),
            v -> cfg.toastDistance = v,
            v -> "Подсказка с " + v + " блоков"));

        addDrawableChild(new CfgSlider(cx-W/2, wy+row*8, W, -20, 20,
            Math.round(cfg.iconHeightOffset * 10),
            v -> cfg.iconHeightOffset = v / 10F,
            v -> "Высота метки: " + String.format("%.1f", v / 10F)));

        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), btn -> close())
            .dimensions(cx - W/2, wy + row*9 + 4, W, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float pt) {
        ctx.fill(0, 0, width, height, 0xC0101010);
        ctx.drawCenteredTextWithShadow(textRenderer, "TierSigns", width/2, 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "§8[H] — скрыть/показать метки", width/2, 20, 0x888888);
        ctx.drawCenteredTextWithShadow(textRenderer,
            "sawiq_  |  APTYP_CABEJIEB  |  Dava_Wasab", width/2, height - 12, 0x555555);
        super.render(ctx, mx, my, pt);
    }

    @Override
    public void close() {
        TierConfigManager.save();
        if (this.client != null) this.client.setScreen(parent);
    }

    private Text tierText(int t, boolean on) {
        return Text.literal("Tier " + t + ": " + (on ? "ВКЛ" : "ВЫКЛ"));
    }

    private Text onOff(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ВКЛ" : "ВЫКЛ"));
    }

    private static class CfgSlider extends SliderWidget {
        private final int min, max;
        private final IntConsumer setter;
        private final IntFunction<String> label;

        CfgSlider(int x, int y, int w, int min, int max, int initVal,
                  IntConsumer setter, IntFunction<String> label) {
            super(x, y, w, 20,
                Text.literal(label.apply(initVal)),
                (double)(initVal - min) / (max - min));
            this.min = min; this.max = max;
            this.setter = setter; this.label = label;
        }

        private int val() {
            return MathHelper.clamp((int) Math.round(min + value * (max - min)), min, max);
        }

        @Override protected void updateMessage() { setMessage(Text.literal(label.apply(val()))); }
        @Override protected void applyValue()    { setter.accept(val()); }
    }
}
