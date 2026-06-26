package me.lovelace.loveHunt.gui;

import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.InputMode;
import me.lovelace.loveHunt.service.PlayerInput;
import me.lovelace.loveHunt.util.HeadUtil;
import me.lovelace.loveHunt.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuManager {
    private static final int INVENTORY_SIZE = 54;
    private static final int[] MAIN_BUTTONS = {21, 22, 23};
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final BountyService bountyService;
    private final ConcurrentHashMap<UUID, PlayerInput> inputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CreateSession> createSessions = new ConcurrentHashMap<>();

    public MenuManager(JavaPlugin plugin, Settings settings, Lang lang, BountyService bountyService) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.bountyService = bountyService;
    }

    public void openMain(Player player) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.MAIN, 0, SortMode.DATE, false, null, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, lang.component("gui.main-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(MAIN_BUTTONS[0], button("gui.items.all-bounties", Material.COMPASS, "heads.all-bounties-base64", lang.component("gui.main.all")));
        inventory.setItem(MAIN_BUTTONS[1], button("gui.items.my-bounties", Material.PLAYER_HEAD, "heads.my-bounties-base64", lang.component("gui.main.mine")));
        inventory.setItem(MAIN_BUTTONS[2], button("gui.items.create-bounty", Material.WRITABLE_BOOK, "heads.create-bounty-base64", lang.component("gui.main.create")));
        player.openInventory(inventory);
    }

    public void openAll(Player player, int page, SortMode sortMode, boolean onlyMine, String search) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.ALL, Math.max(0, page), sortMode, onlyMine, search, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, lang.component(onlyMine ? "gui.mine-title" : "gui.all-title"));
        holder.inventory(inventory);
        fill(inventory);
        addTopPanel(inventory);

        List<Bounty> bounties = filter(player, sortMode, onlyMine, search);
        int start = holder.page() * 34;
        int end = Math.min(start + 34, bounties.size());
        if (start >= bounties.size() && holder.page() > 0) {
            openAll(player, holder.page() - 1, sortMode, onlyMine, search);
            return;
        }
        int slot = 10;
        for (int index = start; index < end; index++) {
            while (slot <= 43 && inventory.getItem(slot) != null && inventory.getItem(slot).getType() != configMaterial("gui.items.filler", Material.BLACK_STAINED_GLASS_PANE)) {
                slot++;
            }
            if (slot > 43) {
                break;
            }
            Bounty bounty = bounties.get(index);
            inventory.setItem(slot, bountyHead(bounty));
            holder.bountySlots().put(slot, bounty.id());
            slot++;
        }
        if (bounties.isEmpty()) {
            inventory.setItem(22, named(Material.GRAY_DYE, lang.component("gui.all.empty")));
        }
        inventory.setItem(45, named(configMaterial("gui.items.previous", Material.ARROW), lang.component("gui.all.previous")));
        inventory.setItem(53, named(configMaterial("gui.items.next", Material.ARROW), lang.component("gui.all.next")));
        player.openInventory(inventory);
    }

    public void openCreateConfirm(Player player, CreateSession session) {
        createSessions.put(player.getUniqueId(), session);
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_CREATE, 0, SortMode.DATE, false, null, session, 0L);
        Inventory inventory = Bukkit.createInventory(holder, 45, lang.component("gui.confirm-create-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(13, createSummary(session));
        inventory.setItem(30, named(configMaterial("gui.items.cancel", Material.RED_CONCRETE), lang.component("gui.confirm.cancel")));
        inventory.setItem(32, named(configMaterial("gui.items.confirm", Material.LIME_CONCRETE), lang.component("gui.confirm.confirm")));
        player.openInventory(inventory);
    }

    public void openAcceptConfirm(Player player, Bounty bounty) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_ACCEPT, 0, SortMode.DATE, false, null, null, bounty.id());
        Inventory inventory = Bukkit.createInventory(holder, 45, lang.component("gui.confirm-accept-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(13, bountyHead(bounty));
        inventory.setItem(30, named(configMaterial("gui.items.cancel", Material.RED_CONCRETE), lang.component("gui.confirm.cancel")));
        inventory.setItem(32, named(configMaterial("gui.items.confirm", Material.LIME_CONCRETE), lang.component("gui.confirm.confirm")));
        player.openInventory(inventory);
    }

    public void beginCreate(Player player) {
        player.closeInventory();
        inputs.put(player.getUniqueId(), new PlayerInput(InputMode.CREATE_TARGET, 0, false));
        lang.send(player, "input-target");
        lang.sendClickableCancel(player);
    }

    public void beginSearch(Player player, LoveHuntHolder holder) {
        player.closeInventory();
        inputs.put(player.getUniqueId(), new PlayerInput(InputMode.SEARCH, holder.page(), holder.onlyMine()));
        lang.send(player, "input-search");
    }

    public boolean hasInput(Player player) {
        return inputs.containsKey(player.getUniqueId());
    }

    public void cancelInput(Player player) {
        inputs.remove(player.getUniqueId());
        createSessions.remove(player.getUniqueId());
        lang.send(player, "cancelled");
    }

    public void handleChatInput(Player player, String message) {
        PlayerInput input = inputs.remove(player.getUniqueId());
        if (input == null) {
            return;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("отменить") || normalized.equals("cancel") || normalized.equals("отмена")) {
            lang.send(player, "cancelled");
            return;
        }
        if (input.mode() == InputMode.CREATE_TARGET) {
            BountyService.CreateCheck check = bountyService.validateCreation(player, message.trim());
            if (!check.success()) {
                lang.send(player, check.messageKey(), check.placeholders());
                return;
            }
            openCreateConfirm(player, check.session());
            return;
        }
        if (input.mode() == InputMode.SEARCH) {
            openAll(player, 0, SortMode.DATE, input.onlyMine(), message.trim());
        }
    }

    public void handleClick(Player player, LoveHuntHolder holder, int slot) {
        switch (holder.type()) {
            case MAIN -> handleMain(player, slot);
            case ALL -> handleAll(player, holder, slot);
            case CONFIRM_CREATE -> handleCreateConfirm(player, holder, slot);
            case CONFIRM_ACCEPT -> handleAcceptConfirm(player, holder, slot);
        }
    }

    private void handleMain(Player player, int slot) {
        if (slot == MAIN_BUTTONS[0]) {
            openAll(player, 0, SortMode.DATE, false, null);
        } else if (slot == MAIN_BUTTONS[1]) {
            openAll(player, 0, SortMode.DATE, true, null);
        } else if (slot == MAIN_BUTTONS[2]) {
            beginCreate(player);
        }
    }

    private void handleAll(Player player, LoveHuntHolder holder, int slot) {
        if (holder.bountySlots().containsKey(slot)) {
            Bounty bounty = bountyService.get(holder.bountySlots().get(slot));
            if (bounty == null) {
                lang.send(player, "bounty-unavailable");
                openAll(player, holder.page(), holder.sortMode(), holder.onlyMine(), holder.search());
                return;
            }
            openAcceptConfirm(player, bounty);
            return;
        }
        if (slot == 0) {
            openAll(player, 0, SortMode.NAME, holder.onlyMine(), holder.search());
        } else if (slot == 1) {
            openAll(player, 0, SortMode.REWARD, holder.onlyMine(), holder.search());
        } else if (slot == 2) {
            openAll(player, 0, SortMode.DATE, holder.onlyMine(), holder.search());
        } else if (slot == 4) {
            openAll(player, 0, holder.sortMode(), true, holder.search());
        } else if (slot == 5) {
            openAll(player, 0, SortMode.DATE, false, null);
        } else if (slot == 6) {
            beginSearch(player, holder);
        } else if (slot == 45 && holder.page() > 0) {
            openAll(player, holder.page() - 1, holder.sortMode(), holder.onlyMine(), holder.search());
        } else if (slot == 53) {
            openAll(player, holder.page() + 1, holder.sortMode(), holder.onlyMine(), holder.search());
        }
    }

    private void handleCreateConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 30) {
            createSessions.remove(player.getUniqueId());
            player.closeInventory();
            lang.send(player, "cancelled");
            return;
        }
        if (slot != 32) {
            return;
        }
        CreateSession session = createSessions.remove(player.getUniqueId());
        if (session == null) {
            session = holder.createSession();
        }
        if (session == null) {
            player.closeInventory();
            return;
        }
        player.closeInventory();
        CreateSession finalSession = session;
        bountyService.createPlayerBounty(player, session).thenAccept(bounty -> Bukkit.getScheduler().runTask(plugin, () ->
                lang.send(player, "create-success", lang.placeholders("target", finalSession.targetName(), "amount", finalSession.reward().amount(), "item", finalSession.reward().displayName()))))
                .exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> lang.send(player, "create-failed"));
                    return null;
                });
    }

    private void handleAcceptConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 30) {
            openAll(player, 0, SortMode.DATE, false, null);
            return;
        }
        if (slot != 32) {
            return;
        }
        Bounty bounty = bountyService.get(holder.bountyId());
        player.closeInventory();
        if (bounty == null) {
            lang.send(player, "bounty-unavailable");
            return;
        }
        if (bountyService.hasAccepted(player.getUniqueId(), bounty.id())) {
            lang.send(player, "already-accepted");
            return;
        }
        bountyService.accept(player, bounty).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                lang.send(player, "accept-failed");
                return;
            }
            giveCompass(player, bounty);
            lang.send(player, "accept-success", lang.placeholders("target", bounty.targetName()));
        }));
    }

    private void giveCompass(Player player, Bounty bounty) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.legacy("§6Розыск: §f" + bounty.targetName()).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(me.lovelace.loveHunt.LoveHunt.TARGET_KEY, org.bukkit.persistence.PersistentDataType.STRING, bounty.targetUuid().toString());
            meta.getPersistentDataContainer().set(me.lovelace.loveHunt.LoveHunt.BOUNTY_KEY, org.bukkit.persistence.PersistentDataType.LONG, bounty.id());
            compass.setItemMeta(meta);
        }
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(compass);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            lang.send(player, "inventory-drop");
        }
    }

    private List<Bounty> filter(Player player, SortMode sortMode, boolean onlyMine, String search) {
        String normalizedSearch = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<Bounty> list = (onlyMine ? bountyService.byCreator(player.getUniqueId()) : bountyService.allActive()).stream()
                .filter(bounty -> normalizedSearch.isBlank() || bounty.targetName().toLowerCase(Locale.ROOT).contains(normalizedSearch))
                .toList();
        Comparator<Bounty> comparator = switch (sortMode) {
            case NAME -> Comparator.comparing(Bounty::targetName, String.CASE_INSENSITIVE_ORDER);
            case REWARD -> Comparator.comparingInt((Bounty bounty) -> bounty.reward().amount()).reversed();
            case DATE -> Comparator.comparingLong(Bounty::createdAt).reversed();
        };
        return list.stream().sorted(comparator).toList();
    }

    private void addTopPanel(Inventory inventory) {
        inventory.setItem(0, named(configMaterial("gui.items.sort-name", Material.NAME_TAG), lang.component("gui.all.sort-name")));
        inventory.setItem(1, named(configMaterial("gui.items.sort-reward", Material.EMERALD), lang.component("gui.all.sort-reward")));
        inventory.setItem(2, named(configMaterial("gui.items.sort-date", Material.CLOCK), lang.component("gui.all.sort-date")));
        inventory.setItem(4, named(configMaterial("gui.items.only-mine", Material.CHEST), lang.component("gui.all.only-mine")));
        inventory.setItem(5, named(configMaterial("gui.items.reset", Material.BARRIER), lang.component("gui.all.reset")));
        inventory.setItem(6, named(configMaterial("gui.items.search", Material.OAK_SIGN), lang.component("gui.all.search")));
    }

    private ItemStack bountyHead(Bounty bounty) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.targetUuid());
        ItemStack head = HeadUtil.playerHead(target);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.legacy("§6" + bounty.targetName()).decoration(TextDecoration.ITALIC, false));
            String creator = switch (bounty.type()) {
                case PLAYER -> bounty.creatorName() == null ? "§4Сервер" : "§f" + bounty.creatorName();
                case CLAN -> "§cКлан: " + bounty.clanTag();
                case SERVER -> "§4Сервер";
            };
            boolean online = Bukkit.getPlayer(bounty.targetUuid()) != null;
            String left = bounty.expiresAt() == null ? "∞" : TimeUtil.compact(bounty.expiresAt() - System.currentTimeMillis());
            meta.lore(List.of(
                    lang.legacy("§7Онлайн: " + (online ? "§aДа" : "§cНет")),
                    lang.legacy("§7Выставил: " + creator),
                    lang.legacy("§7Награда: §e" + bounty.reward().amount() + "× " + bounty.reward().displayName()),
                    lang.legacy("§7Осталось: §f" + left),
                    lang.legacy("§8ID: #" + bounty.id())
            ));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createSummary(CreateSession session) {
        ItemStack item = session.reward().singlePrototype();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.component("gui.confirm.create-name").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    lang.legacy("§7Цель: §6" + session.targetName()),
                    lang.legacy("§7Награда: §e" + session.reward().amount() + "× " + session.reward().displayName()),
                    lang.legacy("§7Срок действия: §f" + Duration.ofDays(settings.creationDurationDays()).toDays() + " дней")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = named(configMaterial("gui.items.filler", Material.BLACK_STAINED_GLASS_PANE), Component.empty());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        return named(item, name);
    }

    private ItemStack named(ItemStack item, Component name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(String materialPath, Material fallback, String base64Path, Component name) {
        if (plugin.getConfig().getBoolean("heads.use-base64-for-buttons", false)) {
            String base64 = plugin.getConfig().getString(base64Path, "");
            if (base64 != null && !base64.isBlank()) {
                return named(HeadUtil.base64Head(base64), name);
            }
        }
        return named(configMaterial(materialPath, fallback), name);
    }

    private Material configMaterial(String path, Material fallback) {
        Material material = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name()));
        return material == null ? fallback : material;
    }
}
