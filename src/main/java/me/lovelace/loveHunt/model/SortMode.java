package me.lovelace.loveHunt.model;

public enum SortMode {
    NAME,
    REWARD,
    DATE,
    EXPIRING,
    POPULAR;

    public SortMode next() {
        SortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public SortMode previous() {
        SortMode[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
