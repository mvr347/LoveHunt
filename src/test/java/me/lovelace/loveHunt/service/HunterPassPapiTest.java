package me.lovelace.loveHunt.service;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

class HunterPassPapiTest {
    @Test
    void ticketFieldsDoNotLoadUnrelatedRatingsOrRequireAnOnlinePlayer() {
        var service = new BountyService(null, null, null, null, null, null);
        var expansion = new LoveHuntPapiExpansion(null, service);
        UUID id = UUID.randomUUID();
        var offline =
                (OfflinePlayer)
                        Proxy.newProxyInstance(
                                OfflinePlayer.class.getClassLoader(),
                                new Class[] {OfflinePlayer.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getUniqueId") ? id : null);
        assertEquals("false", expansion.onRequest(offline, "has_pass"));
        assertEquals("0", expansion.onRequest(offline, "pass_level"));
        assertEquals("", expansion.onRequest(offline, "pass"));
        assertEquals("", expansion.onRequest(null, "has_pass"));
    }
}
