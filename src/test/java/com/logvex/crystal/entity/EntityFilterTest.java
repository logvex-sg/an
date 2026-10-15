package com.logvex.crystal.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EntityFilter}, the shared entity-visibility decision. These run
 * without a Minecraft runtime: the filter takes plain values, so every branch is reachable
 * by calling {@code accepts} directly.
 */
class EntityFilterTest {

    @Test
    void emptyCategoriesAcceptsEverythingInRange() {
        EntityFilter filter = EntityFilter.builder().range(32).build();
        assertTrue(filter.accepts(EntityCategory.PLAYER, false, false, false, true, 5, "Steve"));
        assertTrue(filter.accepts(EntityCategory.ITEM, false, false, false, false, 30, ""));
    }

    @Test
    void selfIsAlwaysRejected() {
        EntityFilter filter = EntityFilter.builder().build();
        assertFalse(filter.accepts(EntityCategory.PLAYER, true, false, false, true, 1, "me"));
    }

    @Test
    void categoryAllowListIsHonoured() {
        EntityFilter playersOnly = EntityFilter.builder().categories(EntityCategory.PLAYER).build();
        assertTrue(playersOnly.accepts(EntityCategory.PLAYER, false, false, false, true, 4, "Alex"));
        assertFalse(playersOnly.accepts(EntityCategory.HOSTILE, false, false, false, true, 4, "Zombie"));
    }

    @Test
    void distanceBoundsAreInclusive() {
        EntityFilter filter = EntityFilter.builder().minDistance(2).range(10).build();
        assertFalse(filter.accepts(EntityCategory.PLAYER, false, false, false, true, 1.9, "a"));
        assertTrue(filter.accepts(EntityCategory.PLAYER, false, false, false, true, 2.0, "a"));
        assertTrue(filter.accepts(EntityCategory.PLAYER, false, false, false, true, 10.0, "a"));
        assertFalse(filter.accepts(EntityCategory.PLAYER, false, false, false, true, 10.1, "a"));
    }

    @Test
    void invisibleExcludedUnlessAllowed() {
        EntityFilter hideInvisible = EntityFilter.builder().build();
        assertFalse(hideInvisible.accepts(EntityCategory.PLAYER, false, false, true, true, 4, "ghost"));

        EntityFilter showInvisible = EntityFilter.builder().invisible(true).build();
        assertTrue(showInvisible.accepts(EntityCategory.PLAYER, false, false, true, true, 4, "ghost"));
    }

    @Test
    void friendRulesRespectTheToggles() {
        EntityFilter onlyFriends = EntityFilter.builder().nonFriends(false).build();
        assertTrue(onlyFriends.accepts(EntityCategory.PLAYER, false, true, false, true, 4, "buddy"));
        assertFalse(onlyFriends.accepts(EntityCategory.PLAYER, false, false, false, true, 4, "stranger"));

        EntityFilter hideFriends = EntityFilter.builder().friends(false).build();
        assertFalse(hideFriends.accepts(EntityCategory.PLAYER, false, true, false, true, 4, "buddy"));
        assertTrue(hideFriends.accepts(EntityCategory.PLAYER, false, false, false, true, 4, "stranger"));
    }

    @Test
    void livingOnlyRejectsNonLiving() {
        EntityFilter livingOnly = EntityFilter.builder().livingOnly(true).build();
        assertTrue(livingOnly.accepts(EntityCategory.HOSTILE, false, false, false, true, 4, "z"));
        assertFalse(livingOnly.accepts(EntityCategory.ITEM, false, false, false, false, 4, "diamond"));
    }

    @Test
    void nameFilterIsCaseInsensitiveSubstring() {
        EntityFilter named = EntityFilter.builder().nameContains("ste").build();
        assertTrue(named.accepts(EntityCategory.PLAYER, false, false, false, true, 4, "Steve"));
        assertFalse(named.accepts(EntityCategory.PLAYER, false, false, false, true, 4, "Alex"));
    }

    @Test
    void nullCategoryIsRejected() {
        EntityFilter filter = EntityFilter.builder().build();
        assertFalse(filter.accepts(null, false, false, false, true, 4, "?"));
    }

    @Test
    void nonPositiveRangeMeansUnbounded() {
        EntityFilter filter = EntityFilter.builder().range(0).build();
        assertTrue(filter.accepts(EntityCategory.PLAYER, false, false, false, true, 10000, "far"));
    }
}
