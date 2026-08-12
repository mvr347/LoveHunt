package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.service.BountyService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PendingRewardListener implements Listener {
    private final BountyService bountyService;

    public PendingRewardListener(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isAuthenticated(event.getPlayer())) {
            bountyService.deliverPendingRewards(event.getPlayer());
        }
    }

    @EventHandler
    public void onAuthenticated(dev.lovelace.lovecore.api.auth.PlayerAuthenticatedEvent event) {
        bountyService.deliverPendingRewards(event.player());
    }

    /**
     * Не кэшируем Optional<AuthOracle> — сосед может зарегистрировать реализацию позже,
     * см. LoveCore.service(...) javadoc в LoveCore. Если LoveAuth не установлен, награды
     * выдаются сразу на join, как и раньше.
     */
    private boolean isAuthenticated(Player player) {
        return dev.lovelace.lovecore.api.LoveCore.service(dev.lovelace.lovecore.api.auth.AuthOracle.class)
                .map(oracle -> oracle.isAuthenticated(player.getUniqueId()))
                .orElse(true);
    }
}
