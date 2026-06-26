package me.lovelace.loveHunt.command;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.gui.MenuManager;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.RewardItem;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.model.TypeFilter;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.CitizensIntegration;
import me.lovelace.loveHunt.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LoveHuntCommand implements CommandExecutor, TabCompleter {
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final MenuManager menuManager;
    private final CitizensIntegration citizens;
    private final ConcurrentHashMap<UUID, Long> commandCooldowns = new ConcurrentHashMap<>();

    public LoveHuntCommand(Settings settings, Lang lang, BountyService bountyService, MenuManager menuManager, CitizensIntegration citizens) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.menuManager = menuManager;
        this.citizens = citizens;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return handleAdmin(sender, args);
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("npc")) {
            return handleNpc(sender, args);
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

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lovehunt.admin")) {
            lang.send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            lang.send(sender, "admin-usage");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                settings.load();
                lang.load();
                lang.send(sender, "reload-success");
            }
            case "block" -> handleBlock(sender, args, true);
            case "unblock" -> handleBlock(sender, args, false);
            case "create" -> handleAdminCreate(sender, args);
            default -> lang.send(sender, "admin-usage");
        }
        return true;
    }

    private void handleBlock(CommandSender sender, String[] args, boolean blocked) {
        String usageKey = blocked ? "admin-block-usage" : "admin-unblock-usage";
        if (args.length < 4) {
            lang.send(sender, usageKey);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            lang.send(sender, "admin-unknown-player");
            return;
        }
        String action = args[3].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> bountyService.ratings().setCreateBlocked(target.getUniqueId(), blocked);
            case "accept" -> bountyService.ratings().setAcceptBlocked(target.getUniqueId(), blocked);
            default -> {
                lang.send(sender, usageKey);
                return;
            }
        }
        String playerName = target.getName() == null ? args[2] : target.getName();
        lang.send(sender, blocked ? "admin-block-success" : "admin-unblock-success",
                lang.placeholders("player", playerName, "action", action));
    }

    private void handleAdminCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        BountyType type;
        try {
            type = BountyType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[3]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            lang.send(sender, "admin-unknown-player");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[4]);
        } catch (NumberFormatException exception) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        if (amount < 1) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        Material material = settings.defaultReward().material();
        if (args.length >= 6) {
            Material parsed = Material.matchMaterial(args[5]);
            if (parsed == null || parsed.isAir()) {
                lang.send(sender, "admin-create-usage");
                return;
            }
            material = parsed;
        }
        RewardItem reward = new RewardItem(material, amount, new ItemStack(material, 1), ItemUtil.readableName(material));
        UUID creatorUuid = sender instanceof Player player ? player.getUniqueId() : null;
        String creatorName = sender instanceof Player player ? player.getName() : "Console";
        String clanTag = type == BountyType.CLAN && creatorUuid != null ? bountyService.clans().getClanTag(creatorUuid) : null;
        String targetName = target.getName() == null ? args[3] : target.getName();

        bountyService.createAdminBounty(type, creatorUuid, creatorName, target.getUniqueId(), targetName, clanTag, reward)
                .thenRun(() -> lang.send(sender, "admin-create-success",
                        lang.placeholders("target", targetName, "amount", String.valueOf(amount), "item", reward.displayName())))
                .exceptionally(throwable -> {
                    lang.send(sender, "admin-create-failed");
                    return null;
                });
    }

    private boolean handleNpc(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lovehunt.admin")) {
            lang.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            lang.send(sender, "only-player");
            return true;
        }
        if (!citizens.isAvailable()) {
            lang.send(player, "npc-not-available");
            return true;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("unbind")) {
            settings.setTurnInNpc(-1, "");
            lang.send(player, "npc-unbind-success");
            return true;
        }
        if (sub.equals("bind")) {
            Entity npc = citizens.lookedAtNpc(player, 6.0);
            if (npc == null) {
                lang.send(player, "npc-select-none");
                return true;
            }
            Integer npcId = citizens.npcId(npc);
            String npcName = citizens.npcName(npc);
            settings.setTurnInNpc(npcId == null ? -1 : npcId, npcName == null ? "" : npcName);
            lang.send(player, "npc-bind-success", lang.placeholders("name", npcName == null ? "?" : npcName));
            return true;
        }
        lang.send(player, "npc-not-bound");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("cancel", "create", "admin", "npc");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("reload", "block", "unblock", "create");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return List.of("bind", "unbind");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("admin")
                && (args[1].equalsIgnoreCase("block") || args[1].equalsIgnoreCase("unblock"))) {
            return List.of("create", "accept");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("create")) {
            return List.of("player", "clan", "server");
        }
        return List.of();
    }
}
