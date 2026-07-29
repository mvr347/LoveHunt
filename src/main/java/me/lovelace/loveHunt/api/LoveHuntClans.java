package me.lovelace.loveHunt.api;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LoveHuntClans {
    boolean isAvailable();

    String getClanTag(UUID playerUuid);

    Collection<UUID> getClanMembers(String clanTag);

    /**
     * Враждуют ли кланы этих игроков. Нужно клановому розыску: контракт из общей казны
     * выписывается на врага, а не на случайного прохожего.
     */
    default boolean areEnemies(UUID first, UUID second) {
        return false;
    }

    /**
     * Списывает сумму из казны клана. Возвращает future с false, если денег не хватило
     * или плагин кланов не поддерживает оплату из казны.
     */
    default CompletableFuture<Boolean> chargeClanTreasury(String clanTag, long amount) {
        return CompletableFuture.completedFuture(false);
    }

    default boolean isSameClan(UUID first, UUID second) {
        String firstClan = getClanTag(first);
        String secondClan = getClanTag(second);
        return firstClan != null && firstClan.equalsIgnoreCase(secondClan);
    }
}
