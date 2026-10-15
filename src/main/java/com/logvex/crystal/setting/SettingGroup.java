package com.logvex.crystal.setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named, collapsible group of settings inside a module (e.g. "Rendering", "Targeting").
 * The ClickGUI draws the group header and indents its settings beneath it.
 */
public final class SettingGroup {

    private final String name;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Setting<?>> view = Collections.unmodifiableList(settings);

    public SettingGroup(String name) {
        this.name = name == null || name.isBlank() ? "General" : name;
    }

    public String name() {
        return name;
    }

    public void add(Setting<?> setting) {
        setting.attachTo(this);
        settings.add(setting);
    }

    public List<Setting<?>> settings() {
        return view;
    }

    public boolean hasVisibleSettings() {
        for (Setting<?> setting : settings) {
            if (setting.visible()) {
                return true;
            }
        }
        return false;
    }
}
