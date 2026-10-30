package me.lovelace.loveHunt.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeadDetailsTest {
    @Test
    void encodesAndDecodesBack() {
        var details = new HeadDetails("PROJECTILE", "BOW", 18.5, 4000L, 86_400_000L);
        assertEquals("cause=PROJECTILE;weapon=BOW;armor=18.5;reward=4000;hunted=86400000", details.encode());
        assertEquals(details, HeadDetails.decode(details.encode()));
    }

    @Test
    void missingOrDamagedTextGivesNeutralValues() {
        assertEquals(HeadDetails.UNKNOWN, HeadDetails.decode(null));
        assertEquals(HeadDetails.UNKNOWN, HeadDetails.decode(""));
        assertEquals(HeadDetails.UNKNOWN, HeadDetails.decode("garbage;;=;x=1"));
        var partly = HeadDetails.decode("cause=FALL;armor=oops;reward=-5;hunted=abc");
        assertEquals("FALL", partly.cause());
        assertEquals(0.0, partly.armor());
        assertEquals(0L, partly.reward());
        assertEquals(0L, partly.hunted());
        assertEquals("FIST", partly.weapon());
    }

    @Test
    void namesAreCleanedSoTheyCannotBreakTheFormat() {
        var details = new HeadDetails("fall;weapon=X", "bo w", 1.0, 0L, 0L);
        assertEquals("FALLWEAPONX", details.cause());
        assertEquals("BOW", details.weapon());
        assertEquals(details, HeadDetails.decode(details.encode()));
    }

    @Test
    void withHuntKeepsTheDeathPart() {
        var details = new HeadDetails("FALL", "FIST", 10.0, 0L, 0L).withHunt(500L, 1000L);
        assertEquals("FALL", details.cause());
        assertEquals(500L, details.reward());
        assertEquals(1000L, details.hunted());
    }
}
