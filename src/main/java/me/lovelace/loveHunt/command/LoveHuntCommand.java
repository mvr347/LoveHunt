package me.lovelace.loveHunt.command;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.service.BountyService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoveHuntCommand implements CommandExecutor, TabCompleter {
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final MenuManager menuManager;
    private final ConcurrentHashMap<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();

    public LoveHuntCommand(Settings settings, Lang lang, BountyService bountyService, MenuManager menuManager) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "only-player");
            return true;
        }
        if (!player.hasPermission("lovehunt.use")) {
            lang.send(player, "no-permission");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("cancel")) {
            menuManager.cancelInput(player);
            return true;
        }
        if (!bountyService.isReady()) {
            lang.send(player, "not-ready");
            return true;
        }
        long now = System.currentTimeMillis();
        long last = commandCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < settings.commandCooldownMs()) {
            lang.send(player, "command-cooldown");
            return true;
        }
        commandCooldowns.put(player.getUniqueId(), now);
        menuManager.openMain(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("cancel");
        }
        return List.of();
    }
}
