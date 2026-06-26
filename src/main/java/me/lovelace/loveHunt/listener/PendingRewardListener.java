package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.service.BountyService;
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
        bountyService.deliverPendingRewards(event.getPlayer());
    }
}
