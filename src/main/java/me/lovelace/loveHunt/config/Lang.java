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
import java.util.Random;

public final class Lang {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Random random = new Random();
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
        // saveResource only writes lang.yml when it does not exist yet, so a server that updated the plugin keeps
        // an old file without the new keys and players saw the raw key ("gui.sort...") instead of text. Keys
        // missing from the server file now fall back to the bundled defaults (the file itself is not modified).
        try (java.io.InputStream bundled = plugin.getResource("lang.yml")) {
            if (bundled != null) {
                lang.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(bundled, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read bundled lang.yml defaults: " + e.getMessage());
        }
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

    /**
     * Отправляет одну случайную строку из списка ключа {@code key}. Ничего не делает и
     * возвращает false, если ключ в lang.yml не задан как список (например, старый lang.yml
     * без новых ключей) — иначе игрок увидел бы сырой текст ключа вместо сообщения.
     *
     * @return true, если сообщение реально было отправлено
     */
    public boolean sendRandom(CommandSender sender, String key) {
        if (!lang.isList(key)) {
            return false;
        }
        List<Component> options = components(key, Map.of(), true);
        if (options.isEmpty()) {
            return false;
        }
        sender.sendMessage(options.get(random.nextInt(options.size())));
        return true;
    }

    public void sendClickableCancel(CommandSender sender) {
        Component component = component("cancel-click", Map.of(), false)
                .clickEvent(ClickEvent.runCommand("/lovehunt cancel"));
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

    /** Raw MiniMessage string of {@code key} (bundled default, then the key itself when missing). */
    public String plainMini(String key) {
        return lang.getString(key, key);
    }

    public Component mini(String raw) {
        return deserialize(raw);
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
