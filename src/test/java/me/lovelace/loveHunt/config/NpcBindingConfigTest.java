package me.lovelace.loveHunt.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NpcBindingConfigTest {

    private static YamlConfiguration yaml(String text) {
        return YamlConfiguration.loadConfiguration(new StringReader(text));
    }

    @Test
    void providerIsAlwaysLoveEssential() {
        assertEquals("loveessential", NpcBindingConfig.PROVIDER_ID);
        assertEquals("loveessential", NpcBindingConfig.read(yaml("integrations:\n  npc-provider: citizens\n")).providerId(),
                "a leftover value must not pick another provider");
    }

    @Test
    void leftoverProviderValuesAreReportedButNeverUsed() {
        assertEquals("citizens", NpcBindingConfig.ignoredProviderValue("citizens"));
        assertEquals("znpcs", NpcBindingConfig.ignoredProviderValue(" znpcs "));
        assertNull(NpcBindingConfig.ignoredProviderValue(null));
        assertNull(NpcBindingConfig.ignoredProviderValue("  "));
        assertNull(NpcBindingConfig.ignoredProviderValue("global"));
        assertNull(NpcBindingConfig.ignoredProviderValue(" GLOBAL "));
        assertNull(NpcBindingConfig.ignoredProviderValue("LoveEssential"));
    }

    @Test
    void normalizeKeyHandlesNumbersAndText() {
        assertEquals("", NpcBindingConfig.normalizeKey(null));
        assertEquals("", NpcBindingConfig.normalizeKey(-1));
        assertEquals("", NpcBindingConfig.normalizeKey("-1"));
        assertEquals("", NpcBindingConfig.normalizeKey("  "));
        assertEquals("7", NpcBindingConfig.normalizeKey(7));
        assertEquals("7", NpcBindingConfig.normalizeKey(7.0));
        assertEquals("7", NpcBindingConfig.normalizeKey("007"));
        assertEquals("0", NpcBindingConfig.normalizeKey(0));
        assertEquals("ext-1a2b3c4d", NpcBindingConfig.normalizeKey(" ext-1a2b3c4d "));
    }

    @Test
    void legacyNumericBindingIsMigratedWithoutLoss() {
        YamlConfiguration cfg = yaml("citizens:\n  turn-in-npc-id: 42\n  turn-in-npc-name: \"Охотник\"\n");

        assertTrue(NpcBindingConfig.migrateLegacy(cfg));

        assertEquals("42", cfg.getString(NpcBindingConfig.KEY_PATH));
        assertEquals("Охотник", cfg.getString(NpcBindingConfig.NAME_PATH));
        assertFalse(cfg.isSet(NpcBindingConfig.LEGACY_ID_PATH));
        assertFalse(cfg.isSet(NpcBindingConfig.LEGACY_NAME_PATH));
        assertFalse(cfg.isSet("citizens"), "the emptied legacy section is removed");
        NpcBindingConfig binding = NpcBindingConfig.read(cfg);
        assertEquals("42", binding.key());
        assertEquals("Охотник", binding.name());
        assertEquals("loveessential", binding.providerId());
        assertFalse(NpcBindingConfig.migrateLegacy(cfg), "migration is idempotent");
    }

    @Test
    void legacyUnboundMarkerStaysUnbound() {
        YamlConfiguration cfg = yaml("citizens:\n  turn-in-npc-id: -1\n  turn-in-npc-name: \"\"\n");
        assertTrue(NpcBindingConfig.migrateLegacy(cfg));
        assertEquals("", NpcBindingConfig.read(cfg).key());
    }

    @Test
    void newKeyWinsOverLegacy() {
        YamlConfiguration cfg = yaml("npc:\n  turn-in-key: \"ext-abc\"\n  turn-in-name: \"Новый\"\ncitizens:\n  turn-in-npc-id: 5\n");
        NpcBindingConfig.migrateLegacy(cfg);
        NpcBindingConfig binding = NpcBindingConfig.read(cfg);
        assertEquals("ext-abc", binding.key());
        assertEquals("Новый", binding.name());
    }

    @Test
    void unmigratedLegacyIsStillReadable() {
        YamlConfiguration cfg = yaml("citizens:\n  turn-in-npc-id: 9\n");
        assertEquals("9", NpcBindingConfig.read(cfg).key());
    }

    @Test
    void numericYamlValueInNewKeyIsAValidKey() {
        assertEquals("12", NpcBindingConfig.read(yaml("npc:\n  turn-in-key: 12\n")).key());
    }

    @Test
    void isBoundFiltersByProviderAndKey() {
        assertTrue(NpcBindingConfig.isBound("loveessential", "42", "loveessential", "42"));
        assertFalse(NpcBindingConfig.isBound("loveessential", "42", "citizens", "42"), "other provider's click is ignored");
        assertFalse(NpcBindingConfig.isBound("loveessential", "42", "loveessential", "43"));
        assertFalse(NpcBindingConfig.isBound("loveessential", "", "loveessential", ""), "unbound matches nothing");
        assertFalse(NpcBindingConfig.isBound("loveessential", "42", "loveessential", null));
        assertTrue(NpcBindingConfig.isBound("loveessential", "ext-1", "loveessential", "ext-1"));
    }

    @Test
    void bundledConfigDefaults() {
        YamlConfiguration jar = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        NpcBindingConfig binding = NpcBindingConfig.read(jar);
        assertEquals("loveessential", binding.providerId());
        assertEquals("", binding.key());
        assertFalse(NpcBindingConfig.migrateLegacy(jar), "the bundled config has no legacy keys");
        assertNull(jar.getString(NpcBindingConfig.PROVIDER_PATH), "the provider switch was removed");
    }
}
