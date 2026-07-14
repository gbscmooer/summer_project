package com.campus.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCapabilityTest {

    @Test
    void nullOrNonZeroMeansAllowed() {
        assertTrue(UserCapability.isAllowed(null));
        assertTrue(UserCapability.isAllowed(1));
        assertFalse(UserCapability.isAllowed(0));
    }

    @Test
    void toFlagMapsBoolean() {
        assertTrue(UserCapability.isAllowed(UserCapability.toFlag(true)));
        assertFalse(UserCapability.isAllowed(UserCapability.toFlag(false)));
    }
}
