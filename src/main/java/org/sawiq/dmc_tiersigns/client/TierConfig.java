package org.sawiq.dmc_tiersigns.client;

public class TierConfig {
    public boolean showTier0 = true;
    public boolean showTier1 = true;
    public boolean showTier2 = true;
    public boolean showTier3 = true;
    public int scanRadius = 48;
    public int iconOpacityPercent = 90;
    public boolean wallhack = false;
    public boolean fadeWhenClose = true;
    public float fadeDistance = 3.5F;
    public float toastDistance = 4.0F;
    public boolean outlineEnabled = true;
    public int outlineOpacity = 70;
    public boolean soundEnabled = true;
    public boolean hiddenMode = false;
    public boolean warnOnInteract = false;
    public float iconHeightOffset = 0.0F;
    public float warnRadius = 5.0F;

    public boolean isTierVisible(int tier) {
        return switch (tier) {
            case 0 -> showTier0;
            case 1 -> showTier1;
            case 2 -> showTier2;
            case 3 -> showTier3;
            default -> false;
        };
    }
}
