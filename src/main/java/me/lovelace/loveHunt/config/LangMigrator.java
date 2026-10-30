package me.lovelace.loveHunt.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Bukkit's copyDefaults only adds missing keys, so a text that was reworded in the jar never reaches a server's existing
 * lang.yml. Each {@code lang-version} bump lists the sections that must be taken from the jar again; {@link Lang} keeps a
 * backup of the old file next to it, so an admin's edits are never lost without a copy.
 */
public final class LangMigrator {

    public static final String VERSION_KEY = "lang-version";

    /** A section of lang.yml that is overwritten from the jar (its server keys are dropped first). */
    record Root(String path) {
    }

    /** Sections overwritten when migrating to version N (index = N - 2). */
    private static final List<List<Root>> OVERWRITE_BY_VERSION = List.of(
            // v2: GUI lore in the lore standard - the shared style.* block and every gui.* text of the menus
            List.of(new Root("style"), new Root("gui")),
            // v3: head.* - the lore of the trophy and of the delivered head now names how the target died
            List.of(new Root("head")));

    private LangMigrator() {
    }

    public static int latestVersion() {
        return OVERWRITE_BY_VERSION.size() + 1;
    }

    /**
     * Takes the reworded sections of the jar into an older server file. Returns true when the server file was older and
     * has been changed; the server file is then at {@link #latestVersion()}.
     */
    public static boolean migrate(FileConfiguration server, FileConfiguration bundled) {
        int current = server.getInt(VERSION_KEY, 1);
        int target = Math.min(bundled.getInt(VERSION_KEY, 1), latestVersion());
        if (current >= target) {
            return false;
        }
        for (int version = current + 1; version <= target; version++) {
            for (Root root : OVERWRITE_BY_VERSION.get(version - 2)) {
                overwrite(server, bundled, root.path());
            }
        }
        server.set(VERSION_KEY, target);
        return true;
    }

    private static void overwrite(FileConfiguration server, FileConfiguration bundled, String path) {
        Object value = bundled.get(path);
        if (value instanceof ConfigurationSection section) {
            server.set(path, null);
            for (String key : section.getKeys(true)) {
                Object leaf = section.get(key);
                if (!(leaf instanceof ConfigurationSection)) {
                    copy(server, bundled, path + "." + key);
                }
            }
        } else if (value != null) {
            copy(server, bundled, path);
        }
    }

    /** Copies one value together with the comment the jar has above it. */
    private static void copy(FileConfiguration server, FileConfiguration bundled, String path) {
        server.set(path, bundled.get(path));
        List<String> comments = bundled.getComments(path);
        if (!comments.isEmpty()) {
            server.setComments(path, comments);
        }
    }
}
