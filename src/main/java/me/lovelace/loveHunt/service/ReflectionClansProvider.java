package me.lovelace.loveHunt.service;

import me.lovelace.loveHunt.api.LoveHuntClans;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Связка с кланами через рефлексию — LoveHunt не собирается против LoveClans.
 *
 * Раньше провайдер искал плагин с именем «Clans» и наугад дёргал методы
 * getClanTag/getPlayerClanTag/getClan. Плагин кланов называется LoveClans, и таких методов
 * у него нет: isAvailable() всегда возвращал false, поэтому клановые розыски не работали
 * вовсе. Теперь обращаемся к настоящему API — {@code me.lovelace.loveclans.api.LoveClansAPI}.
 */
public final class ReflectionClansProvider implements LoveHuntClans {

    private static final String API_CLASS = "me.lovelace.loveclans.api.LoveClansAPI";

    private final Plugin clansPlugin;

    public ReflectionClansProvider() {
        this.clansPlugin = Bukkit.getPluginManager().getPlugin("LoveClans");
    }

    @Override
    public boolean isAvailable() {
        return clansPlugin != null && clansPlugin.isEnabled() && api() != null;
    }

    @Override
    public String getClanTag(UUID playerUuid) {
        Object clan = clanOf(playerUuid);
        return clan == null ? null : string(clan, "tag");
    }

    @Override
    public Collection<UUID> getClanMembers(String clanTag) {
        Object clan = clanByTag(clanTag);
        if (clan == null) {
            return List.of();
        }
        try {
            Object members = invoke(clan, "members");
            if (members instanceof Map<?, ?> map) {
                return map.keySet().stream()
                        .filter(UUID.class::isInstance)
                        .map(UUID.class::cast)
                        .toList();
            }
        } catch (ReflectiveOperationException ignored) {
            // Состав клана недоступен — считаем, что членов нет.
        }
        return List.of();
    }

    @Override
    public boolean areEnemies(UUID first, UUID second) {
        Object firstClan = clanOf(first);
        Object secondClan = clanOf(second);
        if (firstClan == null || secondClan == null) {
            return false;
        }
        try {
            Object firstId = invoke(firstClan, "id");
            Object secondId = invoke(secondClan, "id");
            if (firstId == null || firstId.equals(secondId)) {
                return false;
            }
            // Вражда здесь односторонняя: клан мог объявить врагом, не будучи объявленным
            // в ответ, поэтому проверяем обе стороны.
            return isEnemyRelation(firstClan, secondId) || isEnemyRelation(secondClan, firstId);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Boolean> chargeClanTreasury(String clanTag, long amount) {
        Object api = api();
        Object clan = clanByTag(clanTag);
        if (api == null || clan == null || amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            for (Method method : api.getClass().getMethods()) {
                if (!method.getName().equals("spendChestMoneyAsync") || method.getParameterCount() != 2) {
                    continue;
                }
                Object result = method.invoke(api, clan, amount);
                if (result instanceof CompletableFuture<?> future) {
                    return future.thenApply(Boolean.TRUE::equals);
                }
            }
        } catch (ReflectiveOperationException exception) {
            // Метода нет — версия LoveClans старше, чем нужно для оплаты из казны.
        }
        return CompletableFuture.completedFuture(false);
    }

    private boolean isEnemyRelation(Object clan, Object otherClanId) throws ReflectiveOperationException {
        Method method = clan.getClass().getMethod("relationTo", UUID.class);
        method.setAccessible(true);
        Object relation = method.invoke(clan, otherClanId);
        return relation != null && "ENEMY".equals(relation.toString());
    }

    private Object clanOf(UUID playerUuid) {
        Object api = api();
        if (api == null || playerUuid == null) {
            return null;
        }
        try {
            return unwrap(api.getClass().getMethod("getPlayerClan", UUID.class).invoke(api, playerUuid));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object clanByTag(String clanTag) {
        Object api = api();
        if (api == null || clanTag == null || clanTag.isBlank()) {
            return null;
        }
        try {
            return unwrap(api.getClass().getMethod("getClanByTag", String.class).invoke(api, clanTag));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    /** Клан приходит как Optional — разворачиваем, не завися от его типа. */
    private Object unwrap(Object result) {
        return result instanceof Optional<?> optional ? optional.orElse(null) : result;
    }

    private String string(Object target, String method) {
        try {
            Object value = invoke(target, method);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private Object invoke(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private Object api() {
        try {
            return Class.forName(API_CLASS).getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
