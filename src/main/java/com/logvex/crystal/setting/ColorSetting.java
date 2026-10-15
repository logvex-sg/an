package com.logvex.crystal.setting;

/**
 * ARGB colour setting. Stored as an {@code int} so it maps directly to the packed colour
 * format used by the rendering and GUI layers.
 */
public final class ColorSetting extends Setting<Integer> {

    public ColorSetting(String name, String description, int defaultValue) {
        super(name, description, defaultValue, v -> v != null);
    }

    public int argb() {
        return get();
    }

    public int rgb() {
        return get() & 0x00FFFFFF;
    }

    public float alpha() {
        return ((get() >>> 24) & 0xFF) / 255.0F;
    }

    public static int of(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    @Override
    public String displayValue() {
        return String.format("#%08X", get());
    }

    @Override
    public String toJson() {
        return Integer.toString(get());
    }

    @Override
    public void fromJson(String json) {
        try {
            setSilently(Integer.parseInt(json.trim()));
        } catch (NumberFormatException ignored) {
            // Malformed value: keep the default.
        }
    }
}
