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

    /**
     * Ранг охотника по рейтингу — то, что игрок видит вместо голого числа.
     * Границы намеренно совпадают с порогами награды по умолчанию
     * ({@code economy.rating-*-threshold}), чтобы «Мастер» означал ровно то же,
     * что и надбавка к награде.
     */
    public String rankName() {
        if (rating >= 4.0) return "Мастер";
        if (rating >= 3.0) return "Опытный";
        if (rating >= 1.0) return "Охотник";
        return "Новичок";
    }

    /** Звёзды ранга для подсказок в меню: закрашенные по рейтингу, остальные пустые. */
    public String rankStars() {
        int filled = (int) Math.round(rating);
        return "★".repeat(Math.max(0, filled)) + "☆".repeat(Math.max(0, 5 - filled));
    }
}
