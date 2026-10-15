package com.logvex.crystal.config;

import com.google.gson.JsonObject;
import com.logvex.crystal.gui.HudLayout;
import com.logvex.crystal.theme.ThemeSettings;
import java.io.IOException;
import java.util.List;

/**
 * Ties the {@link ProfileManager} to the live configuration layers so a profile can be
 * snapshotted from, and applied to, the running client.
 *
 * <p>It deliberately owns no state of its own: it reads the current module/HUD/appearance
 * objects, and on apply it writes straight back through those same objects, so a profile
 * switch is indistinguishable from the user having changed each setting by hand. UI code
 * never touches the JSON; it calls {@link #apply(String)} and gets a result.
 */
public final class ProfileService {

    private final ProfileManager profiles;
    private final ConfigManager config;
    private final HudLayoutStore hudStore;
    private final AppearanceStore appearanceStore;
    private final HudLayout hudLayout;
    private final ThemeSettings themeSettings;
    private final Runnable afterApply;

    public ProfileService(ProfileManager profiles, ConfigManager config, HudLayoutStore hudStore,
                          AppearanceStore appearanceStore, HudLayout hudLayout, ThemeSettings themeSettings,
                          Runnable afterApply) {
        this.profiles = profiles;
        this.config = config;
        this.hudStore = hudStore;
        this.appearanceStore = appearanceStore;
        this.hudLayout = hudLayout;
        this.themeSettings = themeSettings;
        this.afterApply = afterApply;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public List<String> names() {
        return profiles.names();
    }

    /** Captures the current live configuration as a named profile (in memory, not written). */
    public ProfileManager.Profile snapshot(String name) {
        return new ProfileManager.Profile(ProfileManager.sanitize(name), config.toJson(),
                HudLayoutStore.toJson(hudLayout), AppearanceStore.toJson(themeSettings));
    }

    /**
     * Saves the current configuration under {@code name}. Refuses to overwrite a valid
     * existing profile unless {@code overwrite} is true.
     *
     * @return true when a file was written
     */
    public boolean save(String name, boolean overwrite) throws IOException {
        return profiles.write(snapshot(name), overwrite);
    }

    /**
     * Loads a profile and applies all three sections to the live client, then runs the
     * post-apply hook (which re-syncs derived state such as HUD visibility).
     *
     * @return true when the profile existed and was applied
     */
    public boolean apply(String name) {
        ProfileManager.Profile profile = profiles.read(name);
        if (profile == null) {
            return false;
        }
        applyProfile(profile);
        return true;
    }

    /** Applies an in-memory profile (used for import and for the starter profiles). */
    public void applyProfile(ProfileManager.Profile profile) {
        if (profile.modules() != null) {
            config.apply(profile.modules());
        }
        if (profile.hud() != null) {
            HudLayoutStore.apply(profile.hud(), hudLayout);
        }
        if (profile.appearance() != null) {
            copyInto(AppearanceStore.fromJson(profile.appearance()), themeSettings);
        }
        if (afterApply != null) {
            afterApply.run();
        }
    }

    /** Copies every appearance field from a parsed snapshot into the live settings. */
    public static void copyInto(ThemeSettings source, ThemeSettings target) {
        ThemeSettings copy = source.copy();
        target.preset = copy.preset;
        target.variant = copy.variant;
        target.primaryOverride = copy.primaryOverride;
        target.secondaryOverride = copy.secondaryOverride;
        target.backgroundOpacity = copy.backgroundOpacity;
        target.gradientIntensity = copy.gradientIntensity;
        target.glowIntensity = copy.glowIntensity;
        target.cornerRadius = copy.cornerRadius;
        target.uiScale = copy.uiScale;
        target.animationSpeed = copy.animationSpeed;
        target.decorations = copy.decorations;
        target.catCosmetics = copy.catCosmetics;
        target.reducedMotion = copy.reducedMotion;
        target.blur = copy.blur;
        target.customCursor = copy.customCursor;
        target.guiSounds = copy.guiSounds;
        target.splashText = copy.splashText;
        target.easterEgg = copy.easterEgg;
    }

    /** Exports a profile document for the given name, or null when it does not exist. */
    public JsonObject export(String name) {
        ProfileManager.Profile profile = profiles.read(name);
        return profile == null ? null : ProfileManager.toJson(profile);
    }

    /**
     * Imports a profile document under a unique name and returns the stored name, or null
     * when the document is malformed/newer. Never overwrites: a name clash gets a suffix.
     */
    public String importProfile(JsonObject document) throws IOException {
        ProfileManager.Profile parsed = ProfileManager.fromJson(document);
        if (parsed == null) {
            return null;
        }
        String name = parsed.name();
        int suffix = 2;
        while (profiles.exists(name)) {
            name = parsed.name() + "-" + suffix++;
        }
        boolean written = profiles.write(new ProfileManager.Profile(name, parsed.modules(),
                parsed.hud(), parsed.appearance()), false);
        return written ? name : null;
    }
}
