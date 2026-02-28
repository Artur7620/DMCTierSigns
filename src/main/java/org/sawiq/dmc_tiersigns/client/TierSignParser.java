package org.sawiq.dmc_tiersigns.client;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TierSignParser {
    private static final Pattern TIER_PATTERN = Pattern.compile("(?:TIER|\\u0422\\u0418\\u0420)\\s*([0-3])");

    private TierSignParser() {
    }

    public static int parseTier(String text) {
        String normalized = normalize(text);
        Matcher matcher = TIER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String normalize(String input) {
        String upper = input.toUpperCase(Locale.ROOT).replace('\u0401', '\u0415');
        return upper.replaceAll("[^A-Z\\u0410-\\u042F0-9]", "");
    }
}