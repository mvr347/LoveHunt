package me.lovelace.loveHunt.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;

/**
 * Pure (server-free) parsing of the turn-in NPC settings: the provider id, the String NPC key and the
 * migration from the legacy numeric Citizens id ({@code citizens.turn-in-npc-id}).
 */
public record NpcBindingConfig(String providerId, String key, String name) {

    public static final String DEFAULT_PROVIDER = "citizens";
    public static final Set<String> KNOWN_PROVIDERS = Set.of("citizens", "loveessential");

    public static final String PROVIDER_PATH = "integrations.npc-provider";
    public static final String KEY_PATH = "npc.turn-in-key";
    public static final String NAME_PATH = "npc.turn-in-name";
    public static final String LEGACY_ID_PATH = "citizens.turn-in-npc-id";
    public static final String LEGACY_NAME_PATH = "citizens.turn-in-npc-name";

    /** Whether the raw config value names a provider this plugin knows (null/blank counts as the default). */
    public static boolean isKnownProvider(String raw) {
        return raw == null || raw.isBlank() || KNOWN_PROVIDERS.contains(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** Provider id from config; anything unknown falls back to {@link #DEFAULT_PROVIDER}. */
    public static String resolveProviderId(String raw) {
        if (raw == null) {
            return DEFAULT_PROVIDER;
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        return KNOWN_PROVIDERS.contains(id) ? id : DEFAULT_PROVIDER;
    }

    /**
     * Normalizes a raw config value into an NPC key. A number (legacy Citizens id, also {@code 5.0} or
     * {@code "05"}) becomes plain digits; null, blank and negative numbers (the old {@code -1} "unbound"
     * marker) become the empty string, which means "not bound".
     */
    public static String normalizeKey(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (value < 0 || value != Math.rint(value)) {
                return "";
            }
            return String.valueOf(number.longValue());
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return "";
        }
        if (text.matches("-\\d+(\\.0*)?")) {
            return "";
        }
        if (text.matches("\\d+(\\.0*)?")) {
            String digits = text.contains(".") ? text.substring(0, text.indexOf('.')) : text;
            try {
                return String.valueOf(Long.parseLong(digits));
            } catch (NumberFormatException overflow) {
                return digits;
            }
        }
        return text;
    }

    /** Whether the clicked NPC is the bound one: same provider as configured and the same key. */
    public static boolean isBound(String configuredProvider, String boundKey, String npcProviderId, String npcKey) {
        if (boundKey == null || boundKey.isEmpty() || npcProviderId == null || npcKey == null) {
            return false;
        }
        return configuredProvider.equalsIgnoreCase(npcProviderId) && boundKey.equals(normalizeKey(npcKey));
    }

    /**
     * Moves the legacy numeric binding into the String-key keys, in place. Returns true when the section was
     * changed and must be saved. Keys already present in the new place win over the legacy ones.
     */
    public static boolean migrateLegacy(ConfigurationSection config) {
        boolean hasLegacyId = config.isSet(LEGACY_ID_PATH);
        boolean hasLegacyName = config.isSet(LEGACY_NAME_PATH);
        if (!hasLegacyId && !hasLegacyName) {
            return false;
        }
        if (normalizeKey(config.get(KEY_PATH)).isEmpty()) {
            config.set(KEY_PATH, normalizeKey(config.get(LEGACY_ID_PATH)));
        }
        String currentName = config.getString(NAME_PATH, "");
        if (currentName.isEmpty()) {
            config.set(NAME_PATH, config.getString(LEGACY_NAME_PATH, ""));
        }
        config.set(LEGACY_ID_PATH, null);
        config.set(LEGACY_NAME_PATH, null);
        if (config.isConfigurationSection("citizens") && config.getConfigurationSection("citizens").getKeys(false).isEmpty()) {
            config.set("citizens", null);
        }
        return true;
    }

    /** Reads the settings (call {@link #migrateLegacy} first); a legacy id is still honoured as a fallback. */
    public static NpcBindingConfig read(ConfigurationSection config) {
        String key = normalizeKey(config.get(KEY_PATH));
        if (key.isEmpty()) {
            key = normalizeKey(config.get(LEGACY_ID_PATH));
        }
        String name = config.getString(NAME_PATH, "");
        if (name.isEmpty()) {
            name = config.getString(LEGACY_NAME_PATH, "");
        }
        return new NpcBindingConfig(resolveProviderId(config.getString(PROVIDER_PATH)), key, name);
    }
}
