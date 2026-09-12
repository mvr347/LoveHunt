package me.lovelace.loveHunt.textures;

/**
 * Централизованное хранилище base64 текстур голов (skull textures), используемых в GUI LoveHunt.
 * <p>
 * Все base64-литералы текстур голов должны объявляться здесь, а не хардкодиться по месту
 * использования — так плагин следует единой точке правды для GUI-текстур, вместо дублирования
 * одних и тех же строк в разных классах.
 */
public final class HeadTextures {

    private HeadTextures() {
        // Утилитарный класс-константа, инстанцирование не предполагается
    }

    /**
     * Текстура головы-заглушки, используемая по умолчанию для пустых списков розысков,
     * если {@code heads.empty-notice-base64} не задан в config.yml.
     */
    public static final String EMPTY_NOTICE_DEFAULT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzYxODczMWUwNjMzNzlhZWJmODJmMWQ2NGM0MTljOTBkN2YwYzE2NDhjNTQ4ZTliNjE1MWIxYmFiYTY2ZDcyMyJ9fX0=";
}
