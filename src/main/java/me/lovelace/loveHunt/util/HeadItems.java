package me.lovelace.loveHunt.util;

import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/**
 * The two kinds of player head LoveHunt makes. The <b>trophy</b> drops from the target and is bound to the hunter
 * who killed it: it can only be handed in, never passed on. The <b>delivered head</b> is what the customer gets
 * after the hand-in; it is a normal item that LoveShop's Wanderer buys (it finds it by the {@code lovehunt:delivered_head}
 * key, which holds the victim's UUID, so the two plugins do not depend on each other).
 */
public final class HeadItems {
    private HeadItems() {
    }

    public static ItemStack trophy(Lang lang, OfflinePlayer victim, String victimName, long bountyId, UUID hunter) {
        ItemStack head = HeadUtil.playerHead(victim);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            var placeholders = lang.placeholders("target", victimName);
            meta.displayName(plain(lang.component("head.trophy.name", placeholders, false)));
            meta.lore(plain(lang.components("head.trophy.lore", placeholders, false)));
            meta.getPersistentDataContainer().set(LoveHunt.BOUNTY_KEY, PersistentDataType.LONG, bountyId);
            meta.getPersistentDataContainer().set(LoveHunt.TROPHY_HUNTER_KEY, PersistentDataType.STRING, hunter.toString());
            head.setItemMeta(meta);
        }
        return head;
    }

    public static ItemStack deliveredHead(Lang lang, OfflinePlayer victim, String victimName, UUID victimUuid) {
        ItemStack head = HeadUtil.playerHead(victim);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            var placeholders = lang.placeholders("target", victimName);
            meta.displayName(plain(lang.component("head.delivered.name", placeholders, false)));
            meta.lore(plain(lang.components("head.delivered.lore", placeholders, false)));
            meta.getPersistentDataContainer().set(LoveHunt.HEAD_KEY, PersistentDataType.STRING, victimUuid.toString());
            head.setItemMeta(meta);
        }
        return head;
    }

    public static boolean isTrophy(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(LoveHunt.TROPHY_HUNTER_KEY, PersistentDataType.STRING);
    }

    /** The hunter the trophy is bound to, or {@code null} when the item is not a trophy. */
    public static UUID trophyOwner(ItemStack item) {
        if (!isTrophy(item)) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(LoveHunt.TROPHY_HUNTER_KEY, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Client shows custom names and lore in italics; lore text is built without it. */
    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private static List<Component> plain(List<Component> lines) {
        return lines.stream().map(HeadItems::plain).toList();
    }
}
