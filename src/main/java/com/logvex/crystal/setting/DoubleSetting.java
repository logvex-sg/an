package com.logvex.crystal.setting;

/** Bounded decimal setting, rendered as a slider. */
public final class DoubleSetting extends Setting<Double> {

    private final double min;
    private final double max;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max) {
        super(name, description, defaultValue, v -> v != null && !v.isNaN() && v >= min && v <= max);
        if (min > max) {
            throw new IllegalArgumentException("min > max for setting " + name);
        }
        this.min = min;
        this.max = max;
    }

    public double value() {
        return get();
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public void adjust(double delta) {
        set(Math.max(min, Math.min(max, get() + delta)));
    }

    /** Sets from a 0..1 slider fraction. */
    public void setFraction(double fraction) {
        double clamped = Math.max(0, Math.min(1, fraction));
        set(min + clamped * (max - min));
    }

    public double fraction() {
        return max <= min ? 0 : (get() - min) / (max - min);
    }

    @Override
    public String displayValue() {
        return String.format("%.2f", get());
    }

    @Override
    public String toJson() {
        return Double.toString(get());
    }

    @Override
    public void fromJson(String json) {
        try {
            double v = Double.parseDouble(json.trim());
            if (!Double.isNaN(v)) {
                setSilently(Math.max(min, Math.min(max, v)));
            }
        } catch (NumberFormatException ignored) {
            // Malformed value: keep the default.
        }
    }
}
