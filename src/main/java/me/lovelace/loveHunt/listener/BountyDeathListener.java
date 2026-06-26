package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.util.HeadUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Set;

/**
 * On death, a bounty target's head always drops if they currently have an active bounty on
 * them (100% guaranteed drop, regardless of who landed the kill). The bounty itself is no
 * longer completed instantly on kill - the hunter must carry the tagged head to the turn-in
 * NPC (see {@link me.lovelace.loveHunt.service.CitizensTurnInListener}) to actually claim it.
 */
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

        Bounty bountyOnVictim = bountyService.activeOn(victim.getUniqueId());
        if (bountyOnVictim != null) {
            ItemStack head = bountyTrophy(victim, bountyOnVictim.id());
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);
        }

        if (killer == null) {
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

    private ItemStack bountyTrophy(Player victim, long bountyId) {
        ItemStack head = HeadUtil.playerHead(victim);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.legacy("§6Трофей розыска: §f" + victim.getName()));
            meta.lore(java.util.List.of(lang.legacy("§7Сдайте NPC для завершения розыска")));
            meta.getPersistentDataContainer().set(LoveHunt.BOUNTY_KEY, PersistentDataType.LONG, bountyId);
            head.setItemMeta(meta);
        }
        return head;
    }
}
