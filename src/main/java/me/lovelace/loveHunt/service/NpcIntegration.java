package me.lovelace.loveHunt.service;

import dev.lovelace.lovecore.api.LoveCore;
import dev.lovelace.lovecore.api.npc.NpcHandle;
import dev.lovelace.lovecore.api.npc.NpcProvider;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves the configured {@link NpcProvider} on every use (providers register late, and the config can be
 * reloaded). When the provider cannot be resolved, one warning naming it is logged and the NPC feature is
 * simply unavailable - never an exception.
 */
public final class NpcIntegration {
    private final Supplier<String> providerId;
    private final Function<String, Optional<NpcProvider>> lookup;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    public NpcIntegration(Supplier<String> providerId, Function<String, Optional<NpcProvider>> lookup, Logger logger) {
        this.providerId = providerId;
        this.lookup = lookup;
        this.logger = logger;
    }

    /** Production wiring: looks the provider up through LoveCore. */
    public static NpcIntegration viaLoveCore(Supplier<String> providerId, Logger logger) {
        return new NpcIntegration(providerId, id -> LoveCore.provider(NpcProvider.class, id), logger);
    }

    public String providerId() {
        return providerId.get();
    }

    public Optional<NpcProvider> provider() {
        String id = providerId.get();
        Optional<NpcProvider> found;
        try {
            found = lookup.apply(id);
        } catch (LinkageError missingApi) {
            // LoveCore is a soft dependency: its API classes are absent when it is not installed.
            warnOnce(id, "LoveCore is not installed, so the NPC provider '" + id + "' is unavailable; "
                    + "the hunter NPC feature is disabled.", missingApi);
            return Optional.empty();
        } catch (RuntimeException failure) {
            warnOnce(id, "Failed to resolve the NPC provider '" + id + "': " + failure + "; "
                    + "the hunter NPC feature is disabled.", failure);
            return Optional.empty();
        }
        if (found == null || found.isEmpty()) {
            warnOnce(id, "NPC provider '" + id + "' (integrations.npc-provider) is not registered in LoveCore - "
                    + "is the plugin that provides it installed? The hunter NPC feature is disabled.", null);
            return Optional.empty();
        }
        warned.remove(id);
        return found;
    }

    public boolean isAvailable() {
        return provider().isPresent();
    }

    /** The NPC the player is targeting, as the chosen provider defines it. */
    public Optional<NpcHandle> targeted(Player player) {
        return provider().flatMap(p -> p.targeted(player));
    }

    /** Forget which warnings were logged (after a config reload the admin may have fixed the cause). */
    public void resetWarnings() {
        warned.clear();
    }

    private void warnOnce(String id, String message, Throwable cause) {
        if (warned.add(id)) {
            if (cause != null && logger.isLoggable(Level.FINE)) {
                logger.log(Level.WARNING, message, cause);
            } else {
                logger.warning(message);
            }
        }
    }
}
