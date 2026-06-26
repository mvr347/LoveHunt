package me.lovelace.loveHunt.gui;

import me.lovelace.loveHunt.model.CreateSession;
import me.lovelace.loveHunt.model.SortMode;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class LoveHuntHolder implements InventoryHolder {
    private final MenuType type;
    private final int page;
    private final SortMode sortMode;
    private final boolean onlyMine;
    private final String search;
    private final CreateSession createSession;
    private final long bountyId;
    private final Map<Integer, Long> bountySlots = new HashMap<>();
    private Inventory inventory;

    public LoveHuntHolder(MenuType type, int page, SortMode sortMode, boolean onlyMine, String search, CreateSession createSession, long bountyId) {
        this.type = type;
        this.page = page;
        this.sortMode = sortMode;
        this.onlyMine = onlyMine;
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

    public int page() {
        return page;
    }

    public SortMode sortMode() {
        return sortMode;
    }

    public boolean onlyMine() {
        return onlyMine;
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
