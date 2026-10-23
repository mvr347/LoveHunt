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
    void providerDefaultsToCitizensAndRejectsUnknown() {
        assertEquals("citizens", NpcBindingConfig.resolveProviderId(null));
        assertEquals("citizens", NpcBindingConfig.resolveProviderId("  "));
        assertEquals("citizens", NpcBindingConfig.resolveProviderId("znpcs"));
        assertEquals("loveessential", NpcBindingConfig.resolveProviderId(" LoveEssential "));
        assertTrue(NpcBindingConfig.isKnownProvider(null));
        assertTrue(NpcBindingConfig.isKnownProvider("Citizens"));
        assertFalse(NpcBindingConfig.isKnownProvider("znpcs"));
    }

    @Test
    void globalFollowsTheCoreAndWithoutItMeansCitizens() {
        assertEquals("citizens", NpcBindingConfig.resolveProviderId("global"));
        assertEquals("citizens", NpcBindingConfig.resolveProviderId(" GLOBAL "));
        assertTrue(NpcBindingConfig.isKnownProvider("global"));
        assertTrue(NpcBindingConfig.followsGlobal(null));
        assertFalse(NpcBindingConfig.followsGlobal("citizens"));
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
        assertEquals("citizens", binding.providerId());
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
        assertTrue(NpcBindingConfig.isBound("citizens", "42", "citizens", "42"));
        assertFalse(NpcBindingConfig.isBound("citizens", "42", "loveessential", "42"), "other provider's click is ignored");
        assertFalse(NpcBindingConfig.isBound("citizens", "42", "citizens", "43"));
        assertFalse(NpcBindingConfig.isBound("citizens", "", "citizens", ""), "unbound matches nothing");
        assertFalse(NpcBindingConfig.isBound("citizens", "42", "citizens", null));
        assertTrue(NpcBindingConfig.isBound("loveessential", "ext-1", "loveessential", "ext-1"));
    }

    @Test
    void bundledConfigDefaults() {
        YamlConfiguration jar = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        NpcBindingConfig binding = NpcBindingConfig.read(jar);
        assertEquals("citizens", binding.providerId());
        assertEquals("", binding.key());
        assertFalse(NpcBindingConfig.migrateLegacy(jar), "the bundled config has no legacy keys");
    }
}
