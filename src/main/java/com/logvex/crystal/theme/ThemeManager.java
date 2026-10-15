package com.logvex.crystal.theme;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime owner of the active theme.
 *
 * <p>Holds the mutable {@link ThemeSettings} and a cached immutable {@link Palette}. Any
 * mutation goes through {@link #update()} (or the helper methods), which rebuilds the
 * palette and fires listeners so screens and HUD elements can react immediately — no
 * client restart. The palette reference is volatile and swapped atomically, so the render
 * thread reads a consistent object without locking.
 */
public final class ThemeManager {

    /** Receives a callback whenever the active palette changes. */
    public interface Listener {
        void onThemeChanged(Palette palette);
    }

    private final ThemeSettings settings;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile Palette palette;

    public ThemeManager() {
        this(new ThemeSettings());
    }

    public ThemeManager(ThemeSettings settings) {
        this.settings = settings;
        this.settings.clamp();
        this.palette = ThemeEngine.build(this.settings);
    }

    /** The current palette. Safe to read from the render thread. */
    public Palette palette() {
        return palette;
    }

    /** The live, mutable settings. Callers must call {@link #update()} after editing. */
    public ThemeSettings settings() {
        return settings;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Rebuilds the palette from the current settings and notifies listeners. */
    public void update() {
        settings.clamp();
        Palette rebuilt = ThemeEngine.build(settings);
        this.palette = rebuilt;
        for (Listener listener : listeners) {
            listener.onThemeChanged(rebuilt);
        }
    }

    /** Switches preset and rebuilds. */
    public void applyPreset(ThemePreset preset) {
        settings.applyPreset(preset);
        update();
    }

    public void setVariant(ThemeVariant variant) {
        settings.variant = variant;
        update();
    }

    public void setPrimary(int argb) {
        settings.setPrimary(argb);
        update();
    }

    public void setSecondary(int argb) {
        settings.setSecondary(argb);
        update();
    }

    /**
     * Applies a named preset. Accepts enum names or display labels
     * ("Sakura Pink", "Midnight Kitty", …). Returns false if the name is unknown.
     */
    public boolean applyPreset(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String needle = name.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        for (ThemePreset preset : ThemePreset.values()) {
            if (preset.name().equalsIgnoreCase(needle) || preset.label().equalsIgnoreCase(name.trim())) {
                applyPreset(preset);
                return true;
            }
        }
        return false;
    }
}
