package me.lovelace.loveHunt.model;

import java.util.UUID;

/**
 * A player who holds the hunter pass. {@code idleMillis} is the time since the last accepted bounty during
 * which at least one bounty was open (empty-server time is not counted); {@code levelIdleMillis} is the same
 * counter for the level decay window and restarts after every decay.
 */
public record Hunter(UUID uuid, int level, int completed, long joinedAt, long idleMillis, long levelIdleMillis) {

    public static Hunter joined(UUID uuid, long now) {
        return new Hunter(uuid, 1, 0, now, 0L, 0L);
    }

    public Hunter withAccept() {
        return new Hunter(uuid, level, completed, joinedAt, 0L, 0L);
    }

    public Hunter withIdle(long delta) {
        return new Hunter(uuid, level, completed, joinedAt, idleMillis + delta, levelIdleMillis + delta);
    }

    public Hunter withLevel(int newLevel, int newCompleted) {
        return new Hunter(uuid, newLevel, newCompleted, joinedAt, idleMillis, 0L);
    }
}
