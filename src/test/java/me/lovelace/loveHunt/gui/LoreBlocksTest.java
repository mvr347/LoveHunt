package me.lovelace.loveHunt.gui;

import me.lovelace.loveHunt.util.ItemBuilder;
import me.lovelace.loveHunt.util.LoreBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static me.lovelace.loveHunt.gui.LoreBlocks.Click;
import static me.lovelace.loveHunt.gui.LoreBlocks.Hint;
import static org.junit.jupiter.api.Assertions.*;

class LoreBlocksTest {

    private static final LoreBlocks.Style DEFAULTS = LoreBlocks.Style.DEFAULTS;

    @Test
    void defaultsEqualLangYml() {
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/lang.yml").toFile());
        LoreBlocks.defaults().forEach((path, text) -> assertEquals(text, lang.getString(path), path));
    }

    @Test
    void everyClickIsItsOwnLineWithItsOwnColour() {
        assertEquals("<green>ЛКМ <dark_gray>— <gray>открыть", LoreBlocks.click(DEFAULTS, Click.LEFT, "открыть"));
        assertEquals("<gold>ПКМ <dark_gray>— <gray>снять", LoreBlocks.click(DEFAULTS, Click.RIGHT, "снять"));
        assertEquals("<yellow>Shift+ЛКМ <dark_gray>— <gray>стек", LoreBlocks.click(DEFAULTS, Click.SHIFT_LEFT, "стек"));
        assertEquals("<yellow>Shift+ПКМ <dark_gray>— <gray>всё", LoreBlocks.click(DEFAULTS, Click.SHIFT_RIGHT, "всё"));
    }

    @Test
    void clicksStartWithTheSeparatorAndHaveOneLinePerHint() {
        List<String> lines = LoreBlocks.clicks(DEFAULTS, new Hint(Click.LEFT, "купить"), new Hint(Click.RIGHT, "снять"));
        assertEquals(List.of(LoreBlocks.SEPARATOR,
                "<green>ЛКМ <dark_gray>— <gray>купить",
                "<gold>ПКМ <dark_gray>— <gray>снять"), lines);
    }

    @Test
    void noHintsGiveNoSeparator() {
        assertTrue(LoreBlocks.clicks(DEFAULTS, List.of()).isEmpty());
    }

    @Test
    void blockedBlockKeepsTheStandardOrder() {
        List<String> lines = LoreBlocks.blocked(DEFAULTS, "unavailable", "Нет денег", new Hint(Click.LEFT, "улучшить"));
        assertEquals(List.of(
                LoreBlocks.SEPARATOR,
                "<red>✕ Действие недоступно",
                "<red>Причина: <gray>Нет денег",
                "<dark_gray>ЛКМ — улучшить (заблокировано)"), lines);
    }

    @Test
    void disabledNameHasTheStateSuffix() {
        assertEquals("<gray>Продажа <dark_gray>| <red>Недоступно", LoreBlocks.disabledName(DEFAULTS, "Продажа"));
    }

    @Test
    void styleTextWinsOverTheDefault() {
        LoreBlocks.Style custom = path -> path.equals("style.click.left") ? "L {action}" : null;
        assertEquals("L x", LoreBlocks.click(custom, Click.LEFT, "x"));
        assertEquals("<gold>ПКМ <dark_gray>— <gray>y", LoreBlocks.click(custom, Click.RIGHT, "y"));
    }

    @Test
    void barIsSixteenCellsWithPercent() {
        String bar = LoreBar.bar(5, 10);
        assertEquals(16, bar.chars().filter(c -> c == '█').count());
        assertTrue(bar.contains("50%"));
        assertTrue(LoreBar.bar(10, 10).contains("<green>"), "a full bar turns green");
        assertEquals(0, LoreBar.percent(3, 0), "an empty maximum never divides by zero");
    }

    @Test
    void italicsAreStrippedUnlessTheTextSetsIt() {
        Component plain = MiniMessage.miniMessage().deserialize("<gray>text");
        assertEquals(TextDecoration.State.FALSE, ItemBuilder.noItalic(plain).decoration(TextDecoration.ITALIC));
        Component explicit = MiniMessage.miniMessage().deserialize("<italic>text");
        assertEquals(TextDecoration.State.TRUE, ItemBuilder.noItalic(explicit).decoration(TextDecoration.ITALIC));
    }
}
