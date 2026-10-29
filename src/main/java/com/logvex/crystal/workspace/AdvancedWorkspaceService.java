package com.logvex.crystal.workspace;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.logvex.crystal.config.ProfileManager;
import com.logvex.crystal.config.ProfileService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Advanced workspaces: a workspace is a saved client configuration that can be applied
 * <em>partially</em> and <em>previewed</em>.
 *
 * <p>{@link ProfileService} already saves and applies all three sections (modules, HUD,
 * appearance) atomically. This service builds on it rather than duplicating it, adding the two
 * operations an advanced user wants and a plain profile cannot do:
 * <ul>
 *   <li><b>Selective apply</b> - swap only the theme, or only the HUD layout, while keeping the
 *       current modules. This is the common "I like this person's HUD but not their toggles"
 *       case.</li>
 *   <li><b>Diff</b> - a count of what applying a workspace would change in each section, so the
 *       user can see the effect before committing to it.</li>
 * </ul>
 *
 * <p>Minecraft-free: it reads and writes the same JSON layers the rest of the config code uses,
 * so it is unit tested without a running client.
 */
public final class AdvancedWorkspaceService {

    /** The configuration sections a workspace can carry and apply independently. */
    public enum Section {
        MODULES("modules"),
        HUD("hud"),
        APPEARANCE("appearance");

        private final String key;

        Section(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Section parse(String value) {
            if (value == null) {
                return null;
            }
            String needle = value.trim().toLowerCase(Locale.ROOT);
            for (Section section : values()) {
                if (section.key.equals(needle) || section.name().equalsIgnoreCase(needle)) {
                    return section;
                }
            }
            return null;
        }
    }

    /** How many top-level entries differ per section between the live config and a workspace. */
    public record Diff(int modules, int hud, int appearance) {
        public int total() {
            return modules + hud + appearance;
        }

        public boolean isNoop() {
            return total() == 0;
        }

        /** A short human line, e.g. {@code "3 changes (modules 1, hud 0, appearance 2)"}. */
        public String describe() {
            if (isNoop()) {
                return "no changes";
            }
            return total() + " change" + (total() == 1 ? "" : "s") + " (modules " + modules
                    + ", hud " + hud + ", appearance " + appearance + ")";
        }
    }

    private final ProfileService profiles;

    public AdvancedWorkspaceService(ProfileService profiles) {
        this.profiles = profiles;
    }

    /** The saved workspaces. */
    public List<String> names() {
        return profiles.names();
    }

    /** Saves the current configuration as a workspace. */
    public boolean save(String name, boolean overwrite) throws IOException {
        return profiles.save(name, overwrite);
    }

    /**
     * Applies the named workspace's selected sections to the live client. An empty section set
     * applies nothing (and returns false), so a caller cannot accidentally apply everything.
     */
    public boolean applySections(String name, Set<Section> sections) {
        if (sections == null || sections.isEmpty()) {
            return false;
        }
        ProfileManager.Profile profile = profiles.profiles().read(name);
        if (profile == null) {
            return false;
        }
        ProfileManager.Profile partial = new ProfileManager.Profile(profile.name(),
                sections.contains(Section.MODULES) ? profile.modules() : null,
                sections.contains(Section.HUD) ? profile.hud() : null,
                sections.contains(Section.APPEARANCE) ? profile.appearance() : null);
        profiles.applyProfile(partial);
        return true;
    }

    /** Applies every section of the named workspace. */
    public boolean apply(String name) {
        return applySections(name, EnumSet.allOf(Section.class));
    }

    /** The change counts between the live configuration and the named workspace. */
    public Diff diff(String name) {
        ProfileManager.Profile stored = profiles.profiles().read(name);
        if (stored == null) {
            return new Diff(0, 0, 0);
        }
        ProfileManager.Profile current = profiles.snapshot(name);
        return new Diff(
                countChanges(current.modules(), stored.modules()),
                countChanges(current.hud(), stored.hud()),
                countChanges(current.appearance(), stored.appearance()));
    }

    /** A per-section diff, only for the sections the user asked about. */
    public Diff diff(String name, Set<Section> sections) {
        Diff full = diff(name);
        if (sections == null || sections.isEmpty()) {
            return new Diff(0, 0, 0);
        }
        return new Diff(
                sections.contains(Section.MODULES) ? full.modules() : 0,
                sections.contains(Section.HUD) ? full.hud() : 0,
                sections.contains(Section.APPEARANCE) ? full.appearance() : 0);
    }

    /** Parses a comma-separated section list, e.g. {@code "hud,appearance"}. Unknown names are skipped. */
    public static Set<Section> parseSections(String value) {
        Set<Section> result = new LinkedHashSet<>();
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return EnumSet.allOf(Section.class);
        }
        for (String token : value.split(",")) {
            Section section = Section.parse(token);
            if (section != null) {
                result.add(section);
            }
        }
        return result;
    }

    /** The workspace names, as a mutable copy. */
    public List<String> describeAll() {
        return new ArrayList<>(names());
    }

    /**
     * Counts the top-level keys whose value differs between two JSON objects. A key present in
     * only one object counts as a change. A null object contributes nothing, so a section absent
     * from an old profile is not reported as a spurious change.
     */
    static int countChanges(JsonObject current, JsonObject stored) {
        if (current == null || stored == null) {
            return 0;
        }
        Set<String> keys = new LinkedHashSet<>(current.keySet());
        keys.addAll(stored.keySet());
        int changes = 0;
        for (String key : keys) {
            if (!valuesEqual(current.get(key), stored.get(key))) {
                changes++;
            }
        }
        return changes;
    }

    /**
     * Compares two JSON values for the purpose of a diff. Numbers are compared by value with a
     * small tolerance rather than by their textual form: a {@code float} written as {@code 0.9f}
     * serialises to {@code "0.9"} and reads back as a lazily-parsed number whose double value
     * differs in the last bits, so {@link JsonElement#equals} would report a phantom change.
     * A key missing on either side counts as a change.
     */
    static boolean valuesEqual(JsonElement a, JsonElement b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.isJsonPrimitive() && b.isJsonPrimitive()
                && a.getAsJsonPrimitive().isNumber() && b.getAsJsonPrimitive().isNumber()) {
            double x = a.getAsDouble();
            double y = b.getAsDouble();
            return Math.abs(x - y) <= 1e-6 * Math.max(1.0, Math.max(Math.abs(x), Math.abs(y)));
        }
        return a.equals(b);
    }
}
