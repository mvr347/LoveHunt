package me.lovelace.loveHunt.gui;

import me.lovelace.loveHunt.api.LoveHuntClans;
import me.lovelace.loveHunt.config.Heads;
import me.lovelace.loveHunt.config.Lang;
import me.lovelace.loveHunt.config.Settings;
import me.lovelace.loveHunt.model.Bounty;
import me.lovelace.loveHunt.model.BountyType;
import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.HunterRating;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.model.TypeFilter;
import me.lovelace.loveHunt.service.BountyService;
import me.lovelace.loveHunt.service.InputMode;
import me.lovelace.loveHunt.service.PlayerInput;
import me.lovelace.loveHunt.textures.HeadTextures;
import me.lovelace.loveHunt.util.HeadUtil;
import me.lovelace.loveHunt.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.time.Duration;
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

    private static final int MANAGE_SIZE = 54;
    private static final int MANAGE_TARGET = 0;
    private static final int MANAGE_CANCEL = 50;
    private static final int MANAGE_EXTEND = 51;
    private static final int MANAGE_BACK = 52;
    private static final int MANAGE_CLOSE = 53;

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
    private final ConcurrentHashMap<UUID, PlayerInput> inputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CreateSession> createSessions = new ConcurrentHashMap<>();

    public MenuManager(JavaPlugin plugin, Settings settings, Lang lang, Heads heads, BountyService bountyService) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.heads = heads;
        this.bountyService = bountyService;
    }

    public void openMain(Player player) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.MAIN, MenuType.MAIN, 0, SortMode.DATE, TypeFilter.ALL, false, false, null, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MAIN_SIZE, lang.component("gui.main-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, statsButton(player));
        inventory.setItem(MINE_MAIN_BUTTON, button("gui.items.my-bounties", Material.PLAYER_HEAD, "my-bounties-base64", lang.component("gui.main.mine"), lang.components("gui.main.mine-lore", Map.of(), false)));
        inventory.setItem(ALL_MAIN_BUTTON, button("gui.items.all-bounties", Material.COMPASS, "all-bounties-base64", lang.component("gui.main.all"), lang.components("gui.main.all-lore", Map.of(), false)));
        inventory.setItem(25, createButton());
        inventory.setItem(MAIN_SIZE - 1, closeButton());
        player.openInventory(inventory);
    }

    public void openMine(Player player, int page, SortMode sortMode, String search) {
        List<Bounty> bounties = filterMine(player, sortMode, search);
        PageSlice slice = paginate(bounties, page);
        if (slice == null) {
            openMine(player, page - 1, sortMode, search);
            return;
        }
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.MINE, MenuType.MINE, slice.page(), sortMode, TypeFilter.ALL, false, false, search, null, 0L);
        Inventory inventory = Bukkit.createInventory(holder, MANAGE_SIZE, lang.component("gui.mine-title"));
        holder.inventory(inventory);
        fill(inventory);
        addMineHeader(inventory, sortMode, player);
        placeItems(inventory, holder, bounties, slice, player);
        addFooter(inventory, slice);
        if (bounties.isEmpty()) {
            inventory.setItem(31, emptyNoticeHead(lang.component("gui.mine.empty"), lang.components("gui.mine.empty-lore", Map.of(), false)));
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
        addFooter(inventory, slice);
        if (bounties.isEmpty()) {
            inventory.setItem(31, emptyNoticeHead(lang.component("gui.all.empty"), lang.components("gui.all.empty-lore", Map.of(), false)));
        }
        player.openInventory(inventory);
    }

    public void openCreateConfirm(Player player, CreateSession session) {
        createSessions.put(player.getUniqueId(), session);
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_CREATE, MenuType.MAIN, 0, SortMode.DATE, TypeFilter.ALL, false, false, null, session, 0L);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, lang.component("gui.confirm-create-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, confirmButton());
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
        inventory.setItem(0, confirmButton());
        inventory.setItem(2, bountyHead(bounty, player));
        inventory.setItem(4, cancelActionButton());
        player.openInventory(inventory);
    }

    public void openCancelConfirm(Player player, LoveHuntHolder origin, Bounty bounty) {
        LoveHuntHolder holder = new LoveHuntHolder(MenuType.CONFIRM_CANCEL, origin.returnType(), origin.page(), origin.sortMode(), origin.typeFilter(),
                origin.onlyMyClan(), origin.onlineOnly(), origin.search(), null, bounty.id());
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.HOPPER, lang.component("gui.confirm-cancel-title"));
        holder.inventory(inventory);
        fill(inventory);
        inventory.setItem(0, confirmDeleteButton());
        inventory.setItem(2, bountyHead(bounty, player));
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
        inventory.setItem(0, bountyHead(bounty, player));

        if (hunters.isEmpty()) {
            inventory.setItem(13, emptyNoticeHead(lang.component("gui.manage-no-hunters"), lang.components("gui.manage-no-hunters-lore", Map.of(), false)));
            inventory.setItem(23, cancelOrderButton());
            if (bounty.type() == BountyType.PLAYER || bounty.type() == BountyType.CLAN) {
                inventory.setItem(24, extendButton(bounty));
            }
            inventory.setItem(25, backButton());
            inventory.setItem(26, closeButton());
        } else {
            inventory.setItem(MANAGE_CANCEL, cancelOrderButton());
            if (bounty.type() == BountyType.PLAYER || bounty.type() == BountyType.CLAN) {
                inventory.setItem(MANAGE_EXTEND, extendButton(bounty));
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
            if (input.returnType() == MenuType.MINE) openMine(player, 0, input.sortMode(), message.trim());
            else openAll(player, 0, input.sortMode(), input.typeFilter(), input.onlyMyClan(), input.onlineOnly(), message.trim());
        }
    }

    public void handleClick(Player player, LoveHuntHolder holder, int slot) {
        switch (holder.type()) {
            case MAIN -> handleMain(player, slot);
            case MINE -> handleMine(player, holder, slot);
            case ALL -> handleAll(player, holder, slot);
            case CONFIRM_CREATE -> handleCreateConfirm(player, holder, slot);
            case CONFIRM_ACCEPT -> handleAcceptConfirm(player, holder, slot);
            case CONFIRM_CANCEL -> handleCancelConfirm(player, holder, slot);
            case MANAGE -> handleManage(player, holder, slot);
        }
    }

    private void handleMain(Player player, int slot) {
        if (slot == MINE_MAIN_BUTTON) openMine(player, 0, SortMode.DATE, null);
        else if (slot == ALL_MAIN_BUTTON) openAll(player, 0, SortMode.DATE, TypeFilter.ALL, false, false, null);
        else if (slot == 25) beginCreate(player);
        else if (slot == MAIN_SIZE - 1) player.closeInventory();
    }

    private void handleMine(Player player, LoveHuntHolder holder, int slot) {
        if (holder.bountySlots().containsKey(slot)) {
            openBountyAction(player, holder, holder.bountySlots().get(slot));
            return;
        }
        if (slot == 2) openMine(player, 0, nextSort(holder.sortMode()), holder.search());
        else if (slot == 36 && holder.page() > 0) openMine(player, holder.page() - 1, holder.sortMode(), holder.search());
        else if (slot == 44) openMine(player, holder.page() + 1, holder.sortMode(), holder.search());
        else if (slot == 51) beginCreate(player);
        else if (slot == 52) openMain(player);
        else if (slot == 53) player.closeInventory();
    }

    private void handleAll(Player player, LoveHuntHolder holder, int slot) {
        if (holder.bountySlots().containsKey(slot)) {
            openBountyAction(player, holder, holder.bountySlots().get(slot));
            return;
        }
        if (slot == 2) openAll(player, 0, nextSort(holder.sortMode()), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == 3) openAll(player, 0, holder.sortMode(), nextType(holder.typeFilter()), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == 4) {
            if (!holder.onlyMyClan() && !clanFilterAvailable(player)) {
                lang.send(player, "clan-filter-unavailable");
                return;
            }
            boolean[] next = nextClanOnline(holder.onlyMyClan(), holder.onlineOnly());
            openAll(player, 0, holder.sortMode(), holder.typeFilter(), next[0], next[1], holder.search());
        } else if (slot == 36 && holder.page() > 0) openAll(player, holder.page() - 1, holder.sortMode(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == 44) openAll(player, holder.page() + 1, holder.sortMode(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else if (slot == 51) beginCreate(player);
        else if (slot == 52) openMain(player);
        else if (slot == 53) player.closeInventory();
    }

    private void openBountyAction(Player player, LoveHuntHolder origin, long bountyId) {
        Bounty bounty = bountyService.get(bountyId);
        if (bounty == null) {
            lang.send(player, "bounty-unavailable");
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
            lang.send(player, "bounty-unavailable");
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
            lang.send(player, "extend-success", lang.placeholders("amount", String.valueOf(result.cost()), "item", result.bounty().reward().displayName()));
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
                lang.send(player, "create-success", lang.placeholders("target", finalSession.targetName(), "amount", finalSession.reward().amount(), "item", finalSession.reward().displayName()))))
                .exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(plugin, () -> lang.send(player, "create-failed"));
                    return null;
                });
    }

    private void handleAcceptConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 4) { reopen(player, holder); return; }
        if (slot != 0) return;
        BountyService.AcceptResult result = bountyService.accept(player, holder.bountyId());
        if (!result.success()) {
            lang.send(player, result.messageKey(), result.placeholders());
            reopen(player, holder);
            return;
        }
        lang.send(player, "accept-success");
        player.closeInventory();
    }

    private void handleCancelConfirm(Player player, LoveHuntHolder holder, int slot) {
        if (slot == 4) { reopen(player, holder); return; }
        if (slot != 0) return;
        BountyService.CancelResult result = bountyService.cancel(player, holder.bountyId());
        if (!result.success()) {
            lang.send(player, result.messageKey(), result.placeholders());
            reopen(player, holder);
            return;
        }
        lang.send(player, "cancel-success");
        reopen(player, holder);
    }

    private void reopen(Player player, LoveHuntHolder holder) {
        MenuType t = holder.returnType();
        if (t == MenuType.MINE) openMine(player, holder.page(), holder.sortMode(), holder.search());
        else if (t == MenuType.ALL) openAll(player, holder.page(), holder.sortMode(), holder.typeFilter(), holder.onlyMyClan(), holder.onlineOnly(), holder.search());
        else openMain(player);
    }

    private void addMineHeader(Inventory inventory, SortMode sortMode, Player player) {
        inventory.setItem(0, statsButton(player));
        inventory.setItem(2, sortButton(sortMode));
        inventory.setItem(8, closeButton());
    }

    private void addAllHeader(Inventory inventory, SortMode sortMode, TypeFilter typeFilter, boolean onlyMyClan, boolean onlineOnly, Player player) {
        inventory.setItem(0, statsButton(player));
        inventory.setItem(2, sortButton(sortMode));
        inventory.setItem(3, typeFilterButton(typeFilter));
        inventory.setItem(4, clanOnlineButton(onlyMyClan, onlineOnly));
        inventory.setItem(8, closeButton());
    }

    private void addFooter(Inventory inventory, PageSlice slice) {
        if (slice.totalPages() > 1) {
            if (slice.page() > 0) inventory.setItem(36, prevButton());
            if (slice.page() < slice.totalPages() - 1) inventory.setItem(44, nextButton());
        }
        Material footerMat = configMaterial("gui.items.filler", Material.GRAY_STAINED_GLASS_PANE);
        if (footerMat == null || footerMat.isAir()) footerMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack footerGlass = named(footerMat, Component.text(" "));
        for (int slot = 45; slot <= 50; slot++) inventory.setItem(slot, footerGlass);
        inventory.setItem(51, createButton());
        inventory.setItem(52, backButton());
        inventory.setItem(53, closeButton());
    }

    private void placeItems(Inventory inventory, LoveHuntHolder holder, List<Bounty> bounties, PageSlice slice, Player viewer) {
        int itemIndex = 0;
        for (int index = slice.start(); index < slice.end(); index++) {
            if (itemIndex >= CONTENT_SLOTS.length) break;
            int slot = CONTENT_SLOTS[itemIndex++];
            Bounty bounty = bounties.get(index);
            inventory.setItem(slot, bountyHead(bounty, viewer));
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

    private List<Bounty> filterMine(Player player, SortMode sortMode, String search) {
        List<Bounty> list = new ArrayList<>(bountyService.activeByCreator(player.getUniqueId()));
        applySearch(list, search);
        sort(list, sortMode);
        return list;
    }

    private List<Bounty> filterAll(Player player, SortMode sortMode, TypeFilter typeFilter, boolean onlyMyClan, boolean onlineOnly, String search) {
        List<Bounty> list = new ArrayList<>(bountyService.activeAll());
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
            String clan = clans.clanId(player.getUniqueId());
            if (clan != null) list.removeIf(b -> b.type() != BountyType.CLAN || !clan.equals(b.clanId()));
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
            case DATE -> Comparator.comparingLong(Bounty::createdAt).reversed();
            case REWARD -> Comparator.comparingLong((Bounty b) -> b.reward() != null ? b.reward().amount() : 0L).reversed();
            case EXPIRY -> Comparator.comparingLong(Bounty::expiresAt);
        };
        list.sort(cmp);
    }

    private SortMode nextSort(SortMode current) {
        SortMode[] values = SortMode.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private TypeFilter nextType(TypeFilter current) {
        TypeFilter[] values = TypeFilter.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private boolean[] nextClanOnline(boolean onlyMyClan, boolean onlineOnly) {
        if (!onlyMyClan && !onlineOnly) return new boolean[]{true, false};
        if (onlyMyClan && !onlineOnly) return new boolean[]{false, true};
        if (!onlyMyClan && onlineOnly) return new boolean[]{true, true};
        return new boolean[]{false, false};
    }

    private boolean clanFilterAvailable(Player player) {
        return bountyService.clans().clanId(player.getUniqueId()) != null;
    }

    private void fill(Inventory inventory) {
        Material fillerMat = configMaterial("gui.items.filler", Material.GRAY_STAINED_GLASS_PANE);
        if (fillerMat == null || fillerMat.isAir()) fillerMat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack filler = named(fillerMat, Component.text(" "));
        int size = inventory.getSize();
        if (size == 54) {
            inventory.setItem(1, filler);
            for (int slot = 5; slot <= 8; slot++) inventory.setItem(slot, filler);
            for (int slot = 9; slot <= 17; slot++) inventory.setItem(slot, filler);
            for (int slot = 45; slot <= 53; slot++) inventory.setItem(slot, filler);
        } else if (size == 27) {
            for (int slot = 1; slot <= 8; slot++) inventory.setItem(slot, filler);
            for (int slot = 18; slot <= 22; slot++) inventory.setItem(slot, filler);
        } else if (size == 5 || inventory.getType() == InventoryType.HOPPER) {
            inventory.setItem(1, filler);
            inventory.setItem(3, filler);
        } else {
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
        }
    }

    private ItemStack createButton() {
        return button("gui.items.create-bounty", Material.WRITABLE_BOOK, "create-bounty-base64", lang.component("gui.create"), lang.components("gui.create-lore", Map.of(), false));
    }

    private ItemStack prevButton() {
        return button("gui.items.previous", Material.ARROW, "previous-base64", lang.component("gui.all.previous"), lang.components("gui.all.previous-lore", Map.of(), false));
    }

    private ItemStack nextButton() {
        return button("gui.items.next", Material.ARROW, "next-base64", lang.component("gui.all.next"), lang.components("gui.all.next-lore", Map.of(), false));
    }

    private ItemStack confirmButton() {
        return button("gui.items.confirm", Material.LIME_CONCRETE, "confirm-base64", lang.component("gui.confirm.confirm"), lang.components("gui.confirm.confirm-lore", Map.of(), false));
    }

    private ItemStack confirmDeleteButton() {
        return button("gui.items.confirm", Material.LIME_CONCRETE, "confirm-base64", lang.component("gui.confirm.confirm"), lang.components("gui.confirm.confirm-delete-lore", Map.of(), false));
    }

    private ItemStack cancelActionButton() {
        return button("gui.items.cancel", Material.RED_CONCRETE, "close-base64", lang.component("gui.confirm.cancel"), lang.components("gui.confirm.cancel-lore", Map.of(), false));
    }

    private ItemStack cancelOrderButton() {
        return button("gui.items.cancel-order", Material.RED_CONCRETE, "cancel-order-base64", lang.component("gui.confirm.cancel-order"), lang.components("gui.confirm.cancel-order-lore", Map.of(), false));
    }

    private ItemStack backButton() {
        return button("gui.items.back", Material.ARROW, "back-base64", lang.component("gui.back"), lang.components("gui.back-lore", Map.of(), false));
    }

    private ItemStack closeButton() {
        return button("gui.items.close", Material.BARRIER, "close-base64", lang.component("gui.close"), lang.components("gui.close-lore", Map.of(), false));
    }

    private ItemStack sortButton(SortMode sortMode) {
        return button("gui.items.sort", Material.HOPPER, "sort-base64", lang.component("gui.sort." + sortMode.name().toLowerCase(Locale.ROOT)), lang.components("gui.sort-lore", Map.of(), false));
    }

    private ItemStack typeFilterButton(TypeFilter typeFilter) {
        return button("gui.items.filter", Material.NAME_TAG, "filter-base64", lang.component("gui.filter." + typeFilter.name().toLowerCase(Locale.ROOT)), lang.components("gui.filter-lore", Map.of(), false));
    }

    private ItemStack clanOnlineButton(boolean onlyMyClan, boolean onlineOnly) {
        String key = onlyMyClan ? (onlineOnly ? "clan-online" : "clan") : (onlineOnly ? "online" : "all");
        return button("gui.items.clan-filter", Material.SHIELD, "clan-filter-base64", lang.component("gui.clan-filter." + key), lang.components("gui.clan-filter-lore", Map.of(), false));
    }

    private ItemStack extendButton(Bounty bounty) {
        return button("gui.items.extend", Material.CLOCK, "extend-base64", lang.component("gui.extend"), lang.components("gui.extend-lore", Map.of(), false));
    }

    private ItemStack emptyNoticeHead(Component name, List<Component> lore) {
        ItemStack item = named(Material.GRAY_DYE, name);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack statsButton(Player player) {
        HunterRating rating = bountyService.ratings().get(player.getUniqueId());
        ItemStack head = HeadUtil.playerHead(player);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.legacy("§e" + player.getName()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    lang.legacy("§7Ранг: §6" + rating.rankName() + " §e" + rating.rankStars()),
                    lang.legacy("§7Рейтинг: §e" + String.format(Locale.ROOT, "%.1f", rating.rating()) + " / 5.0"),
                    lang.legacy("§7Выполнено: §a" + rating.completed()),
                    lang.legacy("§7Провалено: §c" + rating.failed()),
                    lang.legacy(""),
                    lang.legacy(rewardModifierLine(rating))
            ));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private String rewardModifierLine(HunterRating rating) {
        int percent = (int) Math.round(bountyService.ratings().rewardModifierFraction(rating.rating()) * 100.0);
        if (percent > 0) return "§7Награда: §a+" + percent + "% §7за ранг";
        if (percent < 0) return "§7Награда: §c" + percent + "% §7за низкий рейтинг";
        return "§7Награда: §fбез надбавки";
    }

    private ItemStack hunterHead(UUID hunterUuid) {
        OfflinePlayer hunter = Bukkit.getOfflinePlayer(hunterUuid);
        HunterRating rating = bountyService.ratings().get(hunterUuid);
        ItemStack head = HeadUtil.playerHead(hunter);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            String name = hunter.getName() == null ? hunterUuid.toString() : hunter.getName();
            meta.displayName(lang.legacy("§e" + name).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    lang.legacy("§7Онлайн: " + (hunter.isOnline() ? "§aДа" : "§cНет")),
                    lang.legacy("§7Ранг: §6" + rating.rankName() + " §e" + rating.rankStars()),
                    lang.legacy("§7Рейтинг: §e" + String.format(Locale.ROOT, "%.1f", rating.rating()) + " / 5.0"),
                    lang.legacy("§7Выполнено: §a" + rating.completed()),
                    lang.legacy("§7Провалено: §c" + rating.failed())
            ));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack bountyHead(Bounty bounty, Player viewer) {
        OfflinePlayer target = bounty.targetUuid() != null ? Bukkit.getOfflinePlayer(bounty.targetUuid()) : null;
        ItemStack head = target != null ? HeadUtil.playerHead(target) : named(Material.SKELETON_SKULL, lang.legacy("§c?").decoration(TextDecoration.ITALIC, false));
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            String targetName = bounty.targetName() != null ? bounty.targetName() : "?";
            meta.displayName(lang.legacy("§c" + targetName).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(lang.legacy("§7Тип: §f" + bounty.type().name()));
            if (bounty.reward() != null) {
                lore.add(lang.legacy("§7Награда: §a" + bounty.reward().amount() + " §7" + bounty.reward().displayName()));
            }
            lore.add(lang.legacy("§7Истекает: §e" + TimeUtil.formatRemaining(Duration.ofMillis(Math.max(0, bounty.expiresAt() - System.currentTimeMillis())))));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createSummary(CreateSession session) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(session.targetUuid());
        ItemStack head = HeadUtil.playerHead(target);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(lang.legacy("§e" + session.targetName()).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    lang.legacy("§7Награда: §a" + session.reward().amount() + " §7" + session.reward().displayName()),
                    lang.legacy("§7Подтвердите создание ордера")
            ));
            meta.addItemFlags(ItemFlag.values());
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack button(String path, Material fallback, String headKey, Component name, List<Component> lore) {
        ItemStack item;
        String texture = heads != null ? heads.get(headKey) : null;
        if (texture != null && !texture.isBlank()) {
            item = HeadUtil.base64Head(texture);
        } else {
            item = new ItemStack(configMaterial(path, fallback));
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
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
