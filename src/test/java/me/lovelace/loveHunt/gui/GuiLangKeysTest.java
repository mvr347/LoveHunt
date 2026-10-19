package me.lovelace.loveHunt.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every literal {@code gui.*} / {@code style.*} key the menu code reads must exist in lang.yml, so a renamed key fails the
 * build instead of showing the raw key in game. Keys under {@code gui.items.} are config.yml paths and are skipped, and
 * prefixes built by concatenation (they end with a dot) cannot be checked literally.
 */
class GuiLangKeysTest {

    private static final Pattern KEY = Pattern.compile("\"((?:gui|style)\\.[A-Za-z0-9_.\\-]+)\"");

    @Test
    void everyLiteralKeyExistsInLangYml() throws Exception {
        YamlConfiguration lang = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/lang.yml").toFile());
        TreeSet<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/me/lovelace/loveHunt/gui"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (m.find()) keys.add(m.group(1));
            }
        }
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (key.startsWith("gui.items.") || key.endsWith(".")) continue;
            if (!lang.contains(key)) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "keys used by the gui code but missing from lang.yml: " + missing);
    }
}
