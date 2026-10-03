package me.lovelace.loveHunt.config;

import dev.lovelace.lovecore.api.economy.MoneyParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** The clan bounty price must parse: a typo must fail the build, not silently make contracts free. */
class MoneyKeysTest {

    @Test
    void clanTreasuryCostParses() throws Exception {
        YamlConfiguration cfg;
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            cfg = YamlConfiguration.loadConfiguration(r);
        }
        Object raw = cfg.get("clan-bounty.treasury-cost");
        assertNotNull(raw);
        long cost = raw instanceof Number n ? n.longValue() : MoneyParser.parse(String.valueOf(raw), MoneyParser.STANDARD);
        assertEquals(1_500L, cost);
    }
}
