package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.api.LoveHuntClans;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class NoopClansProvider implements LoveHuntClans {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getClanTag(UUID playerUuid) {
        return null;
    }

    @Override
    public Collection<UUID> getClanMembers(String clanTag) {
        return List.of();
    }
}
