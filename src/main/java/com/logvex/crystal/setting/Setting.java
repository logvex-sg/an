package com.logvex.crystal.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Base type for every typed, persistent, validated setting a module can expose.
 *
 * <p>Settings are the single source of truth for module configuration: they are
 * serialized to JSON by the config layer, validated on write, and can react to changes
 * via {@link #onChange(Consumer)}. The GUI renders them generically by type.
 *
 * @param <T> the value type
 */
public abstract class Setting<T> {

    private final String name;
    private final String description;
    private final T defaultValue;
    private final Predicate<T> validator;

    private T value;
    private Consumer<T> onChange;
    private final List<BooleanSupplier> visibleWhen = new ArrayList<>();
    private SettingGroup group;

    protected Setting(String name, String description, T defaultValue, Predicate<T> validator) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.validator = validator == null ? v -> true : validator;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    /**
     * Applies a new value. Invalid values are rejected with an
     * {@link IllegalArgumentException} rather than silently clamped, so callers (and the
     * config loader) can report the problem.
     */
    public void set(T newValue) {
        if (!validator.test(newValue)) {
            throw new IllegalArgumentException("Value " + newValue + " rejected for setting '" + name + "'");
        }
        if (!Objects.equals(this.value, newValue)) {
            this.value = newValue;
            if (onChange != null) {
                onChange.accept(newValue);
            }
        }
    }

    /** Applies a value without firing the change callback (used while loading config). */
    public void setSilently(T newValue) {
        if (!validator.test(newValue)) {
            return;
        }
        this.value = newValue;
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isDefault() {
        return Objects.equals(value, defaultValue);
    }

    public Setting<T> onChange(Consumer<T> listener) {
        this.onChange = listener;
        return this;
    }

    /** Setting is only shown/active when all conditions hold (dependent settings). */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibleWhen.add(condition);
        return this;
    }

    public boolean visible() {
        for (BooleanSupplier condition : visibleWhen) {
            if (!condition.getAsBoolean()) {
                return false;
            }
        }
        return true;
    }

    void attachTo(SettingGroup group) {
        this.group = group;
    }

    public SettingGroup group() {
        return group;
    }

    /** Human-readable rendering of the current value, used by the ClickGUI. */
    public String displayValue() {
        return String.valueOf(value);
    }

    /** Writes the value as a JSON primitive/object string; the codec parses it back. */
    public abstract String toJson();

    /** Reads a value from a JSON literal produced by {@link #toJson()}. */
    public abstract void fromJson(String json);

    @Override
    public String toString() {
        return name + "=" + displayValue();
    }
}
