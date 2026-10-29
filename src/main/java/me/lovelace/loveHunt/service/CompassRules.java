package me.lovelace.loveHunt.service;

import java.util.SplittableRandom;

/** Pure rules of the hunter's sense (no Bukkit types), so they can be unit-tested. */
public final class CompassRules {
    private CompassRules() {
    }

    /** Fraction of the distance to the target that the error may take at most: a close target is never lost. */
    public static final double MAX_SHARE_OF_DISTANCE = 0.35;

    /** Error radius in blocks for a ticket level; the last entry applies to every higher level. */
    public static double radiusFor(int level, double[] radii) {
        if (radii.length == 0) return 0.0;
        int index = Math.max(0, Math.min(radii.length - 1, level - 1));
        return radii[index];
    }

    /** The radius actually used: the closer the target, the smaller the error, so the last steps are exact. */
    public static double effectiveRadius(double radius, double distance) {
        if (!(radius > 0.0) || !(distance > 0.0)) return 0.0;
        return Math.min(radius, distance * MAX_SHARE_OF_DISTANCE);
    }

    /**
     * Offset (x, z) of the false point, uniform inside a disc of {@code radius}. It depends only on the seed, so
     * it stays put between refreshes and averaging many readings of the compass does not find the real spot.
     */
    public static double[] offset(long seed, double radius) {
        if (!(radius > 0.0)) return new double[] {0.0, 0.0};
        SplittableRandom random = new SplittableRandom(seed);
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = radius * Math.sqrt(random.nextDouble());
        return new double[] {Math.cos(angle) * distance, Math.sin(angle) * distance};
    }

    /** Seed for one hunter, one bounty and one refresh window. */
    public static long seed(long hunterBits, long bountyId, long now, long refreshMillis) {
        long bucket = refreshMillis <= 0L ? 0L : now / refreshMillis;
        long mixed = hunterBits * 0x9E3779B97F4A7C15L + bountyId * 0xC2B2AE3D27D4EB4FL + bucket * 0x165667B19E3779F9L;
        return mixed ^ (mixed >>> 31);
    }
}
