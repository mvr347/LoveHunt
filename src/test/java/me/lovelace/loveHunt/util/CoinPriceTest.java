package me.lovelace.loveHunt.util;

import static org.junit.jupiter.api.Assertions.*;
import dev.lovelace.lovecore.api.economy.Denomination;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class CoinPriceTest {
    private final List<Denomination> coins = List.of(
        new Denomination("voidcore:copper_coin", 1),
        new Denomination("voidcore:gold_coin", 2000),
        new Denomination("voidcore:iron_coin", 100));

    @Test
    void priceUsesVisibleDenominationsFromLargestToSmallest() {
        assertEquals("[voidcore:gold_coin] <white>x2</white>  [voidcore:iron_coin] <white>x3</white>  [voidcore:copper_coin] <white>x7</white>",
            CoinPrice.format(4307, coins, id -> "[" + id + "]"));
        assertEquals("[voidcore:copper_coin] <white>x0</white>",
            CoinPrice.format(0, coins, id -> "[" + id + "]"));
    }

    @Test
    void itemsAdderLegacyColorAndResetDoNotBreakThePriceOrFollowingText() {
        String price = CoinPrice.format(2000, coins, id -> CoinPrice.fontText("§f\uE020§r"));
        var component = MiniMessage.miniMessage().deserialize("<white>" + price + "</white><gold> Купить</gold>");
        assertEquals("\uE020 x1 Купить", PlainTextComponentSerializer.plainText().serialize(component));
        assertFalse(price.contains("§"));
    }

    @Test
    void missingDenominationsNeverHideTheAmountOrRemainder() {
        assertEquals("123", CoinPrice.format(123, List.of(), id -> id));
        assertEquals("gold <white>x1</white>  7", CoinPrice.format(2007,
            List.of(new Denomination("gold", 2000)), id -> id));
    }
}
