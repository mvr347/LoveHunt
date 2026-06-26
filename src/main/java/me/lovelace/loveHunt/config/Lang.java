package me.lovelace.loveHunt.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Lang {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration lang;
    private String prefix;

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "lang.yml");
        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }
        lang = YamlConfiguration.loadConfiguration(file);
        prefix = lang.getString("prefix", "");
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        for (Component component : components(key, placeholders, true)) {
            sender.sendMessage(component);
        }
    }

    public void sendClickableCancel(CommandSender sender) {
        Component component = component("cancel-click", Map.of(), false)
                .clickEvent(ClickEvent.runCommand("/ph cancel"));
        sender.sendMessage(component);
    }

    public Component component(String key) {
        return component(key, Map.of(), false);
    }

    public Component component(String key, Map<String, String> placeholders, boolean withPrefix) {
        String raw = lang.getString(key, key);
        return deserialize((withPrefix ? prefix : "") + apply(raw, placeholders));
    }

    public List<Component> components(String key, Map<String, String> placeholders, boolean withPrefix) {
        if (lang.isList(key)) {
            return lang.getStringList(key).stream()
                    .map(line -> deserialize((withPrefix ? prefix : "") + apply(line, placeholders)))
                    .toList();
        }
        return List.of(component(key, placeholders, withPrefix));
    }

    public Component legacy(String sectionColorText) {
        return LegacyComponentSerializer.legacySection().deserialize(sectionColorText);
    }

    public String plain(String key) {
        return LegacyComponentSerializer.legacySection().serialize(component(key));
    }

    public Map<String, String> placeholders(Object... values) {
        Map<String, String> map = new HashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), String.valueOf(values[index + 1]));
        }
        return map;
    }

    private Component deserialize(String raw) {
        return miniMessage.deserialize(raw);
    }

    private String apply(String raw, Map<String, String> placeholders) {
        String result = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
