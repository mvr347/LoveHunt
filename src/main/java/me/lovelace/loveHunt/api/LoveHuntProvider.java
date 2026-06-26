package me.lovelace.loveHunt.api;

public final class LoveHuntProvider {
    private static LoveHuntAPI api = null;

    private LoveHuntProvider() {}

    public static LoveHuntAPI get() {
        if (api == null) {
            throw new IllegalStateException("LoveHuntAPI is not initialized yet");
        }
        return api;
    }

    public static void register(LoveHuntAPI implementation) {
        api = implementation;
    }

    public static void unregister() {
        api = null;
    }
}