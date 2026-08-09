package me.lovelace.loveHunt.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Heads {
    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public Heads(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "heads.yml");
        if (!file.exists()) {
            plugin.saveResource("heads.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean useBase64ForButtons() {
        return config.getBoolean("use-base64-for-buttons", false);
    }

    public String base64(String key) {
        return config.getString(key, "");
    }

    public String base64(String key, String fallback) {
        return config.getString(key, fallback);
    }
}
