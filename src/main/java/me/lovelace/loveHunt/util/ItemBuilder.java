package me.lovelace.loveHunt.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Single place where menu items get their name and lore. Minecraft draws item names and lore in italics unless the
 * text says otherwise, so the italics are switched off here; a line that sets italics explicitly ({@code <i>}) keeps them.
 */
public final class ItemBuilder {

    private ItemBuilder() {
    }

    public static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET
                ? component.decoration(TextDecoration.ITALIC, false)
                : component;
    }

    public static List<Component> noItalic(List<Component> lines) {
        return lines.stream().map(ItemBuilder::noItalic).toList();
    }

    /** Sets the name and, when given, the lore of {@code meta} without the default italics. */
    public static void apply(ItemMeta meta, Component name, List<Component> lore) {
        if (name != null) {
            meta.displayName(noItalic(name));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(noItalic(lore));
        }
    }
}
