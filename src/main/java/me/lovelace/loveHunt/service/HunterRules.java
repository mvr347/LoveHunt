package me.lovelace.loveHunt.service;

/** Pure rules of the hunter pass (no Bukkit types), so they can be unit-tested. */
public final class HunterRules {
    private HunterRules() {
    }

    /**
     * Pass level for a number of completed bounties. {@code thresholds[i]} is the completed count that
     * reaches level {@code i + 1}; level 1 needs {@code thresholds[0]} (normally 0).
     */
    public static int levelFor(int completed, int[] thresholds) {
        int level = 1;
        for (int i = 0; i < thresholds.length; i++) {
            if (completed >= thresholds[i]) level = i + 1;
        }
        return level;
    }

    /** Tax rate (0..1) for a level; the last entry applies to every higher level. */
    public static double taxFor(int level, double[] rates) {
        if (rates.length == 0) return 0.0;
        int index = Math.max(0, Math.min(rates.length - 1, level - 1));
        return rates[index];
    }

    /** Payout after tax, rounded to whole units and never below 1 for a positive amount. */
    public static long afterTax(long amount, double rate) {
        if (amount <= 0) return amount;
        return Math.max(1L, Math.round(amount * (1.0 - rate)));
    }

    /**
     * Idle time to add for one upkeep tick: the time since the previous tick, but only while a bounty was
     * open, and capped so that a server pause or restart does not count as hunting downtime.
     */
    public static long idleDelta(long now, long lastTickAt, boolean bountiesOpen, long capMillis) {
        if (!bountiesOpen || lastTickAt <= 0L) return 0L;
        return Math.max(0L, Math.min(now - lastTickAt, capMillis));
    }

    /** The pass is dropped when the hunter took nothing for {@code resetMillis} of eligible (bounties open) time. */
    public static boolean shouldReset(long idleMillis, long resetMillis) {
        return idleMillis >= resetMillis;
    }

    /** A rarely active hunter loses one level each time {@code decayMillis} of eligible idle time passes. */
    public static boolean shouldDecay(int level, long levelIdleMillis, long decayMillis) {
        return level > 1 && levelIdleMillis >= decayMillis;
    }

    /** Completed count that matches the start of {@code level}: a decayed hunter restarts the climb from there. */
    public static int completedAtLevelStart(int level, int[] thresholds) {
        if (level <= 1 || thresholds.length == 0) return 0;
        return thresholds[Math.min(level, thresholds.length) - 1];
    }
}
