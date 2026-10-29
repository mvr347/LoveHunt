package me.lovelace.loveHunt.listener;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.util.HeadItems;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A trophy belongs to the hunter who made the kill and exists only to be handed in at the NPC. It cannot be given
 * away: dropping it destroys it, nobody else can pick it up, and it cannot be put into a chest or any other
 * container. It is also lost when its owner dies, so it never ends up as loot for the one who killed the hunter.
 */
public final class TrophyBindingListener implements Listener {
    private final Lang lang;

    public TrophyBindingListener(Lang lang) {
        this.lang = lang;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (!HeadItems.isTrophy(dropped.getItemStack())) {
            return;
        }
        dropped.remove();
        lang.notice(event.getPlayer(), "trophy-lost");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!HeadItems.isTrophy(stack)) {
            return;
        }
        UUID owner = HeadItems.trophyOwner(stack);
        if (!(event.getEntity() instanceof Player player) || owner == null || !owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOwnerDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(HeadItems::isTrophy);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || isOwnView(event.getView().getTopInventory())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean inTop = event.getClickedInventory() == top;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean block = false;
        if (inTop) {
            // Placing a trophy into the container, by cursor or by a hotbar key.
            block = HeadItems.isTrophy(cursor)
                    || (event.getClick() == ClickType.NUMBER_KEY && HeadItems.isTrophy(player.getInventory().getItem(event.getHotbarButton())))
                    || (event.getClick() == ClickType.SWAP_OFFHAND && HeadItems.isTrophy(player.getInventory().getItemInOffHand()));
        } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            block = HeadItems.isTrophy(current);
        }
        if (block) {
            event.setCancelled(true);
            lang.notice(player, "trophy-no-store");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || isOwnView(event.getView().getTopInventory())) {
            return;
        }
        if (!HeadItems.isTrophy(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
            lang.notice(player, "trophy-no-store");
        }
    }

    /** The player's own inventory screen has a crafting grid on top; a trophy may move freely there. */
    private boolean isOwnView(Inventory top) {
        return top.getType() == InventoryType.CRAFTING;
    }
}
