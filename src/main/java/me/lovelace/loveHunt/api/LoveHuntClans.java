package me.lovelace.loveHunt.api;

import java.util.Collection;
import java.util.UUID;

public interface LoveHuntClans {
    boolean isAvailable();

    String getClanTag(UUID playerUuid);

    Collection<UUID> getClanMembers(String clanTag);

    default boolean isSameClan(UUID first, UUID second) {
        String firstClan = getClanTag(first);
        String secondClan = getClanTag(second);
        return firstClan != null && firstClan.equalsIgnoreCase(secondClan);
    }
}
