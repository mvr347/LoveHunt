package me.lovelace.loveHunt.gui;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.config.Heads;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.gui.LoreBlocks.Click;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.HunterRating;
import me.lovelace.loveHunt.model.ClanOnlineMode;
import me.lovelace.loveHunt.model.RewardItem;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.model.TypeFilter;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.InputMode;
import me.lovelace.loveHunt.service.PlayerInput;
import me.lovelace.loveHunt.service.RatingService;
import me.lovelace.loveHunt.util.HeadUtil;
import me.lovelace.loveHunt.util.ItemBuilder;
import me.lovelace.loveHunt.util.LoreBar;
import me.lovelace.loveHunt.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MenuManager {
    private static final int ROW = 9;
    private static final int MAX_ITEM_ROWS = 4;
    private static final int PAGE_CAPACITY = ROW * MAX_ITEM_ROWS;
    private static final int MAIN_SIZE = 27;
    private static final int MINE_MAIN_BUTTON = 11;
    private static final int ALL_MAIN_BUTTON = 15;
    private static final int PASS_BUTTON = 13;

    private static final int MANAGE_SIZE = 54;
    private static final int MANAGE_TARGET = 0;
    private static final int MANAGE_CANCEL = 50;
    private static final int MANAGE_EXTEND = 51;
    private static final int MANAGE_BACK = 52;
    private static final int MANAGE_CLOSE = 53;

    private static final String FILTER_HEAD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGViODFlZjg5MDIzNzk2NTBiYTc5ZjQ1NzIzZDZiOWM4ODgzODhhMDBmYzRlMTkyZjM0NTRmZTE5Mzg4MmVlMSJ9fX0=";

    /** Progress of a rating (0..5) is drawn as a bar of 500 units: 1 rating point = 20 %. */
    private static final long RATING_BAR_MAX = 500L;

    // Header control buttons (sort, type filter, clan/online): "mine" has 2, "all" has 3.
    private static final int[] MINE_CONTROLS = controlSlots(2);
    private static final int[] ALL_CONTROLS = controlSlots(3);

    private static final int[] CONTENT_SLOTS = {
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final Heads heads;
    private final BountyService bountyService;
    private final LoreBlocks.Style style;
    private final ConcurrentHashMap<UUID, PlayerInput> inputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CreateSession> createSessions = new ConcurrentHashMap<>();

    /**
     * Why an action is unavailable: the chat message key that already explains it, and its placeholders. The same key
     * gives the {@code Причина:} line of a disabled button.
     */
    private record Refusal(String key, Map<String, String> placeholders) {
    }

    public MenuManager(JavaPlugin plugin, Settings settings, Lang lang, Heads heads, BountyService bountyService) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.heads = heads;
        this.bountyService = bountyService;
        this.style = lang::rawOrNull;
    }

    public void openMain(Player player) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.MAIN, MenuType.MAIN, 0, SortMode.DATE, TypeFilter.ALL, false, false, null, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MAIN_SIZE, lang.component("gui.main-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, statsButton(player));
        inventory.setItem(MINE_MAIN_BUTTON, button("gui.items.my-bounties", Material.PLAYER_HEAD, "my-bounties-base64",
                lang.component("gui.main.mine"), lore("gui.main.mine-lore", Map.of(), left("gui.main.mine-action"))));
        inventory.setItem(ALL_MAIN_BUTTON, button("gui.items.all-bounties", Material.COMPASS, "all-bounties-base64",
                lang.component("gui.main.all"), lore("gui.main.all-lore", Map.of(), left("gui.main.all-action"))));
        inventory.setItem(PASS_BUTTON, passButton(player));
        inventory.setItem(25, createButton(player));
        inventory.setItem(MAIN_SIZE - 1, closeButton());
        player.openInventory(inventory);
    }

    public void openMine(Player player, int page, SortMode sortMode, TypeFilter typeFilter, String search) {
        List<Bounty> bounties = filterMine(player, sortMode, typeFilter, search);
        PageSlice slice = paginate(bounties, page);
        if (slice == null) {
            openMine(player, page - 1, sortMode, typeFilter, search);
            return;
        }
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.MINE, MenuType.MINE, slice.page(), sortMode, typeFilter, false, false, search, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MANAGE_SIZE, lang.component("gui.mine-title"));
        holder.inventory(inventory);
        fill(inventory);
        addMineHeader(inventory, sortMode, typeFilter, player);
        placeItems(inventory, holder, bounties, slice, player);
        addFooter(inventory, slice, player);
        if (bounties.isEmpty()) {
            inventory.setItem(31, emptyNoticeHead(lang.component("gui.mine.empty"), lore("gui.mine.empty-lore", Map.of(), List.of())));
        }
        player.openInventory(inventory);
    }

    public void openAll(Player player, int page, SortMode sortMode, TypeFilter typeFilter, boolean onlyMyClan, boolean onlineOnly, String search) {
        boolean clanFilter = onlyMyClan && clanFilterAvailable(player);
        List<Bounty> bounties = filterAll(player, sortMode, typeFilter, clanFilter, onlineOnly, search);
        PageSlice slice = paginate(bounties, page);
        if (slice == null) {
            openAll(player, page - 1, sortMode, typeFilter, clanFilter, onlineOnly, search);
            return;
        }
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.ALL, MenuType.ALL, slice.page(), sortMode, typeFilter, clanFilter, onlineOnly, search, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MANAGE_SIZE, lang.component("gui.all-title"));
        holder.inventory(inventory);
        fill(inventory);
        addAllHeader(inventory, sortMode, typeFilter, clanFilter, onlineOnly, player);
        placeItems(inventory, holder, bounties, slice, player);
        addFooter(inventory, slice, player);
        if (bounties.isEmpty()) {
            inventory.setItem(31, emptyNoticeHead(lang.component("gui.all.empty"), lore("gui.all.empty-lore", Map.of(), List.of())));
        }
        player.openInventory(inventory);
    }

    public void openCreateConfirm(Player player, CreateSession session) {
        createSessions.put(player.getUniqueId(), session);
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_CREATE, MenuType.MAIN, 0, SortMode.DATE, TypeFilter.ALL, false, false, null, session, 0L);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, lang.component("gui.confirm-create-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, confirmButton("gui.confirm.create-lore", "gui.confirm.create-action"));
        inventory.setItem(2, createSummary(session));
        inventory.setItem(4, cancelActionButton());
        player.openInventory(inventory);
    }

    public void openAcceptConfirm(Player player, LoveHuntHolder origin, Bounty bounty) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_ACCEPT, origin.returnType(), origin.page(), origin.sortMode(), origin.typeFilter(),
                origin.onlyMyClan(), origin.onlineOnly(), origin.search(), null, bounty.id());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, lang.component("gui.confirm-accept-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, confirmButton("gui.confirm.accept-lore", "gui.confirm.accept-action"));
        inventory.setItem(2, bountyHead(bounty, player, false));
        inventory.setItem(4, cancelActionButton());
        player.openInventory(inventory);
    }

    public void openCancelConfirm(Player player, LoveHuntHolder origin, Bounty bounty) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_CANCEL, origin.returnType(), origin.page(), origin.sortMode(), origin.typeFilter(),
                origin.onlyMyClan(), origin.onlineOnly(), origin.search(), null, bounty.id());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, lang.component("gui.confirm-cancel-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, confirmButton("gui.confirm.delete-lore", "gui.confirm.delete-action"));
        inventory.setItem(2, bountyHead(bounty, player, false));
        inventory.setItem(4, cancelActionButton());
        player.openInventory(inventory);
    }

    public void openManage(Player player, LoveHuntHolder origin, Bounty bounty) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.MANAGE, origin.returnType(), origin.page(), origin.sortMode(), origin.typeFilter(),
                origin.onlyMyClan(), origin.onlineOnly(), origin.search(), null, bounty.id());
        List<UUID> hunters = bountyService.huntersOf(bounty.id());
        int size = hunters.isEmpty() ? 27 : MANAGE_SIZE;
        Inventory inventory = Bukkit.createInventory(holder, size, lang.component("gui.manage-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(MANAGE_TARGET, bountyHead(bounty, player, false));

        if (hunters.isEmpty()) {
            inventory.setItem(13, emptyNoticeHead(lang.component("gui.manage-no-hunters"), lore("gui.manage-no-hunters-lore", Map.of(), List.of())));
            inventory.setItem(23, cancelOrderButton());
            if (bounty.type() == BountyType.PLAYER || bounty.type() == BountyType.CLAN) {
                inventory.setItem(24, extendButton(player, bounty));
            }
            inventory.setItem(25, backButton());
            inventory.setItem(26, closeButton());
        } else {
            inventory.setItem(MANAGE_CANCEL, cancelOrderButton());
            if (bounty.type() == BountyType.PLAYER || bounty.type() == BountyType.CLAN) {
                inventory.setItem(MANAGE_EXTEND, extendButton(player, bounty));
            }
            inventory.setItem(MANAGE_BACK, backButton());
            inventory.setItem(MANAGE_CLOSE, closeButton());

            int hunterIndex = 0;
            for (UUID hunterUuid : hunters) {
                if (hunterIndex >= CONTENT_SLOTS.length) break;
                inventory.setItem(CONTENT_SLOTS[hunterIndex++], hunterHead(hunterUuid));
            }
        }
        player.openInventory(inventory);
    }

    public void beginCreate(Player player) {
        player.closeInventory();
        inputs.put(player.getUniqueId(), new PlayerInput(InputMode.CREATE_TARGET, MenuType.MAIN, 0, SortMode.DATE, TypeFilter.ALL, false, false));
        lang.send(player, "input-target");
        lang.sendClickableCancel(player);
    }

    public void beginSearch(Player player, LoveHuntHolder holder) {
        player.closeInventory();
        inputs.put(player.getUniqueId(), new PlayerInput(InputMode.SEARCH, holder.type(), holder.page(), holder.sortMode(), holder.typeFilter(),
                holder.onlyMyClan(), holder.onlineOnly()));
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
        if (input == null) return;
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
            if (input.returnType() == MenuType.MINE) openMine(player, 0, input.sortMode(), input.typeFilter(), message.trim());
            else openAll(player, 0, input.sortMode(), input.typeFilter(), input.onlyMyClan(), input.onlineOnly(), message.trim());
        }
    }

    public void handleClick(Player player, LoveHuntHolder holder, int slot) {
        handleClick(player, holder, slot, false);
    }

    /** {@code rightClick} steps header switches backwards (members-menu style: LMB next, RMB previous). */
    public void handleClick(Player player, LoveHuntHolder holder, int slot, boolean rightClick) {
        switch (holder.type()) {
            case MAIN -> handleMain(player, slot);
            case MINE -> handleMine(player, holder, slot, rightClick);
            case ALL -> handleAll(player, holder, slot, rightClick);
            case CONFIRM_CREATE -> handleCreateConfirm(player, holder, slot);
            case CONFIRM_ACCEPT -> handleAcceptConfirm(player, holder, slot);
            case CONFIRM_CANCEL -> handleCancelConfirm(player, holder, slot);
            case MANAGE -> handleManage(player, holder, slot);
        }
    }

    private void handleMain(Player player, int slot) {
        if (slot == MINE_MAIN_BUTTON) openMine(player, 0, SortMode.DATE, TypeFilter.ALL, null);
        else if (slot == ALL_MAIN_BUTTON) openAll(player, 0, SortMode.DATE, TypeFilter.ALL, false, false, null);
        else if (slot == PASS_BUTTON) buyPass(player);
        else if (slot == 25) beginCreate(player);
        else if (slot == MAIN_SIZE - 1) player.closeInventory();
    }

    private void handleMine(Player player, LoveHuntHolder holder, int slot, boolean rightClick) {
        if (holder.bountySlots().containsKey(slot)) {
            openBountyAction(player, holder, holder.bountySlots().get(slot));
            return;
        }
        if (slot == MINE_CONTROLS[0]) openMine(player, 0, rightClick ? holder.sortMode().previous() : holder.sortMode().next(), holder.typeFilter(), holder.search());
        else if (slot == MINE_CONTROLS[1]) openMine(player, 0, holder.sortMode(), rightClick ? holder.typeFilter().previous() : holder.typeFilter().next(), holder.search());
        else if (slot == 36 && holder.page() > 0) openMine(player, holder.page() - 1, holder.sortMode(), holder.typeFilter(), holder.search());
        else if (slot == 44) openMine(player, holder.page() + 1, holder.sortMode(), holder.typeFilter(), holder.search());
        else if (slot == 51) beginCreate(player);
        else if (slot == 52) openMain(player);
        else if (slot == 53) player.closeInventory();
    }

    private void handleAll(Player player, LoveHuntHolder holder, int slot, boolean rightClick) {
        if (holder.bountySlots().containsKey(slot)) {
            openBountyAction(player, holder, holder.bountySlots().get(slot));
            return;
        }
        if (slot == ALL_CONTROLS[0]) openAll(player, 0, rightClick ? holder.sortMode().previous() : holder.sortMode().next(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == ALL_CONTROLS[1]) openAll(player, 0, holder.sortMode(), rightClick ? holder.typeFilter().previous() : holder.typeFilter().next(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == ALL_CONTROLS[2]) {
            // "My clan" options are skipped for clanless players instead of showing an error.
            ClanOnlineMode current = ClanOnlineMode.of(holder.onlyMyClan(), holder.onlineOnly());
            boolean hasClan = clanFilterAvailable(player);
            ClanOnlineMode next = rightClick ? current.previous(hasClan) : current.next(hasClan);
            openAll(player, 0, holder.sortMode(), holder.typeFilter(), next.onlyMyClan(), next.onlineOnly(), holder.search());
        } else if (slot == 36 && holder.page() > 0) openAll(player, holder.page() - 1, holder.sortMode(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == 44) openAll(player, holder.page() + 1, holder.sortMode(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == 51) beginCreate(player);
        else if (slot == 52) openMain(player);
        else if (slot == 53) player.closeInventory();
    }

    private void openBountyAction(Player player, LoveHuntHolder origin, long bountyId) {
        Bounty bounty = bountyService.get(bountyId);
        if (bounty == null) {
            lang.notice(player, "bounty-unavailable");
            reopen(player, origin);
            return;
        }
        boolean own = (bounty.type() == BountyType.PLAYER || bounty.type() == BountyType.CLAN)
                && bounty.creatorUuid() != null && bounty.creatorUuid().equals(player.getUniqueId());
        if (own) openManage(player, origin, bounty);
        else openAcceptConfirm(player, origin, bounty);
    }

    private void handleManage(Player player, LoveHuntHolder holder, int slot) {
        int invSize = holder.getInventory() != null ? holder.getInventory().getSize() : MANAGE_SIZE;
        int closeSlot = invSize == 27 ? 26 : MANAGE_CLOSE;
        int backSlot = invSize == 27 ? 25 : MANAGE_BACK;
        int extendSlot = invSize == 27 ? 24 : MANAGE_EXTEND;
        int cancelSlot = invSize == 27 ? 23 : MANAGE_CANCEL;

        if (slot == closeSlot) { player.closeInventory(); return; }
        if (slot == backSlot) { reopen(player, holder); return; }
        Bounty bounty = bountyService.get(holder.bountyId());
        if (bounty == null) {
            lang.notice(player, "bounty-unavailable");
            reopen(player, holder);
            return;
        }
        if (slot == cancelSlot) {
            openCancelConfirm(player, holder, bounty);
            return;
        }
        if (slot == extendSlot) {
            BountyService.ExtendResult result = bountyService.extend(player, bounty);
            if (!result.success()) {
                lang.send(player, result.messageKey(), result.placeholders());
                return;
            }
            lang.important(player, "extend-success", lang.placeholders("amount", String.valueOf(result.cost()), "item", result.bounty().reward().displayName()));
            openManage(player, holder, result.bounty());
        }
    }

    private void handleCreateConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 4) {
            createSessions.remove(player.getUniqueId());
            player.closeInventory();
            lang.send(player, "cancelled");
            return;
        }
        if (slot != 0) return;
        CreateSession session = createSessions.remove(player.getUniqueId());
        if (session == null) session = holder.createSession();
        if (session == null) { player.closeInventory(); return; }
        player.closeInventory();
        CreateSession finalSession = session;
        bountyService.createPlayerBounty(player, session).thenAccept(bounty -> Bukkit.getScheduler().runTask(plugin, () ->
                lang.important(player, "create-success", lang.placeholders("target", finalSession.targetName(), "amount", finalSession.reward().amount(), "item", finalSession.reward().displayName()))))
                .exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> lang.notice(player, "create-failed"));
                    return null;
                });
    }

    private void handleAcceptConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 4) { reopen(player, holder); return; }
        if (slot != 0) return;
        Bounty bounty = bountyService.get(holder.bountyId());
        bountyService.accept(player, bounty).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (success) {
                lang.notice(player, "accept-success");
                player.closeInventory();
            } else {
                lang.notice(player, "accept-failed");
                reopen(player, holder);
            }
        })).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                lang.notice(player, "accept-failed");
                reopen(player, holder);
            });
            return null;
        });
    }

    private void handleCancelConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 4) { reopen(player, holder); return; }
        if (slot != 0) return;
        Bounty bounty = bountyService.get(holder.bountyId());
        boolean success = bountyService.cancelByCreator(player, bounty);
        if (!success) {
            lang.notice(player, "cancel-order-failed");
            reopen(player, holder);
            return;
        }
        lang.important(player, "cancel-order-success");
        reopen(player, holder);
    }

    private void reopen(Player player, LoveHuntHolder holder) {
        MenuType t = holder.returnType();
        if (t == MenuType.MINE) openMine(player, holder.page(), holder.sortMode(), holder.typeFilter(), holder.search());
        else if (t == MenuType.ALL) openAll(player, holder.page(), holder.sortMode(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else openMain(player);
    }

    // gui_gen v2.1, RULE 4: control buttons live in header slots 2-7 and are spread evenly across that range by
    // their count (1 -> 4, 2 -> 3/5, 3 -> 2/4/6, 4 -> 2/3/5/6, 5 -> 2/3/4/6/7, 6 -> 2..7). The slots a menu leaves
    // over stay glass (fill()), and slot 8 is glass too: the close button lives in the footer only.
    private static int[] controlSlots(int count) {
        return switch (count) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 6, 7};
            case 6 -> new int[]{2, 3, 4, 5, 6, 7};
            default -> throw new IllegalArgumentException("Header fits 1-6 control buttons, got " + count);
        };
    }

    private void addMineHeader(Inventory inventory, SortMode sortMode, TypeFilter typeFilter, Player player) {
        inventory.setItem(0, statsButton(player));
        inventory.setItem(MINE_CONTROLS[0], sortButton(sortMode));
        inventory.setItem(MINE_CONTROLS[1], typeFilterButton(typeFilter));
    }

    private void addAllHeader(Inventory inventory, SortMode sortMode, TypeFilter typeFilter, boolean onlyMyClan, boolean onlineOnly, Player player) {
        inventory.setItem(0, statsButton(player));
        inventory.setItem(ALL_CONTROLS[0], sortButton(sortMode));
        inventory.setItem(ALL_CONTROLS[1], typeFilterButton(typeFilter));
        inventory.setItem(ALL_CONTROLS[2], clanOnlineButton(onlyMyClan, onlineOnly, clanFilterAvailable(player)));
    }

    private void addFooter(Inventory inventory, PageSlice slice, Player player) {
        if (slice.totalPages() > 1) {
            if (slice.page() > 0) inventory.setItem(36, prevButton());
            if (slice.page() < slice.totalPages() - 1) inventory.setItem(44, nextButton());
        }
        Material footerMat = configMaterial("gui.items.filler", Material.GRAY_STAINED_GLASS_PANE);
        if (footerMat == null || footerMat.isAir()) footerMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack footerGlass = named(footerMat, Component.text(" "));
        for (int slot = 45; slot <= 50; slot++) inventory.setItem(slot, footerGlass);
        inventory.setItem(51, createButton(player));
        inventory.setItem(52, backButton());
        inventory.setItem(53, closeButton());
    }

    private void placeItems(Inventory inventory, LoveHuntHolder holder, List<Bounty> bounties, PageSlice slice, Player viewer) {
        int itemIndex = 0;
        for (int index = slice.start(); index < slice.end(); index++) {
            if (itemIndex >= CONTENT_SLOTS.length) break;
            int slot = CONTENT_SLOTS[itemIndex++];
            Bounty bounty = bounties.get(index);
            inventory.setItem(slot, bountyHead(bounty, viewer, true));
            holder.bountySlots().put(slot, bounty.id());
        }
    }

    private PageSlice paginate(List<Bounty> bounties, int page) {
        int total = bounties.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) PAGE_CAPACITY));
        int safePage = Math.max(0, page);
        int start = safePage * PAGE_CAPACITY;
        if (start >= total && safePage > 0) return null;
        int end = Math.min(start + PAGE_CAPACITY, total);
        return new PageSlice(safePage, start, end, MANAGE_SIZE, totalPages);
    }

    private List<Bounty> filterMine(Player player, SortMode sortMode, TypeFilter typeFilter, String search) {
        List<Bounty> list = new ArrayList<>(bountyService.byCreator(player.getUniqueId()));
        if (typeFilter != TypeFilter.ALL) {
            list.removeIf(b -> switch (typeFilter) {
                case PLAYER -> b.type() != BountyType.PLAYER;
                case CLAN -> b.type() != BountyType.CLAN;
                case SERVER -> b.type() != BountyType.SERVER;
                default -> false;
            });
        }
        applySearch(list, search);
        sort(list, sortMode);
        return list;
    }

    private List<Bounty> filterAll(Player player, SortMode sortMode, TypeFilter typeFilter, boolean onlyMyClan, boolean onlineOnly, String search) {
        List<Bounty> list = new ArrayList<>(bountyService.allActive());
        if (typeFilter != TypeFilter.ALL) {
            list.removeIf(b -> switch (typeFilter) {
                case PLAYER -> b.type() != BountyType.PLAYER;
                case CLAN -> b.type() != BountyType.CLAN;
                case SERVER -> b.type() != BountyType.SERVER;
                default -> false;
            });
        }
        if (onlyMyClan) {
            LoveHuntClans clans = bountyService.clans();
            String clan = clans.getClanTag(player.getUniqueId());
            if (clan != null) list.removeIf(b -> b.type() != BountyType.CLAN || !clan.equalsIgnoreCase(b.clanTag()));
            else list.clear();
        }
        if (onlineOnly) list.removeIf(b -> {
            UUID t = b.targetUuid();
            return t == null || Bukkit.getPlayer(t) == null;
        });
        applySearch(list, search);
        sort(list, sortMode);
        return list;
    }

    private void applySearch(List<Bounty> list, String search) {
        if (search == null || search.isBlank()) return;
        String q = search.toLowerCase(Locale.ROOT);
        list.removeIf(b -> {
            String name = b.targetName();
            return name == null || !name.toLowerCase(Locale.ROOT).contains(q);
        });
    }

    private void sort(List<Bounty> list, SortMode sortMode) {
        Comparator<Bounty> cmp = switch (sortMode) {
            case NAME -> Comparator.comparing(Bounty::targetName, String.CASE_INSENSITIVE_ORDER);
            case REWARD -> Comparator.comparingLong((Bounty b) -> b.reward() != null ? b.reward().amount() : 0L).reversed();
            case DATE -> Comparator.comparingLong(Bounty::createdAt).reversed();
            case EXPIRING -> Comparator.comparingLong((Bounty b) -> b.expiresAt() != null ? b.expiresAt() : Long.MAX_VALUE);
            case POPULAR -> Comparator.comparingInt((Bounty b) -> bountyService.hunterCount(b.id())).reversed();
        };
        list.sort(cmp);
    }

    private boolean clanFilterAvailable(Player player) {
        return bountyService.clans().getClanTag(player.getUniqueId()) != null;
    }

    private void fill(Inventory inventory) {
        Material fillerMat = configMaterial("gui.items.filler", Material.GRAY_STAINED_GLASS_PANE);
        if (fillerMat == null || fillerMat.isAir()) fillerMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = named(fillerMat, Component.text(" "));
        int size = inventory.getSize();
        if (size == 54) {
            // Header: every slot except 0 starts as glass; control buttons then replace the glass they sit on.
            for (int slot = 1; slot <= 8; slot++) inventory.setItem(slot, filler);
            for (int slot = 9; slot <= 17; slot++) inventory.setItem(slot, filler);
            for (int slot = 45; slot <= 53; slot++) inventory.setItem(slot, filler);
        } else if (size == 27) {
            for (int slot = 1; slot <= 8; slot++) inventory.setItem(slot, filler);
            for (int slot = 18; slot <= 24; slot++) inventory.setItem(slot, filler);
        } else if (size == 5 || inventory.getType() == InventoryType.HOPPER) {
            inventory.setItem(1, filler);
            inventory.setItem(3, filler);
        } else {
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        }
    }

    // ---- Lore helpers -------------------------------------------------------------------------------------------

    /** Lang lines of {@code loreKey} (placeholders filled in) followed by the {@code tail} lines, each parsed as MiniMessage. */
    private List<Component> lore(String loreKey, Map<String, String> placeholders, List<String> tail) {
        List<String> lines = new ArrayList<>(lang.rawLines(loreKey, placeholders));
        lines.addAll(tail);
        return toComponents(lines);
    }

    private List<Component> toComponents(List<String> lines) {
        return lines.stream().map(lang::mini).toList();
    }

    /** One left-click line that performs {@code actionKey} (its text comes from lang). */
    private List<String> left(String actionKey) {
        return LoreBlocks.clicks(style, LoreBlocks.hint(Click.LEFT, lang.raw(actionKey, Map.of())));
    }

    private static String statusFor(String messageKey) {
        return switch (messageKey) {
            case "create-blocked", "accept-blocked" -> "locked";
            case "creator-limit", "extend-max-duration" -> "max";
            default -> "unavailable";
        };
    }

    /** The chat text of a refusal as one {@code Причина:} line: plain, without the final full stop, MiniMessage-safe. */
    private String reasonOf(Refusal refusal) {
        String text = lang.plainText(refusal.key(), refusal.placeholders());
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return lang.escape(text);
    }

    /**
     * Action button. Available: its lore plus the left-click line. Refused: the same lore, then the blocked block
     * (status, {@code Причина:}, blocked click) and the name {@code Имя | Недоступно}; the click itself is unchanged.
     */
    private ItemStack actionButton(String path, Material fallback, String headKey, String nameKey, String loreKey, String actionKey,
                                   Map<String, String> placeholders, Refusal refusal) {
        String action = lang.raw(actionKey, Map.of());
        if (refusal == null) {
            return button(path, fallback, headKey, lang.component(nameKey, placeholders, false),
                    lore(loreKey, placeholders, LoreBlocks.clicks(style, LoreBlocks.hint(Click.LEFT, action))));
        }
        Component name = lang.mini(LoreBlocks.disabledName(style, lang.plainText(nameKey, placeholders)));
        return button(path, fallback, headKey, name, lore(loreKey, placeholders,
                LoreBlocks.blocked(style, statusFor(refusal.key()), reasonOf(refusal), LoreBlocks.hint(Click.LEFT, action))));
    }

    // ---- Buttons ------------------------------------------------------------------------------------------------

    private ItemStack createButton(Player player) {
        return actionButton("gui.items.create-bounty", Material.WRITABLE_BOOK, "create-bounty-base64",
                "gui.create", "gui.create-lore", "gui.create-action", Map.of(), createRefusal(player));
    }

    /** Pass card: buy button for non-hunters, an info card (level, tax, progress) for owners; hidden when the pass is off. */
    private ItemStack passButton(Player player) {
        var hunters = bountyService.hunters();
        if (hunters == null || !hunters.enabled()) return null;
        var hunter = hunters.get(player.getUniqueId());
        if (hunter != null) {
            int[] thresholds = settings.passLevelThresholds();
            int level = hunter.level();
            boolean max = level >= thresholds.length;
            Map<String, String> placeholders = lang.placeholders(
                    "level", level, "max", thresholds.length,
                    "tax", Math.round(me.lovelace.loveHunt.service.HunterRules.taxFor(level, settings.passTaxRates()) * 100),
                    "completed", hunter.completed(),
                    "next", max ? hunter.completed() : thresholds[level],
                    "bar", LoreBar.bar(max ? 1 : hunter.completed(), max ? 1 : Math.max(1, thresholds[level])),
                    "reset", settings.passResetDays());
            return button("gui.items.pass", Material.PAPER, "pass-base64", lang.component("gui.pass.mine", placeholders, false),
                    lore(max ? "gui.pass.mine-lore-max" : "gui.pass.mine-lore", placeholders, List.of()));
        }
        var refusal = hunters.purchaseRefusal(player);
        Map<String, String> placeholders = lang.placeholders("price", settings.passPrice(),
                "tax", Math.round(me.lovelace.loveHunt.service.HunterRules.taxFor(1, settings.passTaxRates()) * 100));
        return actionButton("gui.items.pass", Material.PAPER, "pass-base64", "gui.pass.buy", "gui.pass.buy-lore",
                "gui.pass.buy-action", placeholders, refusal == null ? null : new Refusal(refusal.key(), refusal.placeholders()));
    }

    private void buyPass(Player player) {
        var hunters = bountyService.hunters();
        if (hunters == null || !hunters.enabled() || hunters.get(player.getUniqueId()) != null) return;
        var refusal = hunters.buy(player);
        if (refusal != null) {
            lang.notice(player, refusal.key(), refusal.placeholders());
        } else {
            lang.important(player, "pass-bought");
        }
        openMain(player);
    }

    private ItemStack prevButton() {
        return button("gui.items.previous", Material.ARROW, "previous-base64", lang.component("gui.all.previous"), List.of());
    }

    private ItemStack nextButton() {
        return button("gui.items.next", Material.ARROW, "next-base64", lang.component("gui.all.next"), List.of());
    }

    private ItemStack confirmButton(String loreKey, String actionKey) {
        return button("gui.items.confirm", Material.LIME_CONCRETE, "confirm-base64", lang.component("gui.confirm.confirm"),
                lore(loreKey, Map.of(), left(actionKey)));
    }

    private ItemStack cancelActionButton() {
        return button("gui.items.cancel", Material.RED_CONCRETE, "close-base64", lang.component("gui.confirm.cancel"),
                lore("gui.confirm.cancel-lore", Map.of(), List.of()));
    }

    private ItemStack cancelOrderButton() {
        return button("gui.items.cancel-order", Material.RED_CONCRETE, "cancel-order-base64", lang.component("gui.confirm.cancel-order"),
                lore("gui.confirm.cancel-order-lore", Map.of(), left("gui.confirm.cancel-order-action")));
    }

    /** Navigation buttons carry no lore by design (GUI_LORE_STYLE, section 14). */
    private ItemStack backButton() {
        return button("gui.items.back", Material.ARROW, "back-base64", lang.component("gui.back"), List.of());
    }

    private ItemStack closeButton() {
        return button("gui.items.close", Material.BARRIER, "close-base64", lang.component("gui.close"), List.of());
    }

    // Members-menu style switches: the name shows the current option, the lore lists every option with the current one
    // marked, then the LMB/RMB lines. Option labels live in gui.switch.<switch>.options.<option>; Lang falls back to the
    // bundled lang.yml for servers whose file predates these keys.
    private ItemStack sortButton(SortMode sortMode) {
        return button("gui.items.sort", Material.HOPPER, "sort-base64",
                switchName("sort", sortMode),
                switchLore("sort", sortMode, java.util.Arrays.asList(SortMode.values())));
    }

    private ItemStack typeFilterButton(TypeFilter typeFilter) {
        // Default skin is built in, so servers whose already-generated heads.yml predates this key still get the head.
        String texture = heads != null ? heads.base64("type-filter-base64", FILTER_HEAD) : FILTER_HEAD;
        return buttonWithTexture("gui.items.type-filter", Material.NAME_TAG, texture,
                switchName("type", typeFilter),
                switchLore("type", typeFilter, java.util.Arrays.asList(TypeFilter.values())));
    }

    private ItemStack clanOnlineButton(boolean onlyMyClan, boolean onlineOnly, boolean hasClan) {
        List<ClanOnlineMode> options = java.util.Arrays.stream(ClanOnlineMode.values())
                .filter(mode -> mode.availableFor(hasClan)).toList();
        ClanOnlineMode current = ClanOnlineMode.of(onlyMyClan, onlineOnly);
        return button("gui.items.clan-filter", Material.SHIELD, "clan-filter-base64",
                switchName("clan-online", current),
                switchLore("clan-online", current, options));
    }

    private <E extends Enum<E>> String optionLabel(String switchKey, E option) {
        return lang.plainMini("gui.switch." + switchKey + ".options." + option.name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private <E extends Enum<E>> Component switchName(String switchKey, E current) {
        return lang.component("gui.switch." + switchKey + ".name", lang.placeholders("current", optionLabel(switchKey, current)), false);
    }

    private <E extends Enum<E>> List<Component> switchLore(String switchKey, E current, List<E> options) {
        List<String> lines = new ArrayList<>();
        lines.add(lang.raw("gui.switch.subtitle", Map.of()));
        lines.add("");
        lines.add(lang.raw("gui.switch.options-title", Map.of()));
        for (E option : options) {
            String key = option == current ? "gui.switch.option-current" : "gui.switch.option-other";
            lines.add(lang.raw(key, lang.placeholders("name", optionLabel(switchKey, option))));
        }
        lines.addAll(LoreBlocks.clicks(style,
                LoreBlocks.hint(Click.LEFT, lang.raw("gui.switch.next", Map.of())),
                LoreBlocks.hint(Click.RIGHT, lang.raw("gui.switch.previous", Map.of()))));
        return toComponents(lines);
    }

    private ItemStack extendButton(Player player, Bounty bounty) {
        Map<String, String> placeholders = lang.placeholders("amount", String.valueOf(bountyService.extendCost(bounty)),
                "item", rewardName(bounty.reward()));
        return actionButton("gui.items.extend", Material.CLOCK, "extend-base64", "gui.manage-extend", "gui.manage-extend-lore",
                "gui.manage-extend-action", placeholders, extendBlock(player, bounty));
    }

    private ItemStack emptyNoticeHead(Component name, List<Component> lore) {
        ItemStack item = named(Material.GRAY_DYE, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemBuilder.apply(meta, null, lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ---- Heads --------------------------------------------------------------------------------------------------

    private ItemStack statsButton(Player player) {
        HunterRating rating = bountyService.ratings().get(player.getUniqueId());
        ItemStack head = HeadUtil.playerHead(player);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = new HashMap<>(ratingPlaceholders(rating));
            placeholders.put("player", player.getName());
            ItemBuilder.apply(meta, lang.component("gui.stats", placeholders, false), lore("gui.stats-lore", placeholders, List.of()));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Placeholders shared by the rating blocks of the stats head and the hunter heads. */
    private Map<String, String> ratingPlaceholders(HunterRating rating) {
        return lang.placeholders(
                "rank", rating.rankName(),
                "stars", rating.rankStars(),
                "rating", String.format(Locale.ROOT, "%.1f", rating.rating()),
                "bar", LoreBar.bar(Math.round(rating.rating() * 100), RATING_BAR_MAX),
                "completed", String.valueOf(rating.completed()),
                "failed", String.valueOf(rating.failed()),
                "bonus", bonusLine(rating));
    }

    private String bonusLine(HunterRating rating) {
        int percent = (int) Math.round(bountyService.ratings().rewardModifierFraction(rating.rating()) * 100.0);
        if (percent > 0) return lang.raw("gui.stats-bonus-up", lang.placeholders("percent", String.valueOf(percent)));
        if (percent < 0) return lang.raw("gui.stats-bonus-down", lang.placeholders("percent", String.valueOf(percent)));
        return lang.raw("gui.stats-bonus-none", Map.of());
    }

    private ItemStack hunterHead(UUID hunterUuid) {
        OfflinePlayer hunter = Bukkit.getOfflinePlayer(hunterUuid);
        HunterRating rating = bountyService.ratings().get(hunterUuid);
        ItemStack head = HeadUtil.playerHead(hunter);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            String name = hunter.getName() == null ? hunterUuid.toString() : hunter.getName();
            Map<String, String> placeholders = new HashMap<>(ratingPlaceholders(rating));
            placeholders.put("name", name);
            placeholders.put("state", lang.raw(hunter.isOnline() ? "gui.hunter-state-online" : "gui.hunter-state-offline", Map.of()));
            ItemBuilder.apply(meta, lang.component("gui.hunter", placeholders, false), lore("gui.hunter-lore", placeholders, List.of()));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Bounty head. {@code listed} heads are entries of a list: they carry the click (manage own, accept other) or the
     * blocked block when accepting is refused. Heads in confirm and manage screens only inform and carry no click.
     */
    private ItemStack bountyHead(Bounty bounty, Player viewer, boolean listed) {
        OfflinePlayer target = bounty.targetUuid() != null ? Bukkit.getOfflinePlayer(bounty.targetUuid()) : null;
        ItemStack head = target != null ? HeadUtil.playerHead(target) : named(Material.SKELETON_SKULL, lang.mini("<red>?"));
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            String targetName = bounty.targetName() != null ? bounty.targetName() : "?";
            Map<String, String> placeholders = bountyPlaceholders(bounty, targetName);
            Component name = lang.component("gui.bounty", placeholders, false);
            List<String> lines = new ArrayList<>(lang.rawLines("gui.bounty-lore", placeholders));
            if (listed) {
                boolean own = viewer != null && bounty.creatorUuid() != null && bounty.creatorUuid().equals(viewer.getUniqueId());
                if (own) {
                    lines.addAll(left("gui.bounty-action-manage"));
                } else {
                    Refusal refusal = viewer == null ? null : acceptRefusal(viewer, bounty);
                    if (refusal == null) {
                        lines.addAll(left("gui.bounty-action-accept"));
                    } else {
                        name = lang.mini(LoreBlocks.disabledName(style, lang.escape(targetName)));
                        lines.addAll(LoreBlocks.blocked(style, statusFor(refusal.key()), reasonOf(refusal),
                                LoreBlocks.hint(Click.LEFT, lang.raw("gui.bounty-action-accept", Map.of()))));
                    }
                }
            }
            ItemBuilder.apply(meta, name, toComponents(lines));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private Map<String, String> bountyPlaceholders(Bounty bounty, String targetName) {
        RewardItem reward = bounty.reward();
        return lang.placeholders(
                "target", targetName,
                "type", lang.plainMini("gui.bounty-type." + bounty.type().name().toLowerCase(Locale.ROOT)),
                "amount", reward == null ? "0" : String.valueOf(reward.amount()),
                "item", rewardName(reward),
                "time", TimeUtil.compact(Math.max(0, bounty.expiresAt() - System.currentTimeMillis())));
    }

    private ItemStack createSummary(CreateSession session) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUuid());
        ItemStack head = HeadUtil.playerHead(target);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            Map<String, String> placeholders = lang.placeholders(
                    "target", session.targetName(),
                    "amount", String.valueOf(session.reward().amount()),
                    "item", rewardName(session.reward()));
            ItemBuilder.apply(meta, lang.component("gui.create-summary", placeholders, false),
                    lore("gui.create-summary-lore", placeholders, List.of()));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    // ---- Refusals -----------------------------------------------------------------------------------------------

    /** Same gates as {@link BountyService#validateCreation}, checked before the player is asked for a target. */
    private Refusal createRefusal(Player player) {
        if (bountyService.ratings().isCreateBlocked(player.getUniqueId())) {
            return new Refusal("create-blocked", Map.of());
        }
        int limit = settings.maxActiveByPlayer();
        if (bountyService.byCreator(player.getUniqueId()).size() >= limit) {
            return new Refusal("creator-limit", lang.placeholders("limit", String.valueOf(limit)));
        }
        return null;
    }

    /** Same gates as {@link BountyService#accept}, for the hunter who would click the bounty head. */
    private Refusal acceptRefusal(Player hunter, Bounty bounty) {
        RatingService ratings = bountyService.ratings();
        UUID uuid = hunter.getUniqueId();
        if (ratings.isAcceptBlocked(uuid)) return new Refusal("accept-blocked", Map.of());
        if (ratings.isRatingTooLowToAccept(uuid)) return new Refusal("rating-too-low", Map.of());
        if (bountyService.isFrozen(bounty)) return new Refusal("bounty-frozen", Map.of());
        if (bountyService.hasAccepted(uuid, bounty.id())) return new Refusal("already-accepted", Map.of());
        return null;
    }

    private Refusal extendBlock(Player player, Bounty bounty) {
        BountyService.ExtendResult result = bountyService.extendRefusal(player, bounty);
        return result == null ? null : new Refusal(result.messageKey(), result.placeholders());
    }

    // ---- Item plumbing ------------------------------------------------------------------------------------------

    private ItemStack button(String path, Material fallback, String headKey, Component name, List<Component> lore) {
        return buttonWithTexture(path, fallback, heads != null ? heads.base64(headKey) : null, name, lore);
    }

    /** {@code texture} is the resolved Base64 skin (may be blank -> plain material from config). */
    private ItemStack buttonWithTexture(String path, Material fallback, String texture, Component name, List<Component> lore) {
        ItemStack item;
        if (texture != null && !texture.isBlank()) {
            item = HeadUtil.base64Head(texture);
        } else {
            item = new ItemStack(configMaterial(path, fallback));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemBuilder.apply(meta, name, lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ItemBuilder.apply(meta, name, null);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String rewardName(RewardItem reward) {
        return reward == null || reward.displayName() == null ? "" : reward.displayName();
    }

    private static String rewardName(Bounty bounty) {
        return rewardName(bounty.reward());
    }

    private Material configMaterial(String path, Material fallback) {
        String raw = plugin.getConfig().getString(path);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private record PageSlice(int page, int start, int end, int size, int totalPages) {}
}
