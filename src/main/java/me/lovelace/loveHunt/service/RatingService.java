package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.database.DatabaseManager;
import me.lovelace.loveHunt.model.HunterRating;
import me.lovelace.loveHunt.model.PlayerLock;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks hunter rating (0-5 scale, default 2.5) and per-player create/accept lock flags.
 * Rating moves +0.1 per completed bounty, -0.1 per bounty accepted then lost (cancelled by
 * the creator, expired, or the target outlasted it), clamped to [0, 5].
 */
public final class RatingService {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final DatabaseManager database;

    private final ConcurrentHashMap<UUID, HunterRating> ratings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerLock> locks = new ConcurrentHashMap<>();

    public RatingService(JavaPlugin plugin, Settings settings, DatabaseManager database) {
        this.plugin = plugin;
        this.settings = settings;
        this.database = database;
    }

    public CompletableFuture<Void> load() {
        CompletableFuture<Void> ratingsFuture = database.loadRatings().thenAccept(loaded -> {
            ratings.clear();
            ratings.putAll(loaded);
        });
        CompletableFuture<Void> locksFuture = database.loadLocks().thenAccept(loaded -> {
            locks.clear();
            locks.putAll(loaded);
        });
        return CompletableFuture.allOf(ratingsFuture, locksFuture);
    }

    public HunterRating get(UUID uuid) {
        return ratings.getOrDefault(uuid, HunterRating.defaultFor(uuid));
    }

    public void recordCompletion(UUID uuid) {
        HunterRating updated = get(uuid).withCompletion();
        ratings.put(uuid, updated);
        database.upsertRating(updated);
    }

    public void recordFailure(UUID uuid) {
        HunterRating updated = get(uuid).withFailure();
        ratings.put(uuid, updated);
        database.upsertRating(updated);
    }

    /**
     * Reward multiplier applied on top of the base reward amount when a hunter with this
     * rating completes a bounty. Positive above settings.ratingBonusThreshold (scaled linearly
     * to ratingBonusCapPercent at rating 5.0), negative at/below ratingPenaltyThreshold (scaled
     * linearly to -ratingPenaltyCapPercent at rating 0.0), zero in between.
     */
    public double rewardModifierFraction(double rating) {
        double bonusThreshold = settings.ratingBonusThreshold();
        double penaltyThreshold = settings.ratingPenaltyThreshold();
        if (rating >= bonusThreshold && HunterRating.MAX_RATING > bonusThreshold) {
            double span = HunterRating.MAX_RATING - bonusThreshold;
            double fraction = (rating - bonusThreshold) / span;
            double capped = Math.min(1.0, fraction) * (settings.ratingBonusCapPercent() / 100.0);
            return capped;
        }
        if (rating <= penaltyThreshold && penaltyThreshold > HunterRating.MIN_RATING) {
            double span = penaltyThreshold - HunterRating.MIN_RATING;
            double fraction = (penaltyThreshold - rating) / span;
            double capped = Math.min(1.0, fraction) * (settings.ratingPenaltyCapPercent() / 100.0);
            return -capped;
        }
        return 0.0;
    }

    /**
     * Applies rewardModifierFraction to a base amount, rounding in the hunter's favor either
     * way (bonus rounds up, penalty rounds down so less is withheld).
     */
    public int applyRewardModifier(int baseAmount, double rating) {
        double modifier = rewardModifierFraction(rating);
        if (modifier == 0.0) {
            return baseAmount;
        }
        double delta = baseAmount * modifier;
        long rounded = modifier > 0 ? (long) Math.ceil(delta) : (long) Math.floor(delta);
        long result = baseAmount + rounded;
        return (int) Math.max(0, result);
    }

    /**
     * Не даёт брать контракты охотнику, который завалил их подряд столько раз, что
     * рейтинг упал до порога. Состояние вычисляется из рейтинга, а не хранится
     * отдельным флагом: провалы его роняют, выполненные контракты поднимают, поэтому
     * блокировка снимается сама и не путается с ручной блокировкой от админа.
     * Порог 0 или меньше отключает проверку.
     */
    public boolean isRatingTooLowToAccept(UUID uuid) {
        double threshold = settings.acceptBlockRating();
        if (threshold <= HunterRating.MIN_RATING) {
            return false;
        }
        return get(uuid).rating() <= threshold;
    }

    public boolean isCreateBlocked(UUID uuid) {
        PlayerLock lock = locks.get(uuid);
        return lock != null && lock.createBlocked();
    }

    public boolean isAcceptBlocked(UUID uuid) {
        PlayerLock lock = locks.get(uuid);
        return lock != null && lock.acceptBlocked();
    }

    public void setCreateBlocked(UUID uuid, boolean blocked) {
        updateLock(uuid, blocked, isAcceptBlocked(uuid));
    }

    public void setAcceptBlocked(UUID uuid, boolean blocked) {
        updateLock(uuid, isCreateBlocked(uuid), blocked);
    }

    private void updateLock(UUID uuid, boolean createBlocked, boolean acceptBlocked) {
        PlayerLock lock = new PlayerLock(uuid, createBlocked, acceptBlocked);
        locks.put(uuid, lock);
        database.upsertLock(lock);
    }

    public Map<UUID, HunterRating> snapshot() {
        return Map.copyOf(ratings);
    }
}
