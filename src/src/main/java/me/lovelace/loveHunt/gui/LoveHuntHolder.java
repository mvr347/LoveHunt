package me.lovelace.loveHunt.gui;

import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.SortMode;
import me.lovelace.loveHunt.model.TypeFilter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class LoveHuntHolder implements InventoryHolder {
    private final MenuType type;
    private final MenuType returnType;
    private final int page;
    private final SortMode sortMode;
    private final TypeFilter typeFilter;
    private final boolean onlyMyClan;
    private final boolean onlineOnly;
    private final String search;
    private final CreateSession createSession;
    private final long bountyId;
    private final Map<Integer, Long> bountySlots = new HashMap<>();
    private Inventory inventory;

    public LoveHuntHolder(MenuType type, MenuType returnType, int page, SortMode sortMode, TypeFilter typeFilter,
                           boolean onlyMyClan, boolean onlineOnly, String search, CreateSession createSession, long bountyId) {
        this.type = type;
        this.returnType = returnType;
        this.page = page;
        this.sortMode = sortMode;
        this.typeFilter = typeFilter;
        this.onlyMyClan = onlyMyClan;
        this.onlineOnly = onlineOnly;
        this.search = search;
        this.createSession = createSession;
        this.bountyId = bountyId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType type() {
        return type;
    }

    public MenuType returnType() {
        return returnType;
    }

    public int page() {
        return page;
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public TypeFilter typeFilter() {
        return typeFilter;
    }

    public boolean onlyMyClan() {
        return onlyMyClan;
    }

    public boolean onlineOnly() {
        return onlineOnly;
    }

    public String search() {
        return search;
    }

    public CreateSession createSession() {
        return createSession;
    }

    public long bountyId() {
        return bountyId;
    }

    public Map<Integer, Long> bountySlots() {
        return bountySlots;
    }
}
