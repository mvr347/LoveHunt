package me.lovelace.loveHunt.model;

import java.util.UUID;

/** A player who holds the hunter pass. Times are epoch millis. */
public record Hunter(UUID uuid, int level, int completed, long joinedAt, long lastAcceptAt, long levelCheckedAt) {

    public static Hunter joined(UUID uuid, long now) {
        return new Hunter(uuid, 1, 0, now, 0L, now);
    }

    /** The last time the hunter did anything with bounties; a fresh pass counts from its purchase. */
    public long activityAt() {
        return Math.max(joinedAt, lastAcceptAt);
    }

    public Hunter withAccept(long now) {
        return new Hunter(uuid, level, completed, joinedAt, now, levelCheckedAt);
    }

    public Hunter withLevel(int newLevel, int newCompleted, long now) {
        return new Hunter(uuid, newLevel, newCompleted, joinedAt, lastAcceptAt, now);
    }
}
