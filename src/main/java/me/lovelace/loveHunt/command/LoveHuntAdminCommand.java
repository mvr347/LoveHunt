package me.lovelace.loveHunt.command;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.RewardItem;
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

/**
 * Единая административная команда плагина: {@code /lovehuntadmin <subcommand>}.
 * <p>
 * Раньше admin-функции (reload, block/unblock, admin-create, привязка NPC) были зарыты внутри
 * {@code /lovehunt admin ...} и {@code /lovehunt npc ...}, что не соответствует принятому в
 * экосистеме Love* стилю единой родительской admin-команды с подкомандами. Старые пути
 * по-прежнему перехватываются в {@link LoveHuntCommand} и просто указывают сюда, чтобы игрок,
 * набравший команду по привычке, не остался без ответа.
 */
public final class LoveHuntAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload", "block", "unblock", "create", "npc", "help");
    private static final List<String> RELOAD_TYPES = List.of("all", "config", "message");
    private static final List<String> BLOCK_ACTIONS = List.of("create", "accept");
    private static final List<String> CREATE_TYPES = List.of("player", "clan", "server");
    private static final List<String> NPC_ACTIONS = List.of("bind", "unbind");

    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final CitizensIntegration citizens;

    public LoveHuntAdminCommand(Settings settings, Lang lang, BountyService bountyService, CitizensIntegration citizens) {
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
        this.citizens = citizens;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lovehunt.admin")) {
            lang.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender, args);
            case "block" -> handleBlock(sender, args, true);
            case "unblock" -> handleBlock(sender, args, false);
            case "create" -> handleCreate(sender, args);
            case "npc" -> handleNpc(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "all";
        switch (type) {
            case "config" -> {
                settings.load();
                lang.send(sender, "reload-success");
            }
            case "message", "messages" -> {
                lang.load();
                lang.send(sender, "reload-messages-success");
            }
            default -> {
                settings.load();
                lang.load();
                lang.send(sender, "reload-success");
            }
        }
    }

    private void handleBlock(CommandSender sender, String[] args, boolean blocked) {
        String usageKey = blocked ? "admin-block-usage" : "admin-unblock-usage";
        if (args.length < 3) {
            lang.send(sender, usageKey);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            lang.send(sender, "admin-unknown-player");
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> bountyService.ratings().setCreateBlocked(target.getUniqueId(), blocked);
            case "accept" -> bountyService.ratings().setAcceptBlocked(target.getUniqueId(), blocked);
            default -> {
                lang.send(sender, usageKey);
                return;
            }
        }
        String playerName = target.getName() == null ? args[1] : target.getName();
        lang.send(sender, blocked ? "admin-block-success" : "admin-unblock-success",
                lang.placeholders("player", playerName, "action", action));
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        BountyType type;
        try {
            type = BountyType.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            lang.send(sender, "admin-unknown-player");
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        if (amount < 1) {
            lang.send(sender, "admin-create-usage");
            return;
        }
        Material material = settings.defaultReward().material();
        if (args.length >= 5) {
            Material parsed = Material.matchMaterial(args[4]);
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
        String targetName = target.getName() == null ? args[2] : target.getName();

        bountyService.createAdminBounty(type, creatorUuid, creatorName, target.getUniqueId(), targetName, clanTag, reward)
                .thenRun(() -> lang.send(sender, "admin-create-success",
                        lang.placeholders("target", targetName, "amount", String.valueOf(amount), "item", reward.displayName())))
                .exceptionally(throwable -> {
                    lang.send(sender, "admin-create-failed");
                    return null;
                });
    }

    private void handleNpc(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "only-player");
            return;
        }
        if (!citizens.isAvailable()) {
            lang.send(player, "npc-not-available");
            return;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (sub.equals("unbind")) {
            settings.setTurnInNpc(-1, "");
            lang.send(player, "npc-unbind-success");
            return;
        }
        if (sub.equals("bind")) {
            Entity npc = citizens.lookedAtNpc(player, 6.0);
            if (npc == null) {
                lang.send(player, "npc-select-none");
                return;
            }
            Integer npcId = citizens.npcId(npc);
            String npcName = citizens.npcName(npc);
            settings.setTurnInNpc(npcId == null ? -1 : npcId, npcName == null ? "" : npcName);
            lang.send(player, "npc-bind-success", lang.placeholders("name", npcName == null ? "?" : npcName));
            return;
        }
        lang.send(player, "npc-not-bound");
    }

    private void sendHelp(CommandSender sender) {
        lang.send(sender, "admin-help-header");
        lang.send(sender, "admin-help-reload");
        lang.send(sender, "admin-help-block");
        lang.send(sender, "admin-help-unblock");
        lang.send(sender, "admin-help-create");
        lang.send(sender, "admin-help-npc");
        lang.send(sender, "admin-help-footer");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lovehunt.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            return RELOAD_TYPES;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return CREATE_TYPES;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return NPC_ACTIONS;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("block") || args[0].equalsIgnoreCase("unblock"))) {
            return BLOCK_ACTIONS;
        }
        return List.of();
    }
}
