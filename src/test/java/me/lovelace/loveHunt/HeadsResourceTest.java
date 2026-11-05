package me.lovelace.loveHunt;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HeadsResourceTest {
    private static YamlConfiguration resource(String name) {
        var stream = HeadsResourceTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, name);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test
    void theConfirmHeadUsesTheStandardTextureHash() {
        String decoded = new String(Base64.getDecoder().decode(resource("heads.yml").getString("confirm-base64")), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a"), decoded);
    }

    @Test
    void theTypoMigrationReplacesOnlyTheOldValue() {
        var manifest = resource("gui-style-followup.yml");
        boolean found = false;
        for (var update : manifest.getMapList("updates")) {
            if (!"confirm-base64".equals(update.get("path"))) continue;
            found = true;
            assertEquals(Boolean.TRUE, update.get("preserve-custom"));
            assertEquals(update.get("value"), resource("heads.yml").getString("confirm-base64"));
            assertNotEquals(update.get("before"), update.get("value"));
        }
        assertTrue(found);
    }
}
