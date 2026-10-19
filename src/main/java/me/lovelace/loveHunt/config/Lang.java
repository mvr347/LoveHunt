package me.lovelace.loveHunt.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import me.lovelace.loveHunt.util.Notify;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        YamlConfiguration bundled = loadBundled();
        if (bundled != null) {
            migrate(file, bundled);
            // Keys missing from the server file fall back to the bundled defaults (a server that updated the plugin keeps
            // its old file, so new keys would otherwise show as raw key names).
            lang.setDefaults(bundled);
        }
        prefix = lang.getString("prefix", "");
    }

    /**
     * Takes the reworded sections of the jar's lang.yml into an older server file (see {@link LangMigrator}). The previous
     * file stays next to it as {@code lang.yml.v<old version>.bak}, so an admin's edits are never lost.
     */
    private void migrate(File file, YamlConfiguration bundled) {
        int before = lang.getInt(LangMigrator.VERSION_KEY, 1);
        if (!LangMigrator.migrate(lang, bundled)) {
            return;
        }
        File backup = new File(plugin.getDataFolder(), "lang.yml.v" + before + ".bak");
        try {
            if (!backup.exists()) {
                Files.copy(file.toPath(), backup.toPath());
            }
            lang.save(file);
            plugin.getLogger().info("lang.yml updated to version " + LangMigrator.latestVersion()
                    + " (previous file kept as " + backup.getName() + ")");
        } catch (IOException e) {
            // The new texts still work in memory; the file is left as it was so nothing is lost.
            plugin.getLogger().warning("Could not save the updated lang.yml: " + e.getMessage());
        }
    }

    private YamlConfiguration loadBundled() {
        try (InputStream in = plugin.getResource("lang.yml")) {
            return in == null ? null : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled lang.yml defaults: " + e.getMessage());
            return null;
        }
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
     * Event-style notice (action bar by default, chat fallback decided by LoveNotify). Command replies,
     * input errors and prompts must keep using {@link #send}.
     */
    public void notice(Player player, String key) {
        notice(player, key, Map.of());
    }

    public void notice(Player player, String key, Map<String, String> placeholders) {
        Notify.event(player, component(key, placeholders, false));
    }

    /** Critical notice (reward/escrow moved, bounty cancelled): LoveNotify always duplicates it to chat. */
    public void important(Player player, String key) {
        important(player, key, Map.of());
    }

    public void important(Player player, String key, Map<String, String> placeholders) {
        Notify.important(player, component(key, placeholders, false));
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

    /** Raw MiniMessage text of {@code key} with placeholders filled in, not parsed (lore mixes it with LoreBlocks lines). */
    public String raw(String key, Map<String, String> placeholders) {
        return apply(lang.getString(key, key), placeholders);
    }

    /** Raw lines of {@code key}: the list when the key is a list, otherwise its single text. Placeholders filled in. */
    public List<String> rawLines(String key, Map<String, String> placeholders) {
        if (lang.isList(key)) {
            return lang.getStringList(key).stream().map(line -> apply(line, placeholders)).toList();
        }
        return List.of(raw(key, placeholders));
    }

    /** Raw text of {@code key} or null when it is not in lang.yml (lets LoreBlocks use its own built-in default). */
    public String rawOrNull(String key) {
        return lang.getString(key);
    }

    /** Plain text (no colours, no prefix) of a chat message, for a reason line inside lore. */
    public String plainText(String key, Map<String, String> placeholders) {
        return PlainTextComponentSerializer.plainText().serialize(component(key, placeholders, false));
    }

    /** Escapes MiniMessage tags in text that is put into a MiniMessage string as it is. */
    public String escape(String text) {
        return miniMessage.escapeTags(text);
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
