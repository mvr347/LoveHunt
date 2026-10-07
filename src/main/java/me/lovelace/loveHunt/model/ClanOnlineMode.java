package me.lovelace.loveHunt.model;

/**
 * Combined "my clan" / "online only" switch of the "all bounties" menu.
 * Options that need a clan are skipped while cycling when the player has none.
 */
public enum ClanOnlineMode {
    OFF(false, false),
    CLAN(true, false),
    ONLINE(false, true),
    BOTH(true, true);

    private final boolean onlyMyClan;
    private final boolean onlineOnly;

    ClanOnlineMode(boolean onlyMyClan, boolean onlineOnly) {
        this.onlyMyClan = onlyMyClan;
        this.onlineOnly = onlineOnly;
    }

    public boolean onlyMyClan() {
        return onlyMyClan;
    }

    public boolean onlineOnly() {
        return onlineOnly;
    }

    public static ClanOnlineMode of(boolean onlyMyClan, boolean onlineOnly) {
        for (ClanOnlineMode mode : values()) {
            if (mode.onlyMyClan == onlyMyClan && mode.onlineOnly == onlineOnly) return mode;
        }
        return OFF;
    }

    public boolean availableFor(boolean hasClan) {
        return hasClan || !onlyMyClan;
    }

    public ClanOnlineMode next(boolean hasClan) {
        return step(1, hasClan);
    }

    public ClanOnlineMode previous(boolean hasClan) {
        return step(-1, hasClan);
    }

    private ClanOnlineMode step(int direction, boolean hasClan) {
        ClanOnlineMode[] values = values();
        int index = ordinal();
        for (int i = 0; i < values.length; i++) {
            index = (index + direction + values.length) % values.length;
            if (values[index].availableFor(hasClan)) return values[index];
        }
        return OFF;
    }
}
