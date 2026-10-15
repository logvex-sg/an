package com.logvex.crystal.setting;

import java.util.List;

/** Enum setting, rendered as a cycling dropdown. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {

    private final Class<E> type;
    private final List<E> values;

    public EnumSetting(String name, String description, E defaultValue) {
        super(name, description, defaultValue, v -> v != null);
        this.type = defaultValue.getDeclaringClass();
        this.values = List.of(type.getEnumConstants());
    }

    public E value() {
        return get();
    }

    public List<E> values() {
        return values;
    }

    /** Cycles to the next constant; used by ClickGUI clicks on an enum row. */
    public void cycle(int direction) {
        int index = values.indexOf(get());
        int next = Math.floorMod(index + direction, values.size());
        set(values.get(next));
    }

    public void setByIndex(int index) {
        if (index >= 0 && index < values.size()) {
            set(values.get(index));
        }
    }

    public int index() {
        return values.indexOf(get());
    }

    @Override
    public String displayValue() {
        return get().name();
    }

    @Override
    public String toJson() {
        return "\"" + get().name() + "\"";
    }

    @Override
    public void fromJson(String json) {
        String name = json == null ? "" : json.trim().replace("\"", "");
        for (E candidate : values) {
            if (candidate.name().equalsIgnoreCase(name)) {
                setSilently(candidate);
                return;
            }
        }
        // Unknown enum constant (e.g. a downgraded config): keep the default.
    }

    public List<String> names() {
        return values.stream().map(Enum::name).toList();
    }
}
