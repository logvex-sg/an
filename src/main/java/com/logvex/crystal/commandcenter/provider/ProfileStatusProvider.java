package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import com.logvex.crystal.config.ProfileService;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the configuration subsystem: the saved profiles and how many there are.
 *
 * <p>Read-only over the {@link ProfileService}. It reports what is stored, never applies
 * anything, so opening the Command Center can never change the user's setup.
 */
public final class ProfileStatusProvider implements StatusProvider {

    private final ProfileService profiles;

    public ProfileStatusProvider(ProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public String id() {
        return "profiles";
    }

    @Override
    public String title() {
        return "Profiles";
    }

    @Override
    public StatusSection snapshot() {
        if (profiles == null) {
            return new StatusSection(id(), title(), "", List.of(StatusEntry.info("Profiles", "unavailable")));
        }
        List<String> names = profiles.names();
        List<StatusEntry> entries = new ArrayList<>();
        entries.add(StatusEntry.ok("Saved", names.size() + " profile(s)"));
        if (!names.isEmpty()) {
            int shown = Math.min(6, names.size());
            entries.add(StatusEntry.ok("Names", String.join(", ", names.subList(0, shown))
                    + (names.size() > shown ? ", \u2026" : "")));
        }
        return new StatusSection(id(), title(), names.size() + " profile(s)", entries);
    }
}
