package me.lovelace.loveHunt.service;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.achievements.Achievements;

import java.util.UUID;

/**
 * Bridge to LoveCore's {@link Achievements}. Looked up on every call; an older LoveCore without the contract
 * ({@link LinkageError}) or no service simply means the achievement is not recorded.
 */
public final class AchievementsHook {
    private AchievementsHook() {
    }

    /** Unlocks an achievement; false when it was already unlocked, unknown, or there is no service. Main thread only. */
    public static boolean unlock(UUID player, String id) {
        try {
            return LoveCore.service(Achievements.class).map(service -> service.unlock(player, id)).orElse(false);
        } catch (LinkageError missingContract) {
            return false;
        }
    }
}
