package com.logvex.crystal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers the API compatibility rule that gates whether an extension may be hosted. */
class ApiVersionTest {

    @Test
    void exactVersionIsCompatible() {
        assertTrue(ApiVersion.isCompatible(1, 0, 1, 0));
        assertTrue(ApiVersion.supports(ApiVersion.MAJOR, ApiVersion.MINOR));
    }

    @Test
    void aNewerHostSatisfiesAnOlderRequirement() {
        // Additive changes never remove a symbol, so a host minor above the requirement is fine.
        assertTrue(ApiVersion.isCompatible(1, 3, 1, 0));
        assertTrue(ApiVersion.isCompatible(1, 3, 1, 3));
    }

    @Test
    void anOlderHostCannotSatisfyANewerRequirement() {
        assertFalse(ApiVersion.isCompatible(1, 0, 1, 1));
    }

    @Test
    void aDifferentMajorIsNeverCompatible() {
        assertFalse(ApiVersion.isCompatible(2, 5, 1, 0));
        assertFalse(ApiVersion.isCompatible(0, 9, 1, 0));
    }

    @Test
    void stringIsMajorDotMinor() {
        assertEquals(ApiVersion.MAJOR + "." + ApiVersion.MINOR, ApiVersion.string());
    }
}
