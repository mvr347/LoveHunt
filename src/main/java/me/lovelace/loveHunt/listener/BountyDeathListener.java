package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.util.HeadUtil;
import me.lovelace.loveHunt.api.event.BountyClaimEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public final class BountyDeathListener implements Listener {
    private final BountyService bountyService;
    private final Lang lang;

    public BountyDeathListener(BountyService bountyService, Lang lang) {
        this.bountyService = bountyService;
        this.lang = lang;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || victim.getUniqueId() == null || killer.getUniqueId() == null) {
            return;
        }

        Bounty bountyOnVictim = bountyService.activeOn(victim.getUniqueId());
        if (bountyOnVictim != null && bountyService.hasAccepted(killer.getUniqueId(), bountyOnVictim.id())) {
            BountyClaimEvent claimEvent = new BountyClaimEvent(bountyOnVictim, killer);
            Bukkit.getPluginManager().callEvent(claimEvent);
            if (claimEvent.isCancelled()) {
                return;
            }
            bountyService.complete(bountyOnVictim, killer);
            ItemStack head = HeadUtil.playerHead(victim);
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);
            return;
        }

        Set<Long> acceptedByVictim = bountyService.acceptedBy(victim.getUniqueId());
        for (long bountyId : acceptedByVictim) {
            Bounty accepted = bountyService.get(bountyId);
            if (accepted == null || accepted.type() != BountyType.PLAYER) {
                continue;
            }
            if (accepted.targetUuid().equals(killer.getUniqueId())) {
                bountyService.cancel(accepted);
                lang.send(victim, "target-killed-hunter");
                lang.send(killer, "target-killed-hunter");
                return;
            }
        }
    }
}
