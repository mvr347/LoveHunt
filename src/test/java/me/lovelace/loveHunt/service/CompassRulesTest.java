package me.lovelace.loveHunt.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompassRulesTest {
    private static final double[] RADII = {80, 55, 35, 20, 8};

    @Test
    void radiusShrinksWithLevelAndLastEntryCoversHigherLevels() {
        assertEquals(80, CompassRules.radiusFor(1, RADII));
        assertEquals(35, CompassRules.radiusFor(3, RADII));
        assertEquals(8, CompassRules.radiusFor(5, RADII));
        assertEquals(8, CompassRules.radiusFor(9, RADII));
        assertEquals(80, CompassRules.radiusFor(0, RADII));
        assertEquals(0, CompassRules.radiusFor(1, new double[0]));
    }

    @Test
    void nearTargetIsNeverLost() {
        assertEquals(80, CompassRules.effectiveRadius(80, 1000), 1e-9);
        assertEquals(0.35 * 100, CompassRules.effectiveRadius(80, 100), 1e-9);
        assertEquals(0, CompassRules.effectiveRadius(80, 0));
        assertEquals(0, CompassRules.effectiveRadius(0, 500));
    }

    @Test
    void offsetStaysInsideTheDiscAndIsStable() {
        for (long seed = 0; seed < 2000; seed++) {
            double[] shift = CompassRules.offset(seed, 35);
            assertTrue(Math.hypot(shift[0], shift[1]) <= 35 + 1e-9);
        }
        assertEquals(CompassRules.offset(42, 20)[0], CompassRules.offset(42, 20)[0]);
        assertEquals(0, CompassRules.offset(7, 0)[0]);
    }

    @Test
    void offsetIsNotBiasedToOneSide() {
        double sumX = 0, sumZ = 0;
        int n = 20000;
        for (long seed = 0; seed < n; seed++) {
            double[] shift = CompassRules.offset(CompassRules.seed(seed * 31, seed, seed, 1), 50);
            sumX += shift[0];
            sumZ += shift[1];
        }
        assertEquals(0, sumX / n, 1.5);
        assertEquals(0, sumZ / n, 1.5);
    }

    @Test
    void seedHoldsInsideAWindowAndChangesBetweenWindows() {
        long a = CompassRules.seed(11, 5, 1_000, 90_000);
        assertEquals(a, CompassRules.seed(11, 5, 89_999, 90_000));
        assertNotEquals(a, CompassRules.seed(11, 5, 90_000, 90_000));
        assertNotEquals(a, CompassRules.seed(12, 5, 1_000, 90_000));
        assertNotEquals(a, CompassRules.seed(11, 6, 1_000, 90_000));
    }
}
