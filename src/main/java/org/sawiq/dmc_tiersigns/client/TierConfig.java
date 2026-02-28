package org.sawiq.dmc_tiersigns.client;

public class TierConfig {
    public boolean showTier0 = true;
    public boolean showTier1 = true;
    public boolean showTier2 = true;
    public boolean showTier3 = true;
    public int scanRadius = 48;
    public int hudEspHeightPercent = 40;
    public int hudEspWidthPercent = 40;
    public int hudEspYOffset = 4;
    public int iconOpacityPercent = 72;

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
