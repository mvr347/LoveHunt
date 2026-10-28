package me.lovelace.loveHunt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HunterRulesTest {
    private static final int[] THRESHOLDS = {0, 3, 8, 15, 25};
    private static final double[] TAX = {0.20, 0.16, 0.12, 0.08, 0.04};
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    void levelGrowsWithCompletedBounties() {
        assertEquals(1, HunterRules.levelFor(0, THRESHOLDS));
        assertEquals(1, HunterRules.levelFor(2, THRESHOLDS));
        assertEquals(2, HunterRules.levelFor(3, THRESHOLDS));
        assertEquals(4, HunterRules.levelFor(15, THRESHOLDS));
        assertEquals(5, HunterRules.levelFor(500, THRESHOLDS));
    }

    @Test
    void taxFallsWithLevelAndLastRateAppliesAbove() {
        assertEquals(0.20, HunterRules.taxFor(1, TAX), 1e-9);
        assertEquals(0.04, HunterRules.taxFor(5, TAX), 1e-9);
        assertEquals(0.04, HunterRules.taxFor(9, TAX), 1e-9);
        assertEquals(0.0, HunterRules.taxFor(1, new double[0]), 1e-9);
    }

    @Test
    void payoutIsReducedByTaxAndNeverBelowOne() {
        assertEquals(1600, HunterRules.afterTax(2000, 0.20));
        assertEquals(1, HunterRules.afterTax(1, 0.20));
        assertEquals(0, HunterRules.afterTax(0, 0.20));
    }

    @Test
    void idleTimeCountsOnlyWhileBountiesAreOpen() {
        long cap = 5 * 60_000L;
        assertEquals(60_000L, HunterRules.idleDelta(120_000L, 60_000L, true, cap));
        assertEquals(0L, HunterRules.idleDelta(120_000L, 60_000L, false, cap), "empty server: nothing counts");
        assertEquals(0L, HunterRules.idleDelta(120_000L, 0L, true, cap), "first tick has no previous tick");
        assertEquals(cap, HunterRules.idleDelta(10 * DAY, 1_000L, true, cap), "a server pause is capped");
    }

    @Test
    void passResetsAfterFiveDaysOfEligibleIdleTime() {
        assertFalse(HunterRules.shouldReset(5 * DAY - 1, 5 * DAY));
        assertTrue(HunterRules.shouldReset(5 * DAY, 5 * DAY));
    }

    @Test
    void levelDecaysEachWindowOfEligibleIdleTimeAndNeverBelowOne() {
        assertFalse(HunterRules.shouldDecay(3, 2 * DAY - 1, 2 * DAY));
        assertTrue(HunterRules.shouldDecay(3, 2 * DAY, 2 * DAY));
        assertFalse(HunterRules.shouldDecay(1, 9 * DAY, 2 * DAY), "level 1 cannot drop");
    }

    @Test
    void decayedHunterRestartsFromTheStartOfTheLowerLevel() {
        assertEquals(0, HunterRules.completedAtLevelStart(1, THRESHOLDS));
        assertEquals(3, HunterRules.completedAtLevelStart(2, THRESHOLDS));
        assertEquals(15, HunterRules.completedAtLevelStart(4, THRESHOLDS));
    }
}
