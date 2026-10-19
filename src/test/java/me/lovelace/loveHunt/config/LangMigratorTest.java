package me.lovelace.loveHunt.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LangMigratorTest {

    private static YamlConfiguration bundled() {
        return YamlConfiguration.loadConfiguration(Path.of("src/main/resources/lang.yml").toFile());
    }

    private static YamlConfiguration server(String yaml) {
        return YamlConfiguration.loadConfiguration(new StringReader(yaml));
    }

    @Test
    void bundledLangYmlIsAtTheLatestVersion() {
        assertEquals(LangMigrator.latestVersion(), bundled().getInt(LangMigrator.VERSION_KEY),
                "bump lang-version in lang.yml together with the LangMigrator step");
    }

    @Test
    void fileAtTheLatestVersionIsNotTouched() {
        YamlConfiguration file = server("lang-version: 2\ngui:\n  create: \"<green>Admin edit\"\n");
        assertFalse(LangMigrator.migrate(file, bundled()));
        assertEquals("<green>Admin edit", file.getString("gui.create"));
    }

    @Test
    void oldFileGetsTheRewrittenSectionsFromTheJar() {
        YamlConfiguration jar = bundled();
        YamlConfiguration file = server("""
                prefix: "<red>Своя охота"
                help-main: "<gray>Свой текст помощи"
                gui:
                  create: "<green>Старое название"
                  create-lore:
                    - "<gray>Старая строка"
                  only-on-old-server: "<gray>Исчезнет при миграции"
                """);

        assertTrue(LangMigrator.migrate(file, jar));

        assertEquals(LangMigrator.latestVersion(), file.getInt(LangMigrator.VERSION_KEY));
        assertEquals(jar.getString("gui.create"), file.getString("gui.create"));
        assertEquals(jar.getStringList("gui.create-lore"), file.getStringList("gui.create-lore"));
        assertEquals(jar.getString("style.separator"), file.getString("style.separator"));
        assertNull(file.getString("gui.only-on-old-server"), "keys of an overwritten section that the jar lacks are dropped");
    }

    @Test
    void keysOutsideTheOverwrittenSectionsSurvive() {
        YamlConfiguration file = server("""
                prefix: "<red>Своя охота"
                help-main: "<gray>Свой текст помощи"
                """);

        LangMigrator.migrate(file, bundled());

        assertEquals("<red>Своя охота", file.getString("prefix"));
        assertEquals("<gray>Свой текст помощи", file.getString("help-main"));
    }

    @Test
    void migrationRunsOnce() {
        YamlConfiguration file = server("prefix: \"<red>x\"\n");
        YamlConfiguration jar = bundled();
        assertTrue(LangMigrator.migrate(file, jar));
        file.set("gui.create", "<green>Admin edit after migration");
        assertFalse(LangMigrator.migrate(file, jar));
        assertEquals("<green>Admin edit after migration", file.getString("gui.create"));
    }
}
