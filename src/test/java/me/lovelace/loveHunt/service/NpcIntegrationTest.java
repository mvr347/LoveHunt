package me.lovelace.loveHunt.service;

import dev.lovelace.lovecore.api.npc.NpcProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class NpcIntegrationTest {

    private static NpcProvider stubProvider() {
        return (NpcProvider) Proxy.newProxyInstance(NpcProvider.class.getClassLoader(),
                new Class<?>[]{NpcProvider.class}, (proxy, method, args) -> {
                    if (method.getName().equals("targeted")) {
                        return Optional.empty();
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static List<LogRecord> capture(Logger logger) {
        List<LogRecord> records = new ArrayList<>();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { records.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        return records;
    }

    @Test
    void returnsTheConfiguredProviderWithoutWarning() {
        Logger logger = Logger.getLogger("npc-test-ok");
        List<LogRecord> logs = capture(logger);
        NpcProvider provider = stubProvider();
        AtomicReference<String> asked = new AtomicReference<>();
        NpcIntegration npcs = new NpcIntegration(() -> "loveessential", id -> {
            asked.set(id);
            return Optional.of(provider);
        }, logger);

        assertSame(provider, npcs.provider().orElseThrow());
        assertEquals("loveessential", asked.get());
        assertTrue(logs.isEmpty());
    }

    @Test
    void missingProviderWarnsOnceNamingItAndNeverThrows() {
        Logger logger = Logger.getLogger("npc-test-missing");
        List<LogRecord> logs = capture(logger);
        NpcIntegration npcs = new NpcIntegration(() -> "loveessential", id -> Optional.empty(), logger);

        assertTrue(npcs.provider().isEmpty());
        assertTrue(npcs.provider().isEmpty());
        assertFalse(npcs.isAvailable());

        assertEquals(1, logs.size());
        assertEquals(Level.WARNING, logs.get(0).getLevel());
        assertTrue(logs.get(0).getMessage().contains("loveessential"));
    }

    @Test
    void absentLoveCoreClassesAreHandled() {
        Logger logger = Logger.getLogger("npc-test-nocore");
        List<LogRecord> logs = capture(logger);
        NpcIntegration npcs = new NpcIntegration(() -> "citizens", id -> {
            throw new NoClassDefFoundError("dev/lovelace/lovecore/api/LoveCore");
        }, logger);

        assertTrue(npcs.provider().isEmpty());
        assertEquals(1, logs.size());
        assertTrue(logs.get(0).getMessage().contains("LoveCore"));
        assertTrue(logs.get(0).getMessage().contains("citizens"));
    }

    @Test
    void warnsAgainAfterResetAndAfterProviderChange() {
        Logger logger = Logger.getLogger("npc-test-reset");
        List<LogRecord> logs = capture(logger);
        AtomicReference<String> id = new AtomicReference<>("citizens");
        NpcIntegration npcs = new NpcIntegration(id::get, any -> Optional.empty(), logger);

        npcs.provider();
        npcs.provider();
        assertEquals(1, logs.size());
        npcs.resetWarnings();
        npcs.provider();
        assertEquals(2, logs.size());
        id.set("loveessential");
        npcs.provider();
        assertEquals(3, logs.size());
    }

    @Test
    void providerIsResolvedOnEveryUseSoLateRegistrationWorks() {
        Logger logger = Logger.getLogger("npc-test-late");
        capture(logger);
        AtomicReference<Optional<NpcProvider>> registry = new AtomicReference<>(Optional.empty());
        NpcIntegration npcs = new NpcIntegration(() -> "citizens", id -> registry.get(), logger);

        assertTrue(npcs.provider().isEmpty());
        registry.set(Optional.of(stubProvider()));
        assertTrue(npcs.provider().isPresent());
    }
}
