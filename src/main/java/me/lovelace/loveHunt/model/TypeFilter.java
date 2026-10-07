package me.lovelace.loveHunt.model;

public enum TypeFilter {
    ALL,
    PLAYER,
    CLAN,
    SERVER;

    public TypeFilter next() {
        TypeFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public TypeFilter previous() {
        TypeFilter[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
