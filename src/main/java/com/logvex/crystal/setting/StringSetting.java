package com.logvex.crystal.setting;

/** Free-text setting. */
public final class StringSetting extends Setting<String> {

    private final int maxLength;

    public StringSetting(String name, String description, String defaultValue, int maxLength) {
        super(name, description, defaultValue == null ? "" : defaultValue,
                v -> v != null && v.length() <= maxLength);
        this.maxLength = maxLength;
    }

    public String value() {
        return get();
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    public String toJson() {
        return "\"" + get().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override
    public void fromJson(String json) {
        if (json == null) {
            return;
        }
        String trimmed = json.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\");
        }
        setSilently(trimmed);
    }
}
