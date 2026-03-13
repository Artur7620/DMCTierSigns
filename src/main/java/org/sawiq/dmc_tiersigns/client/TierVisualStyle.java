package org.sawiq.dmc_tiersigns.client;

import net.minecraft.util.Identifier;

public enum TierVisualStyle {
    //           tier  rgb(иконка)  accentRgb   alpha  core  glow  pulseSpd pulseAmp  label        sub             toastColor(полоска)
    TIER_0(0, 0xFF1E1E, 0xFFE1E1, 0.82F, 0.26F, 3.90F, 0.42F, 0.34F, "TIER 0", "ABSOLUTE LOCKDOWN",  0x333333),
    TIER_1(1, 0xFF2020, 0xFFAAAA, 0.72F, 0.24F, 3.30F, 0.34F, 0.28F, "TIER 1", "CRITICAL RESTRICTION",0xFF2020),
    TIER_2(2, 0xFFCC00, 0xFFEE88, 0.60F, 0.22F, 2.70F, 0.28F, 0.22F, "TIER 2", "CONTROLLED ZONE",    0xFFCC00),
    TIER_3(3, 0x20FF40, 0xAAFFBB, 0.48F, 0.20F, 2.20F, 0.22F, 0.16F, "TIER 3", "PRIVATE TERRITORY",  0x20FF40);

    public final int    tier;
    public final int    rgb;
    public final int    accentRgb;
    public final float  alpha;
    public final float  coreScale;
    public final float  glowScale;
    public final float  pulseSpeed;
    public final float  pulseAmplitude;
    public final String labelMain;
    public final String labelSub;
    public final int    toastColor;   // цвет полоски и текста в подсказке
    public final Identifier texture;

    TierVisualStyle(int tier, int rgb, int accentRgb, float alpha, float coreScale, float glowScale,
                    float pulseSpeed, float pulseAmplitude, String labelMain, String labelSub,
                    int toastColor) {
        this.tier = tier; this.rgb = rgb; this.accentRgb = accentRgb;
        this.alpha = alpha; this.coreScale = coreScale; this.glowScale = glowScale;
        this.pulseSpeed = pulseSpeed; this.pulseAmplitude = pulseAmplitude;
        this.labelMain = labelMain; this.labelSub = labelSub;
        this.toastColor = toastColor;
        this.texture = Identifier.of("dmc_tiersigns", "textures/gui/tier" + tier + ".png");
    }

    public static TierVisualStyle byTier(int tier) {
        return switch (tier) { case 0->TIER_0; case 1->TIER_1; case 2->TIER_2; default->TIER_3; };
    }

    public float red()       { return ((rgb >> 16) & 255) / 255.0F; }
    public float green()     { return ((rgb >>  8) & 255) / 255.0F; }
    public float blue()      { return  (rgb        & 255) / 255.0F; }
    public int mainColor()   { return rgb; }
    public int accentColor() { return accentRgb; }
}
