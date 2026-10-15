package com.logvex.crystal.theme;

/**
 * Built-in colour identities. Each preset supplies the four anchor colours the theme
 * engine expands into a full palette; the engine derives panels, borders, glows and
 * combat indicators from these so a preset stays coherent.
 *
 * <p>These are original palettes authored for LOGVEX CRYSTAL; no third-party client's
 * theme data is reproduced.
 */
public enum ThemePreset {

    /** Neutral dark default: keeps the client usable without the pastel identity. */
    DEFAULT("Default", 0xFF6C4CE0, 0xFF4C8CE0, 0xFF8C6CE0, 0xFF101014, 0xFF1A1A22, 0xFFE8E8F0),
    /** The house femboy identity: pink, baby blue and soft lilac on midnight plum. */
    FEMBOY("Femboy", 0xFFFFA6C9, 0xFFA0D8FF, 0xFFD4B0F0, 0xFF1A1020, 0xFF241730, 0xFFF6EAF4),
    /** Sakura Pink: pastel pink and white. */
    SAKURA_PINK("Sakura Pink", 0xFFFFB7D5, 0xFFFFD9E8, 0xFFE7B7E0, 0xFF1A0F1E, 0xFF251626, 0xFFFFF2F7),
    /** Cotton Candy: pink and baby blue. */
    COTTON_CANDY("Cotton Candy", 0xFFFF9EC4, 0xFF8FD3FF, 0xFFC9A7F5, 0xFF140F22, 0xFF1E1730, 0xFFF3EEFF),
    /** Lavender Dream: purple and lilac. */
    LAVENDER_DREAM("Lavender Dream", 0xFFC9A7F5, 0xFFA9C7FF, 0xFFE0B4F0, 0xFF150F26, 0xFF201733, 0xFFF1ECFF),
    /** Midnight Kitty: dark blue with neon pink and cyan. */
    MIDNIGHT_KITTY("Midnight Kitty", 0xFFFF5FA2, 0xFF7CE7FF, 0xFFB77CFF, 0xFF0A0A1A, 0xFF14142A, 0xFFE8E6FF),
    /** Soft Boy: muted pastel blue and pink. */
    SOFT_BOY("Soft Boy", 0xFFF5A9C0, 0xFFA9C6E8, 0xFFC7B8E8, 0xFF171722, 0xFF21212E, 0xFFEDEBF2),
    /** Sakura Femboy: a pink-forward blush palette with baby-blue highlights. */
    SAKURA_FEMBOY("Sakura Femboy", 0xFFFF9EC4, 0xFFB7E0FF, 0xFFE8C7F0, 0xFF1B1020, 0xFF271733, 0xFFFFF0F6),
    /** Sakura Cyber: neon pastel pink and cyan on near-black, for high-contrast combat. */
    SAKURA_CYBER("Sakura Cyber", 0xFFFF6FB5, 0xFF6FE7FF, 0xFFB98CFF, 0xFF07070F, 0xFF12122A, 0xFFEDE8FF);

    private final String label;
    private final int primary;
    private final int secondary;
    private final int tertiary;
    private final int background;
    private final int surface;
    private final int text;

    ThemePreset(String label, int primary, int secondary, int tertiary, int background, int surface, int text) {
        this.label = label;
        this.primary = primary;
        this.secondary = secondary;
        this.tertiary = tertiary;
        this.background = background;
        this.surface = surface;
        this.text = text;
    }

    public String label() {
        return label;
    }

    /** Main accent (cotton-candy pink in the femboy presets). */
    public int primary() {
        return primary;
    }

    /** Secondary accent (baby blue / cyan). */
    public int secondary() {
        return secondary;
    }

    /** Tertiary accent (lavender / soft purple). */
    public int tertiary() {
        return tertiary;
    }

    /** Deep background base (midnight). */
    public int background() {
        return background;
    }

    /** Panel surface base. */
    public int surface() {
        return surface;
    }

    /** Primary foreground text colour. */
    public int text() {
        return text;
    }

    /** Parses a preset by enum name or display label, falling back to {@link #FEMBOY}. */
    public static ThemePreset parse(String value) {
        if (value == null || value.isBlank()) {
            return FEMBOY;
        }
        String needle = value.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        for (ThemePreset preset : values()) {
            if (preset.name().equalsIgnoreCase(needle) || preset.label.equalsIgnoreCase(value.trim())) {
                return preset;
            }
        }
        return FEMBOY;
    }

    public static String[] labels() {
        ThemePreset[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].label;
        }
        return out;
    }
}
