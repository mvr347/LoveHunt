package me.lovelace.loveHunt.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lore building blocks of the GUI lore standard (docs GUI_LORE_STYLE, "Роли цветов" and "Неактивная кнопка"): the
 * separator, one click hint per line and the block of a disabled button. Every result is a MiniMessage string.
 * <p>
 * The texts live in lang.yml under {@code style.*}. {@link Style} reads them, and every key has the same default here,
 * so a lore never breaks when a server file lacks a key; {@code LoreBlocksTest} keeps the two in sync.
 */
public final class LoreBlocks {

    /** Mouse click a hint belongs to; {@link #key} is the lang key under {@code style.click}. */
    public enum Click {
        LEFT("left", "ЛКМ"), RIGHT("right", "ПКМ"), SHIFT_LEFT("shift-left", "Shift+ЛКМ"), SHIFT_RIGHT("shift-right", "Shift+ПКМ");

        public final String key;
        public final String label;

        Click(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    /** One click line: which click does what ({@code action} is already localised text). */
    public record Hint(Click click, String action) {
    }

    /** Source of the {@code style.*} texts: a lang path (for example {@code style.separator}) to its raw text, or null. */
    @FunctionalInterface
    public interface Style {
        String raw(String path);

        /** Only the built-in defaults (tests, and a safety net before lang.yml is loaded). */
        Style DEFAULTS = path -> null;
    }

    public static final String SEPARATOR = "<dark_gray><st>─────────────────────</st>";

    /** Defaults of every {@code style.*} key; they must equal lang.yml (see LoreBlocksTest). */
    private static final Map<String, String> DEFAULT_TEXT = Map.ofEntries(
            Map.entry("style.separator", SEPARATOR),
            Map.entry("style.click.left", "<green>ЛКМ <dark_gray>— <gray>{action}"),
            Map.entry("style.click.right", "<gold>ПКМ <dark_gray>— <gray>{action}"),
            Map.entry("style.click.shift-left", "<yellow>Shift+ЛКМ <dark_gray>— <gray>{action}"),
            Map.entry("style.click.shift-right", "<yellow>Shift+ПКМ <dark_gray>— <gray>{action}"),
            Map.entry("style.reason", "<red>Причина: <gray>{reason}"),
            Map.entry("style.blocked-click", "<dark_gray>{click} — {action} (заблокировано)"),
            Map.entry("style.disabled-name", "<gray>{name} <dark_gray>| <red>Недоступно"),
            Map.entry("style.status.unavailable", "<red>✕ Действие недоступно"),
            Map.entry("style.status.locked", "<red>✕ Действие заблокировано"),
            Map.entry("style.status.cooldown", "<yellow>⌛ Перезарядка"),
            Map.entry("style.status.max", "<green>✔ Достигнут предел"));

    private LoreBlocks() {
    }

    /** Default text of a style key, for the lang-sync test. */
    public static Map<String, String> defaults() {
        return DEFAULT_TEXT;
    }

    private static String text(Style style, String path) {
        String value = style == null ? null : style.raw(path);
        return value != null ? value : DEFAULT_TEXT.getOrDefault(path, path);
    }

    public static String separator(Style style) {
        return text(style, "style.separator");
    }

    /** One click line: {@code ЛКМ — действие}; the colour comes from the click type. */
    public static String click(Style style, Click click, String action) {
        return text(style, "style.click." + click.key).replace("{action}", action);
    }

    public static String click(Style style, Hint hint) {
        return click(style, hint.click(), hint.action());
    }

    public static Hint hint(Click click, String action) {
        return new Hint(click, action);
    }

    /** Separator followed by one line per hint. No hints give no lines, so there is never a dangling separator. */
    public static List<String> clicks(Style style, List<Hint> hints) {
        if (hints.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(hints.size() + 1);
        lines.add(separator(style));
        for (Hint hint : hints) {
            lines.add(click(style, hint));
        }
        return lines;
    }

    public static List<String> clicks(Style style, Hint... hints) {
        return clicks(style, List.of(hints));
    }

    /**
     * Block of a disabled button: separator, status line ({@code statusKey} under {@code style.status}), the
     * {@code Причина:} line and the click that would have worked, shown as blocked.
     */
    public static List<String> blocked(Style style, String statusKey, String reason, Hint blockedHint) {
        List<String> lines = new ArrayList<>(4);
        lines.add(separator(style));
        lines.add(text(style, "style.status." + statusKey));
        if (reason != null && !reason.isBlank()) {
            lines.add(text(style, "style.reason").replace("{reason}", reason));
        }
        if (blockedHint != null) {
            lines.add(text(style, "style.blocked-click")
                    .replace("{click}", blockedHint.click().label)
                    .replace("{action}", blockedHint.action()));
        }
        return lines;
    }

    /** Name of a disabled button: {@code Имя | Недоступно}. */
    public static String disabledName(Style style, String name) {
        return text(style, "style.disabled-name").replace("{name}", name);
    }
}
