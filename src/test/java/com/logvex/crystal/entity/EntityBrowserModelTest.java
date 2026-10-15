package com.logvex.crystal.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Box;
import org.junit.jupiter.api.Test;

/** Tests for {@link EntityBrowserModel}: filtering, search and sorting. */
class EntityBrowserModelTest {

    private static EntityInfo player(String name, double distance, float health) {
        return new EntityInfo(UUID.randomUUID(), 1, EntityCategory.PLAYER, name,
                0, 0, 0, 0, 0, 0, distance, new Box(0, 0, 0, 1, 1, 1),
                health, 20, 0, true, false, false, true, 0);
    }

    private static EntityInfo item(String name, double distance) {
        return new EntityInfo(UUID.randomUUID(), 2, EntityCategory.ITEM, name,
                0, 0, 0, 0, 0, 0, distance, new Box(0, 0, 0, 0.25, 0.25, 0.25),
                0, 0, 0, false, false, false, true, 0);
    }

    @Test
    void applyDoesNotMutateTheInputList() {
        EntityBrowserModel model = new EntityBrowserModel();
        List<EntityInfo> input = List.of(player("A", 5, 20), player("B", 1, 20));
        List<EntityInfo> before = List.copyOf(input);
        model.apply(input);
        assertEquals(before, input);
    }

    @Test
    void defaultSortIsNearestFirst() {
        EntityBrowserModel model = new EntityBrowserModel();
        List<EntityInfo> result = model.apply(List.of(player("Far", 40, 20), player("Near", 2, 20)));
        assertEquals("Near", result.get(0).name());
        assertEquals("Far", result.get(1).name());
    }

    @Test
    void sortByNameAndDirection() {
        EntityBrowserModel model = new EntityBrowserModel();
        model.setSort(EntityBrowserModel.Sort.NAME);
        List<EntityInfo> ascending = model.apply(List.of(player("Zed", 1, 20), player("Amy", 9, 20)));
        assertEquals("Amy", ascending.get(0).name());
        model.toggleDirection();
        List<EntityInfo> descending = model.apply(List.of(player("Zed", 1, 20), player("Amy", 9, 20)));
        assertEquals("Zed", descending.get(0).name());
    }

    @Test
    void searchMatchesNameOrCategory() {
        EntityBrowserModel model = new EntityBrowserModel();
        model.setSearch("zom");
        assertTrue(model.matches(player("Zombie", 1, 20)));
        assertFalse(model.matches(player("Steve", 1, 20)));
        model.setSearch("item");
        assertTrue(model.matches(item("diamond", 1)));
    }

    @Test
    void distanceAndCategoryFilters() {
        EntityBrowserModel model = new EntityBrowserModel();
        model.setMaxDistance(10);
        assertTrue(model.matches(player("A", 5, 20)));
        assertFalse(model.matches(player("A", 50, 20)));
        model.setMaxDistance(0);
        model.toggleCategory(EntityCategory.ITEM);
        assertTrue(model.matches(item("diamond", 100)));
        assertFalse(model.matches(player("A", 5, 20)));
    }

    @Test
    void emptyCategorySetMeansEveryCategory() {
        EntityBrowserModel model = new EntityBrowserModel();
        assertTrue(model.matches(player("A", 1, 20)));
        assertTrue(model.matches(item("diamond", 1)));
    }

    @Test
    void sortByHealthAscending() {
        EntityBrowserModel model = new EntityBrowserModel();
        model.setSort(EntityBrowserModel.Sort.HEALTH);
        List<EntityInfo> result = model.apply(List.of(player("Healthy", 1, 20), player("Hurt", 1, 3)));
        assertEquals("Hurt", result.get(0).name());
    }

    @Test
    void columnTogglesAreIndependent() {
        EntityBrowserModel model = new EntityBrowserModel();
        assertTrue(model.shows(EntityBrowserModel.Column.NAME));
        model.toggleColumn(EntityBrowserModel.Column.NAME);
        assertFalse(model.shows(EntityBrowserModel.Column.NAME));
        assertTrue(model.shows(EntityBrowserModel.Column.CATEGORY));
    }
}
