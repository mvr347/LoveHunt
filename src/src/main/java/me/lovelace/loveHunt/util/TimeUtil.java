package me.lovelace.loveHunt.util;

import java.time.Duration;

public final class TimeUtil {
    private TimeUtil() {
    }

    public static String compact(long millis) {
        if (millis <= 0) {
            return "0с";
        }
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        if (days > 0) {
            return days + "д " + duration.minusDays(days).toHours() + "ч";
        }
        long hours = duration.toHours();
        if (hours > 0) {
            return hours + "ч " + duration.minusHours(hours).toMinutes() + "м";
        }
        long minutes = duration.toMinutes();
        if (minutes > 0) {
            return minutes + "м";
        }
        return duration.toSeconds() + "с";
    }
}
