package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.service.HunterService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-click with the hunter pass in hand uses it up; carrying it around does nothing. */
public final class HunterPassListener implements Listener {
    private final HunterService hunters;

    public HunterPassListener(HunterService hunters) {
        this.hunters = hunters;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!hunters.isTicket(item)) return;
        event.setCancelled(true);
        hunters.activate(player, item);
    }
}
