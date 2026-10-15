package com.logvex.crystal.setting;

/** On/off setting. */
public final class BooleanSetting extends Setting<Boolean> {

    public BooleanSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue, v -> v != null);
    }

    public boolean value() {
        return get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public String displayValue() {
        return get() ? "on" : "off";
    }

    @Override
    public String toJson() {
        return Boolean.toString(get());
    }

    @Override
    public void fromJson(String json) {
        if (json != null && (json.equals("true") || json.equals("false"))) {
            setSilently(Boolean.parseBoolean(json));
        }
    }
}
