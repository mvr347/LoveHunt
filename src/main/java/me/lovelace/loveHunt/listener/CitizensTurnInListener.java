package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.CitizensIntegration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles turning a bounty trophy head in at the bound Citizens NPC. Deliberately listens for the
 * core {@link PlayerInteractEntityEvent} rather than a Citizens-specific event, so this class (and
 * registering it) is always safe even when Citizens isn't installed - all Citizens-specific
 * lookups happen lazily inside {@link CitizensIntegration}.
 */
public final class CitizensTurnInListener implements Listener {
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final CitizensIntegration citizens;

    public CitizensTurnInListener(Settings settings, Lang lang, BountyService bountyService, CitizensIntegration citizens) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.citizens = citizens;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (settings.turnInNpcId() < 0 || !citizens.isAvailable()) {
            return;
        }
        Integer npcId = citizens.npcId(event.getRightClicked());
        if (npcId == null || npcId != settings.turnInNpcId()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        Long bountyId = bountyIdOf(player.getInventory().getItemInMainHand());
        if (bountyId == null) {
            lang.send(player, "turnin-wrong-item");
            return;
        }
        Bounty bounty = bountyService.get(bountyId);
        if (bounty == null) {
            lang.send(player, "turnin-bounty-gone");
            return;
        }
        if (!bountyService.hasAccepted(player.getUniqueId(), bounty.id())) {
            lang.send(player, "turnin-not-your-bounty");
            return;
        }

        consumeOne(player);
        bountyService.complete(bounty, player);
        lang.send(player, "turnin-success", lang.placeholders("target", bounty.targetName()));
    }

    private Long bountyIdOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(LoveHunt.BOUNTY_KEY, PersistentDataType.LONG);
    }

    private void consumeOne(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }
}
