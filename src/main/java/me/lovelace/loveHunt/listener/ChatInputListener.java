package me.lovelace.loveHunt.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.lovelace.loveHunt.gui.MenuManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChatInputListener implements Listener {
    private final JavaPlugin plugin;
    private final MenuManager menuManager;

    public ChatInputListener(JavaPlugin plugin, MenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!menuManager.hasInput(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> menuManager.handleChatInput(player, message));
    }
}
