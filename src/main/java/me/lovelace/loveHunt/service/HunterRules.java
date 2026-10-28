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
     * The pass is dropped when the hunter took nothing for {@code resetMillis} even though there was
     * something to take: some bounty was open at or after the hunter's last activity. Idle servers do
     * not punish anyone.
     */
    public static boolean shouldReset(long now, long activityAt, long resetMillis, long lastOpenBountyAt) {
        return now - activityAt > resetMillis && lastOpenBountyAt > activityAt;
    }

    /** Whether a rarely active hunter loses one level now (checked at most once per {@code decayMillis}). */
    public static boolean shouldDecay(long now, long activityAt, long levelCheckedAt, long decayMillis, int level, long lastOpenBountyAt) {
        return level > 1
                && now - activityAt > decayMillis
                && now - levelCheckedAt > decayMillis
                && lastOpenBountyAt > activityAt;
    }

    /** Completed count that matches the start of {@code level}: a decayed hunter restarts the climb from there. */
    public static int completedAtLevelStart(int level, int[] thresholds) {
        if (level <= 1 || thresholds.length == 0) return 0;
        return thresholds[Math.min(level, thresholds.length) - 1];
    }
}
