package me.lovelace.loveHunt.util;

import me.lovelace.loveHunt.LoveHunt;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.model.HeadDetails;
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

    public static ItemStack trophy(Lang lang, OfflinePlayer victim, String victimName, long bountyId, UUID hunter, HeadDetails details) {
        ItemStack head = HeadUtil.playerHead(victim);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            var placeholders = lang.placeholders("target", victimName);
            meta.displayName(plain(lang.component("head.trophy.name", placeholders, false)));
            meta.lore(plain(lang.components("head.trophy.lore", placeholders, false)));
            meta.getPersistentDataContainer().set(LoveHunt.BOUNTY_KEY, PersistentDataType.LONG, bountyId);
            meta.getPersistentDataContainer().set(LoveHunt.TROPHY_HUNTER_KEY, PersistentDataType.STRING, hunter.toString());
            meta.getPersistentDataContainer().set(LoveHunt.TROPHY_DETAILS_KEY, PersistentDataType.STRING, details.encode());
            head.setItemMeta(meta);
        }
        return head;
    }

    public static ItemStack deliveredHead(Lang lang, OfflinePlayer victim, String victimName, UUID victimUuid, HeadDetails details) {
        ItemStack head = HeadUtil.playerHead(victim);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            var placeholders = lang.placeholders("target", victimName, "cause", causeLabel(lang, details.cause()),
                    "weapon", weaponLabel(lang, details.weapon()));
            meta.displayName(plain(lang.component("head.delivered.name", placeholders, false)));
            meta.lore(plain(lang.components("head.delivered.lore", placeholders, false)));
            meta.getPersistentDataContainer().set(LoveHunt.HEAD_KEY, PersistentDataType.STRING, victimUuid.toString());
            meta.getPersistentDataContainer().set(LoveHunt.HEAD_DETAILS_KEY, PersistentDataType.STRING, details.encode());
            head.setItemMeta(meta);
        }
        return head;
    }

    public static boolean isTrophy(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(LoveHunt.TROPHY_HUNTER_KEY, PersistentDataType.STRING);
    }

    /** How the target died, as written on the trophy; neutral values for an old trophy without it. */
    public static HeadDetails trophyDetails(ItemStack item) {
        if (!isTrophy(item)) return HeadDetails.UNKNOWN;
        return HeadDetails.decode(item.getItemMeta().getPersistentDataContainer().get(LoveHunt.TROPHY_DETAILS_KEY, PersistentDataType.STRING));
    }

    private static String causeLabel(Lang lang, String cause) {
        String key = "head.cause." + cause;
        return lang.rawOrNull(key) != null ? lang.plainText(key, java.util.Map.of()) : lang.plainText("head.cause.UNKNOWN", java.util.Map.of());
    }

    private static String weaponLabel(Lang lang, String weapon) {
        String key = "head.weapon." + weapon;
        return lang.rawOrNull(key) != null ? lang.plainText(key, java.util.Map.of()) : weapon.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
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
