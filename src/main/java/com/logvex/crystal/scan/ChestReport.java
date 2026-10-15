package com.logvex.crystal.scan;

import java.util.ArrayList;
import java.util.List;

/**
 * A completed chest-cluster find, with everything a report needs.
 *
 * <p>{@code nearbyPlayers} holds only player names the client can already see in its own
 * loaded world — the same names that appear on screen. No player information is gathered
 * beyond what the vanilla client tracks locally.
 */
public record ChestReport(String id, String dimension, int centerX, int centerY, int centerZ,
                          int chestCount, long discoveredAtMillis, List<String> nearbyPlayers) {

    public ChestReport {
        nearbyPlayers = List.copyOf(nearbyPlayers);
    }

    /** A short, human-readable one-liner for the local notification and log. */
    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(chestCount).append(" chests at ")
                .append(centerX).append(' ').append(centerY).append(' ').append(centerZ)
                .append(" (").append(shortDimension()).append(')');
        if (!nearbyPlayers.isEmpty()) {
            builder.append(" - players nearby: ").append(String.join(", ", nearbyPlayers));
        }
        return builder.toString();
    }

    /** The dimension path without the {@code minecraft:} prefix, for compact display. */
    public String shortDimension() {
        int colon = dimension.indexOf(':');
        return colon >= 0 ? dimension.substring(colon + 1) : dimension;
    }

    /** Copies the report with an added player, used to build the nearby-player list. */
    public ChestReport withPlayers(List<String> players) {
        return new ChestReport(id, dimension, centerX, centerY, centerZ, chestCount,
                discoveredAtMillis, players);
    }

    public List<String> nearbyPlayersCopy() {
        return new ArrayList<>(nearbyPlayers);
    }
}
