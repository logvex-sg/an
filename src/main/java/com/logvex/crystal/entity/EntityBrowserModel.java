package com.logvex.crystal.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The filter/sort/column state behind the entity browser, kept separate from the screen so
 * the behaviour is unit tested without a running client.
 *
 * <p>It works entirely on immutable {@link EntityInfo} values: {@link #apply(List)} copies
 * its input, filters it and returns a new, sorted list, so the caller's snapshot is never
 * mutated and a list captured for one frame is safe to read for that frame.
 *
 * <p>Search matches a case-insensitive substring of the display name or the category label.
 * Sorting is stable so entities that compare equal keep their incoming order.
 */
public final class EntityBrowserModel {

    /** Which attribute the list is sorted by. */
    public enum Sort {
        DISTANCE("Distance"),
        NAME("Name"),
        HEALTH("Health"),
        CATEGORY("Category");

        private final String label;

        Sort(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** A configurable column. */
    public enum Column {
        NAME("Name"),
        CATEGORY("Type"),
        DISTANCE("Dist"),
        HEALTH("HP"),
        ARMOR("Armor"),
        FRIEND("Friend"),
        SPEED("Speed");

        private final String label;

        Column(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private String search = "";
    private final Set<EntityCategory> categories = EnumSet.noneOf(EntityCategory.class);
    private double minDistance;
    private double maxDistance = Double.MAX_VALUE;
    private Sort sort = Sort.DISTANCE;
    private boolean ascending = true;
    private final Set<Column> columns = EnumSet.of(
            Column.NAME, Column.CATEGORY, Column.DISTANCE, Column.HEALTH);

    public String search() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search == null ? "" : search;
    }

    public Set<EntityCategory> categories() {
        return categories;
    }

    /** Toggles a category on/off; an empty set means "every category". */
    public void toggleCategory(EntityCategory category) {
        if (category == null) {
            return;
        }
        if (!categories.remove(category)) {
            categories.add(category);
        }
    }

    public double minDistance() {
        return minDistance;
    }

    public void setMinDistance(double value) {
        this.minDistance = Math.max(0, value);
    }

    public double maxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(double value) {
        this.maxDistance = value <= 0 ? Double.MAX_VALUE : value;
    }

    public Sort sort() {
        return sort;
    }

    public void setSort(Sort sort) {
        this.sort = sort == null ? Sort.DISTANCE : sort;
    }

    public boolean ascending() {
        return ascending;
    }

    public void setAscending(boolean ascending) {
        this.ascending = ascending;
    }

    /** Flips the sort direction. */
    public void toggleDirection() {
        ascending = !ascending;
    }

    public Set<Column> columns() {
        return columns;
    }

    public boolean shows(Column column) {
        return columns.contains(column);
    }

    public void toggleColumn(Column column) {
        if (column == null) {
            return;
        }
        if (!columns.remove(column)) {
            columns.add(column);
        }
    }

    /** True when {@code info} passes the search and distance/category filters. */
    public boolean matches(EntityInfo info) {
        if (info == null) {
            return false;
        }
        if (info.distance() < minDistance || info.distance() > maxDistance) {
            return false;
        }
        if (!categories.isEmpty() && !categories.contains(info.category())) {
            return false;
        }
        if (!search.isBlank()) {
            String needle = search.trim().toLowerCase(Locale.ROOT);
            boolean hit = info.name().toLowerCase(Locale.ROOT).contains(needle)
                    || info.category().label().toLowerCase(Locale.ROOT).contains(needle);
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    /** Filters and sorts a copy of {@code input}. The input is never modified. */
    public List<EntityInfo> apply(List<EntityInfo> input) {
        List<EntityInfo> result = new ArrayList<>();
        if (input != null) {
            for (EntityInfo info : input) {
                if (matches(info)) {
                    result.add(info);
                }
            }
        }
        Comparator<EntityInfo> comparator = comparatorFor(sort);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        result.sort(comparator);
        return result;
    }

    private static Comparator<EntityInfo> comparatorFor(Sort sort) {
        return switch (sort) {
            case DISTANCE -> Comparator.comparingDouble(EntityInfo::distance);
            case NAME -> Comparator.comparing(EntityInfo::name, String.CASE_INSENSITIVE_ORDER);
            case HEALTH -> Comparator.comparingDouble(EntityInfo::healthFraction);
            case CATEGORY -> Comparator.comparing(info -> info.category().ordinal());
        };
    }
}
