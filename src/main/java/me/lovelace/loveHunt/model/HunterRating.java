package me.lovelace.loveHunt.model;

import java.util.UUID;

public record HunterRating(UUID uuid, double rating, int completed, int failed) {

    public static final double DEFAULT_RATING = 2.5;
    public static final double MIN_RATING = 0.0;
    public static final double MAX_RATING = 5.0;

    public static HunterRating defaultFor(UUID uuid) {
        return new HunterRating(uuid, DEFAULT_RATING, 0, 0);
    }

    public HunterRating withCompletion() {
        return new HunterRating(uuid, Math.min(MAX_RATING, rating + 0.1), completed + 1, failed);
    }

    public HunterRating withFailure() {
        return new HunterRating(uuid, Math.max(MIN_RATING, rating - 0.1), completed, failed + 1);
    }
}
