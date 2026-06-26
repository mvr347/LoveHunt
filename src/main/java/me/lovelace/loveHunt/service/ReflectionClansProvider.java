package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.api.LoveHuntClans;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ReflectionClansProvider implements LoveHuntClans {
    private final Plugin clansPlugin;

    public ReflectionClansProvider() {
        this.clansPlugin = Bukkit.getPluginManager().getPlugin("Clans");
    }

    @Override
    public boolean isAvailable() {
        return clansPlugin != null && clansPlugin.isEnabled();
    }

    @Override
    public String getClanTag(UUID playerUuid) {
        if (!isAvailable() || playerUuid == null) {
            return null;
        }
        Object result = invokeFirst(clansPlugin, List.of("getClanTag", "getPlayerClanTag", "getClan"), playerUuid);
        if (result == null) {
            return null;
        }
        if (result instanceof String value) {
            return value;
        }
        Object tag = invokeFirst(result, List.of("getTag", "tag", "getName", "name"));
        return tag == null ? result.toString() : tag.toString();
    }

    @Override
    public Collection<UUID> getClanMembers(String clanTag) {
        if (!isAvailable() || clanTag == null) {
            return List.of();
        }
        Object result = invokeFirst(clansPlugin, List.of("getClanMembers", "getMembers"), clanTag);
        if (result instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(UUID.class::isInstance)
                    .map(UUID.class::cast)
                    .toList();
        }
        return List.of();
    }

    private Object invokeFirst(Object target, List<String> names, Object... args) {
        for (String name : names) {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                    continue;
                }
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }
}
