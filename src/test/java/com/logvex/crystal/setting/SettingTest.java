package com.logvex.crystal.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.logvex.crystal.theme.ThemePreset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Covers the typed settings, which are the single source of truth for configuration. */
class SettingTest {

    @Test
    void booleanSettingTogglesAndSerializes() {
        BooleanSetting setting = new BooleanSetting("Enabled", "", false);
        assertFalse(setting.value());
        setting.toggle();
        assertTrue(setting.value());
        assertEquals("true", setting.toJson());
        setting.fromJson("false");
        assertFalse(setting.value());
        setting.fromJson("garbage");
        assertFalse(setting.value(), "malformed json keeps the current value");
    }

    @Test
    void intSettingClampsFractionAndJson() {
        IntSetting setting = new IntSetting("Radius", "", 10, 0, 20);
        setting.setFraction(0.5);
        assertEquals(10, setting.value());
        setting.setFraction(2.0);
        assertEquals(20, setting.value());
        setting.setFraction(-1.0);
        assertEquals(0, setting.value());
        setting.adjust(5);
        assertEquals(5, setting.value());
        setting.fromJson("999");
        assertEquals(20, setting.value(), "json load clamps to max");
        setting.fromJson("nonsense");
        assertEquals(20, setting.value(), "malformed json keeps value");
    }

    @Test
    void outOfRangeSetIsRejected() {
        IntSetting setting = new IntSetting("X", "", 1, 0, 5);
        assertThrows(IllegalArgumentException.class, () -> setting.set(9));
        DoubleSetting decimal = new DoubleSetting("Y", "", 0.5, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> decimal.set(2.0));
    }

    @Test
    void doubleSettingFormatsAndClamps() {
        DoubleSetting setting = new DoubleSetting("Scale", "", 1.0, 0.5, 2.0);
        setting.setFraction(1.0);
        assertEquals(2.0, setting.value(), 1e-9);
        assertEquals("2.00", setting.displayValue());
        setting.fromJson("NaN");
        assertEquals(2.0, setting.value(), 1e-9);
    }

    @Test
    void stringSettingEscapesAndRestores() {
        StringSetting setting = new StringSetting("Query", "", "diamond", 32);
        setting.set("say \"hi\" \\ now");
        String json = setting.toJson();
        setting.setSilently("");
        setting.fromJson(json);
        assertEquals("say \"hi\" \\ now", setting.value());
        assertThrows(IllegalArgumentException.class, () -> setting.set("x".repeat(33)));
    }

    @Test
    void enumSettingCyclesAndParses() {
        EnumSetting<ThemePreset> setting = new EnumSetting<>("Preset", "", ThemePreset.FEMBOY);
        assertEquals(ThemePreset.FEMBOY, setting.value());
        setting.cycle(1);
        assertEquals(ThemePreset.SAKURA_PINK, setting.value());
        setting.cycle(-1);
        assertEquals(ThemePreset.FEMBOY, setting.value());
        // Cycling wraps around rather than clamping.
        setting.setByIndex(setting.values().size() - 1);
        setting.cycle(1);
        assertEquals(setting.values().get(0), setting.value());
        setting.set(ThemePreset.FEMBOY);
        setting.fromJson("\"Midnight Kitty\"");
        assertEquals(ThemePreset.FEMBOY, setting.value(), "unknown constant keeps the current value");
        setting.fromJson("\"MIDNIGHT_KITTY\"");
        assertEquals(ThemePreset.MIDNIGHT_KITTY, setting.value());
    }

    @Test
    void colorSettingRoundTripsArgb() {
        ColorSetting setting = new ColorSetting("Accent", "", 0xFFFF0000);
        assertEquals(0xFF0000, setting.rgb());
        assertEquals(1.0f, setting.alpha(), 1e-6);
        setting.fromJson("" + 0x80123456);
        assertEquals(0x80123456, setting.argb());
        assertEquals(0x123456, setting.rgb());
    }

    @Test
    void changeListenerFiresOnlyOnRealChange() {
        AtomicInteger fired = new AtomicInteger();
        BooleanSetting setting = new BooleanSetting("Flag", "", false);
        setting.onChange(value -> fired.incrementAndGet());
        setting.set(false);
        assertEquals(0, fired.get(), "no-op set must not fire");
        setting.set(true);
        assertEquals(1, fired.get());
        setting.set(true);
        assertEquals(1, fired.get());
    }

    @Test
    void visibilityDependsOnOtherSettings() {
        BooleanSetting master = new BooleanSetting("Master", "", false);
        IntSetting child = new IntSetting("Child", "", 1, 0, 5);
        child.visibleWhen(master::value);
        assertFalse(child.visible());
        master.set(true);
        assertTrue(child.visible());
    }

    @Test
    void groupsCollectSettingsAndReportVisibility() {
        SettingGroup group = new SettingGroup("Targeting");
        BooleanSetting setting = new BooleanSetting("Aim", "", true);
        group.add(setting);
        assertEquals(1, group.settings().size());
        assertTrue(group.hasVisibleSettings());
        assertEquals(group, setting.group());
        BooleanSetting hidden = new BooleanSetting("Hidden", "", true);
        hidden.visibleWhen(() -> false);
        group.add(hidden);
        assertTrue(group.hasVisibleSettings(), "one visible setting is enough");
    }
}
