package me.lovelace.loveHunt.command;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.model.HunterRating;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.model.TypeFilter;
import me.lovelace.loveHunt.service.BountyService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        // Админ-команды (reload, block/unblock, admin-create, npc bind/unbind) переехали под
        // единую /lovehuntadmin — здесь остаётся только понятная подсказка, чтобы команда не
        // «молчала» для тех, кто по привычке набирает /lovehunt admin или /lovehunt npc.
        if (args.length > 0 && (args[0].equalsIgnoreCase("admin") || args[0].equalsIgnoreCase("npc"))) {
            if (sender.hasPermission("lovehunt.admin")) {
                lang.send(sender, "admin-moved");
            } else {
                lang.send(sender, "no-permission");
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats")) {
            return handleStats(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            return handleTop(sender);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
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
        if (args.length > 0 && args[0].equalsIgnoreCase("create")) {
            menuManager.beginCreate(player);
        } else if (label.equalsIgnoreCase("hunts")) {
            menuManager.openAll(player, 0, SortMode.DATE, TypeFilter.ALL, false, false, null);
        } else {
            menuManager.openMain(player);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        lang.send(sender, "help-header");
        lang.send(sender, "help-main");
        lang.send(sender, "help-hunts");
        lang.send(sender, "help-create");
        lang.send(sender, "help-cancel");
        lang.send(sender, "help-stats");
        lang.send(sender, "help-top");
        if (sender.hasPermission("lovehunt.admin")) {
            lang.send(sender, "help-lovehuntadmin");
        }
        lang.send(sender, "help-footer");
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.hasPermission("lovehunt.use")) {
            lang.send(sender, "no-permission");
            return true;
        }
        OfflinePlayer target;
        String requestedName = args.length >= 2 ? args[1] : null;
        if (requestedName != null) {
            target = Bukkit.getOfflinePlayer(requestedName);
            if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                lang.send(sender, "admin-unknown-player");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            lang.send(sender, "stats-usage");
            return true;
        }
        HunterRating rating = bountyService.ratings().get(target.getUniqueId());
        String name = target.getName() == null ? (requestedName == null ? "?" : requestedName) : target.getName();
        lang.send(sender, "stats-header", lang.placeholders("player", name));
        lang.send(sender, "stats-line", lang.placeholders(
                "rating", String.format(Locale.ROOT, "%.1f", rating.rating()),
                "completed", String.valueOf(rating.completed()),
                "failed", String.valueOf(rating.failed())));
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        if (sender instanceof Player player && !player.hasPermission("lovehunt.use")) {
            lang.send(sender, "no-permission");
            return true;
        }
        Map<UUID, HunterRating> snapshot = bountyService.ratings().snapshot();
        List<HunterRating> top = snapshot.values().stream()
                .filter(rating -> rating.completed() > 0 || rating.failed() > 0)
                .sorted(Comparator.comparingDouble(HunterRating::rating).reversed()
                        .thenComparing(Comparator.comparingInt(HunterRating::completed).reversed()))
                .limit(10)
                .toList();
        if (top.isEmpty()) {
            lang.send(sender, "top-empty");
            return true;
        }
        lang.send(sender, "top-header");
        int rank = 1;
        for (HunterRating rating : top) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(rating.uuid());
            String name = player.getName() == null ? rating.uuid().toString() : player.getName();
            lang.send(sender, "top-entry", lang.placeholders(
                    "rank", String.valueOf(rank++),
                    "player", name,
                    "rating", String.format(Locale.ROOT, "%.1f", rating.rating()),
                    "completed", String.valueOf(rating.completed()),
                    "failed", String.valueOf(rating.failed())));
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player) || !player.hasPermission("lovehunt.use")) {
                return List.of("stats", "top", "help");
            }
            return List.of("cancel", "create", "stats", "top", "help");
        }
        return List.of();
    }
}
