package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.model.HunterRating;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import java.util.Locale;

public final class LoveHuntPapiExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final BountyService bountyService;

    public LoveHuntPapiExpansion(JavaPlugin plugin, BountyService bountyService) {
        this.plugin = plugin;
        this.bountyService = bountyService;
    }

    @Override
    public String getIdentifier() {
        return "lovehunt";
    }

    @Override
    public String getAuthor() {
        return "Lovelace";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null) {
            return "";
        }
        HunterRating rating = bountyService.ratings().get(offlinePlayer.getUniqueId());
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "rating" -> String.format(Locale.ROOT, "%.1f", rating.rating());
            case "rank" -> rating.rankName();
            case "rank_stars" -> rating.rankStars();
            case "reward_modifier" -> String.valueOf(
                (int) Math.round(bountyService.ratings().rewardModifierFraction(rating.rating()) * 100.0));
            case "completed" -> String.valueOf(rating.completed());
            case "failed" -> String.valueOf(rating.failed());
            case "active_count" -> String.valueOf(bountyService.allActive().size());
            case "mine_count" -> String.valueOf(bountyService.byCreator(offlinePlayer.getUniqueId()).size());
            default -> null;
        };
    }
}
