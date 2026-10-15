package com.logvex.crystal.setting;

/** Bounded integer setting, rendered as a slider. */
public final class IntSetting extends Setting<Integer> {

    private final int min;
    private final int max;

    public IntSetting(String name, String description, int defaultValue, int min, int max) {
        super(name, description, defaultValue, v -> v != null && v >= min && v <= max);
        if (min > max) {
            throw new IllegalArgumentException("min > max for setting " + name);
        }
        this.min = min;
        this.max = max;
    }

    public int value() {
        return get();
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    /** Clamped increment helper for GUI sliders. */
    public void adjust(int delta) {
        set(Math.max(min, Math.min(max, get() + delta)));
    }

    /** Sets from a 0..1 slider fraction. */
    public void setFraction(double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        set((int) Math.round(min + clamped * (max - min)));
    }

    public double fraction() {
        return max <= min ? 0 : (get() - min) / (double) (max - min);
    }

    @Override
    public String toJson() {
        return Integer.toString(get());
    }

    @Override
    public void fromJson(String json) {
        try {
            setSilently(Math.max(min, Math.min(max, Integer.parseInt(json.trim()))));
        } catch (NumberFormatException ignored) {
            // Malformed value: keep the default rather than fail the load.
        }
    }
}
