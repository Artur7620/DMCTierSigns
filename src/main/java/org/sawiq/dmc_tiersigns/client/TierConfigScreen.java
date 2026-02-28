package org.sawiq.dmc_tiersigns.client;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class TierConfigScreen extends Screen {
    private final Screen parent;

    private ButtonWidget tier0Button;
    private ButtonWidget tier1Button;
    private ButtonWidget tier2Button;
    private ButtonWidget tier3Button;

    public TierConfigScreen(Screen parent) {
        super(Text.literal("Настройки DMC TierSigns"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        TierConfig config = TierConfigManager.get();
        int centerX = this.width / 2;
        int startY = this.height / 2 - 118;
        int row = 22;

        this.tier0Button = addDrawableChild(ButtonWidget.builder(toggleText(0, config.showTier0), button -> {
            config.showTier0 = !config.showTier0;
            button.setMessage(toggleText(0, config.showTier0));
        }).dimensions(centerX - 110, startY, 220, 20).build());

        this.tier1Button = addDrawableChild(ButtonWidget.builder(toggleText(1, config.showTier1), button -> {
            config.showTier1 = !config.showTier1;
            button.setMessage(toggleText(1, config.showTier1));
        }).dimensions(centerX - 110, startY + row, 220, 20).build());

        this.tier2Button = addDrawableChild(ButtonWidget.builder(toggleText(2, config.showTier2), button -> {
            config.showTier2 = !config.showTier2;
            button.setMessage(toggleText(2, config.showTier2));
        }).dimensions(centerX - 110, startY + row * 2, 220, 20).build());

        this.tier3Button = addDrawableChild(ButtonWidget.builder(toggleText(3, config.showTier3), button -> {
            config.showTier3 = !config.showTier3;
            button.setMessage(toggleText(3, config.showTier3));
        }).dimensions(centerX - 110, startY + row * 3, 220, 20).build());

        addDrawableChild(new RadiusSlider(centerX - 110, startY + row * 4, 220, 20, config));
        addDrawableChild(new IntConfigSlider(
                centerX - 110,
                startY + row * 5,
                220,
                20,
                10,
                140,
                () -> config.hudEspHeightPercent,
                value -> config.hudEspHeightPercent = value,
                value -> "Высота ESP: " + value + "%"
        ));
        addDrawableChild(new IntConfigSlider(
                centerX - 110,
                startY + row * 6,
                220,
                20,
                10,
                120,
                () -> config.hudEspWidthPercent,
                value -> config.hudEspWidthPercent = value,
                value -> "Ширина ESP: " + value + "%"
        ));
        addDrawableChild(new IntConfigSlider(
                centerX - 110,
                startY + row * 7,
                220,
                20,
                -40,
                40,
                () -> config.hudEspYOffset,
                value -> config.hudEspYOffset = value,
                value -> "Смещение ESP по Y: " + value
        ));
        addDrawableChild(new IntConfigSlider(
                centerX - 110,
                startY + row * 8,
                220,
                20,
                10,
                100,
                () -> config.iconOpacityPercent,
                value -> config.iconOpacityPercent = value,
                value -> "Прозрачность иконки тира: " + value + "%"
        ));
        addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> close()).dimensions(centerX - 110, startY + row * 9 + 6, 220, 20).build());
    }

    @Override
    public void close() {
        TierConfigManager.save();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private Text toggleText(int tier, boolean enabled) {
        return Text.literal("Показывать тир " + tier + ": " + (enabled ? "ВКЛ" : "ВЫКЛ"));
    }

    private static class RadiusSlider extends SliderWidget {
        private final TierConfig config;

        private RadiusSlider(int x, int y, int width, int height, TierConfig config) {
            super(x, y, width, height, Text.empty(), toSliderValue(config.scanRadius));
            this.config = config;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal("Радиус сканирования: " + this.config.scanRadius + " блоков"));
        }

        @Override
        protected void applyValue() {
            int radius = fromSliderValue(this.value);
            this.config.scanRadius = radius;
            updateMessage();
        }

        private static double toSliderValue(int radius) {
            return (radius - 8) / 88.0;
        }

        private static int fromSliderValue(double value) {
            return MathHelper.clamp((int) Math.round(8 + value * 88), 8, 96);
        }
    }

    private static class IntConfigSlider extends SliderWidget {
        private final int min;
        private final int max;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final java.util.function.IntFunction<String> text;

        private IntConfigSlider(int x, int y, int width, int height, int min, int max, IntSupplier getter, IntConsumer setter, java.util.function.IntFunction<String> text) {
            super(x, y, width, height, Text.empty(), toSliderValue(getter.getAsInt(), min, max));
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.text = text;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(text.apply(getter.getAsInt())));
        }

        @Override
        protected void applyValue() {
            int value = fromSliderValue(this.value, min, max);
            setter.accept(value);
            updateMessage();
        }

        private static double toSliderValue(int value, int min, int max) {
            return (value - min) / (double) (max - min);
        }

        private static int fromSliderValue(double slider, int min, int max) {
            return MathHelper.clamp((int) Math.round(min + slider * (max - min)), min, max);
        }
    }
}
