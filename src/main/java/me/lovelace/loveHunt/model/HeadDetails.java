package me.lovelace.loveHunt.model;

import java.util.Locale;

/**
 * How a bounty target died and what the hunt was like. It rides on the trophy and then on the head handed to the
 * customer as one text value ({@code cause=PROJECTILE;weapon=BOW;armor=18.0;reward=4000;hunted=86400000}) that
 * LoveShop's Wanderer reads to price the head; the two plugins share only this text, not code.
 *
 * @param cause   Bukkit damage cause of the killing blow, or {@code UNKNOWN}
 * @param weapon  material of the weapon in the killer's hand ({@code FIST} for none) or the projectile
 * @param armor   the victim's armor points at death
 * @param reward  value of the bounty reward in the smallest coin unit, 0 when it is not coins
 * @param hunted  how long the bounty was open, in milliseconds
 */
public record HeadDetails(String cause, String weapon, double armor, long reward, long hunted) {
    public static final HeadDetails UNKNOWN = new HeadDetails("UNKNOWN", "FIST", 0.0, 0L, 0L);

    public HeadDetails {
        cause = clean(cause, "UNKNOWN");
        weapon = clean(weapon, "FIST");
        armor = Double.isFinite(armor) ? Math.max(0.0, armor) : 0.0;
        reward = Math.max(0L, reward);
        hunted = Math.max(0L, hunted);
    }

    public HeadDetails withHunt(long reward, long hunted) {
        return new HeadDetails(cause, weapon, armor, reward, hunted);
    }

    public String encode() {
        return "cause=" + cause + ";weapon=" + weapon + ";armor=" + String.format(Locale.ROOT, "%.1f", armor)
                + ";reward=" + reward + ";hunted=" + hunted;
    }

    /** Reads {@link #encode()}; unknown or damaged parts fall back to neutral values. */
    public static HeadDetails decode(String text) {
        if (text == null || text.isBlank()) {
            return UNKNOWN;
        }
        String cause = null;
        String weapon = null;
        double armor = 0.0;
        long reward = 0L;
        long hunted = 0L;
        for (String part : text.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            try {
                switch (key) {
                    case "cause" -> cause = value;
                    case "weapon" -> weapon = value;
                    case "armor" -> armor = Double.parseDouble(value);
                    case "reward" -> reward = Long.parseLong(value);
                    case "hunted" -> hunted = Long.parseLong(value);
                    default -> {
                    }
                }
            } catch (NumberFormatException ignored) {
                // A damaged number counts as zero.
            }
        }
        return new HeadDetails(cause, weapon, armor, reward, hunted);
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "");
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
