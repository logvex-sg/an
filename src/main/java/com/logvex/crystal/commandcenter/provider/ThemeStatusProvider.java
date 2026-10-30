package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.theme.ThemeManager;
import com.logvex.crystal.theme.ThemeSettings;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the appearance subsystem: the active preset, variant, and the knobs that affect
 * readability.
 *
 * <p>Read-only over the live {@link ThemeManager}. It exists so the Command Center can show
 * the user exactly what the theme is doing, and so the AI context can mention it, without the
 * Command Center ever needing to know the theme's internals.
 */
public final class ThemeStatusProvider implements StatusProvider {

    private final ThemeManager theme;

    public ThemeStatusProvider(ThemeManager theme) {
        this.theme = theme;
    }

    @Override
    public String id() {
        return "theme";
    }

    @Override
    public String title() {
        return "Appearance";
    }

    @Override
    public StatusSection snapshot() {
        if (theme == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Theme", "unavailable")));
        }
        ThemeSettings settings = theme.settings();
        List<StatusEntry> entries = new ArrayList<>();
        entries.add(StatusEntry.ok("Preset", settings.preset.label()));
        entries.add(StatusEntry.ok("Variant", settings.variant.label()));
        entries.add(StatusEntry.ok("Background", Math.round(settings.backgroundOpacity * 100) + "%"));
        entries.add(StatusEntry.ok("Glow", Math.round(settings.glowIntensity * 100) + "%"));
        entries.add(StatusEntry.ok("Animations", settings.reducedMotion ? "reduced" : "on"));
        entries.add(StatusEntry.ok("Decorations", settings.decorations ? "on" : "off"));
        return new StatusSection(id(), title(), settings.preset.label(), entries);
    }
}
