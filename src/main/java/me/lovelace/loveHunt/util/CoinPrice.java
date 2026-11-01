package me.lovelace.loveHunt.util;

import dev.lovelace.lovecore.api.economy.Denomination;
import dev.lovelace.lovecore.api.economy.LoveEconomy;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;

/** Uses the economy's visible denominations and ItemsAdder's actual font images. */
public final class CoinPrice {
    private static final MiniMessage MINI = MiniMessage.builder().strict(true).build();

    private CoinPrice() {}

    public static String format(LoveEconomy economy, long amount) {
        if (economy == null) return Long.toString(amount);
        return format(amount, economy.denominations(), CoinPrice::glyph);
    }

    static String format(long amount, List<Denomination> denominations, Function<String, String> glyph) {
        if (amount < 0) return Long.toString(amount);
        var coins = denominations.stream().sorted(Comparator.comparingLong(Denomination::value).reversed()).toList();
        var parts = new java.util.ArrayList<String>();
        long remaining = amount;
        for (var coin : coins) {
            long count = remaining / coin.value();
            if (count > 0) parts.add(glyph.apply(coin.itemId()) + " <white>x" + count + "</white>");
            remaining %= coin.value();
        }
        // An unusual currency without a unit denomination must never silently hide part of the price.
        if (remaining > 0 || coins.isEmpty()) parts.add(Long.toString(remaining));
        if (parts.isEmpty()) parts.add(glyph.apply(coins.getLast().itemId()) + " <white>x0</white>");
        return String.join("  ", parts);
    }

    private static String glyph(String itemId) {
        String name = itemId.substring(itemId.indexOf(':') + 1);
        String token = ":" + name + ":";
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                var api = Class.forName("dev.lone.itemsadder.api.FontImages.FontImageWrapper");
                Object result = api.getMethod("replaceFontImages", String.class).invoke(null, token);
                if (result instanceof String text && !text.isBlank() && !text.equals(token)) return fontText(text);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // No font API: retain a readable denomination instead of an unresolved placeholder.
            }
        }
        return MINI.escapeTags(name);
    }

    static String fontText(String text) {
        return "<white>" + MINI.serialize(LegacyComponentSerializer.legacySection().deserialize(text)) + "</white>";
    }
}
