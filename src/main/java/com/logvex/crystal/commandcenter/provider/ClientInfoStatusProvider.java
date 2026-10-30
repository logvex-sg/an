package com.logvex.crystal.commandcenter.provider;

import com.logvex.crystal.commandcenter.StatusEntry;
import com.logvex.crystal.commandcenter.StatusProvider;
import com.logvex.crystal.commandcenter.StatusSection;
import java.util.ArrayList;
import java.util.List;

/**
 * Reports the static identity of the running client: the mod version, the Minecraft version,
 * the loader and how many mods are present.
 *
 * <p>The values are plain strings supplied at bootstrap, so this provider is
 * Minecraft-independent and directly unit testable, and it never touches the loader again
 * after start-up.
 */
public final class ClientInfoStatusProvider implements StatusProvider {

    private final String modVersion;
    private final String minecraftVersion;
    private final String loader;
    private final int modCount;

    public ClientInfoStatusProvider(String modVersion, String minecraftVersion, String loader, int modCount) {
        this.modVersion = modVersion == null ? "unknown" : modVersion;
        this.minecraftVersion = minecraftVersion == null ? "unknown" : minecraftVersion;
        this.loader = loader == null ? "unknown" : loader;
        this.modCount = modCount;
    }

    @Override
    public String id() {
        return "client";
    }

    @Override
    public String title() {
        return "Client";
    }

    @Override
    public StatusSection snapshot() {
        List<StatusEntry> entries = new ArrayList<>();
        entries.add(StatusEntry.ok("LOGVEX CRYSTAL", modVersion));
        entries.add(StatusEntry.ok("Minecraft", minecraftVersion));
        entries.add(StatusEntry.ok("Loader", loader));
        entries.add(StatusEntry.ok("Mods", modCount + " loaded"));
        return new StatusSection(id(), title(), "v" + modVersion + " on " + minecraftVersion, entries);
    }
}
