package me.lovelace.loveHunt.util;

/**
 * Progress bar of the GUI lore standard: 16 cells of {@code █} in dark-gray brackets with a percentage. The result is a
 * MiniMessage fragment that is dropped into a lang placeholder ({@code {bar}}).
 */
public final class LoreBar {

    public static final int CELLS = 16;

    private LoreBar() {
    }

    /** Gold while filling, green once complete. */
    public static String bar(long current, long max) {
        return bar(current, max, percent(current, max) >= 100 ? "green" : "gold");
    }

    public static String bar(long current, long max, String fillColor) {
        int percent = percent(current, max);
        int filled = (int) Math.round(CELLS * (percent / 100.0));
        return "<dark_gray>[<" + fillColor + ">" + "█".repeat(filled) + "</" + fillColor + ">"
                + "<dark_gray>" + "█".repeat(CELLS - filled) + "]</dark_gray> "
                + "<dark_gray>(<" + fillColor + ">" + percent + "%</" + fillColor + "><dark_gray>)";
    }

    /** Whole percent clamped to 0..100; an empty or negative maximum counts as 0. */
    public static int percent(long current, long max) {
        if (max <= 0 || current <= 0) {
            return 0;
        }
        return (int) Math.min(100, current * 100 / max);
    }
}
