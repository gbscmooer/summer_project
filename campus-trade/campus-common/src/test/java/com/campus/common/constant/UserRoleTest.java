package com.campus.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleTest {

    @Test
    void canSendNotification_adminAndOfficialOnly() {
        assertTrue(UserRole.canSendNotification(UserRole.ADMIN));
        assertTrue(UserRole.canSendNotification(UserRole.OFFICIAL));
        assertFalse(UserRole.canSendNotification(UserRole.USER));
        assertFalse(UserRole.canSendNotification(UserRole.MERCHANT));
    }

    @Test
    void isOfficial() {
        assertTrue(UserRole.isOfficial(UserRole.OFFICIAL));
        assertFalse(UserRole.isOfficial(UserRole.ADMIN));
        assertFalse(UserRole.isOfficial(UserRole.USER));
    }

    @Test
    void canUpgradeRole_personalOnly_merchantAndOfficialMutuallyExclusive() {
        assertTrue(UserRole.canUpgradeRole(UserRole.USER));
        assertFalse(UserRole.canUpgradeRole(UserRole.ADMIN));
        assertFalse(UserRole.canUpgradeRole(UserRole.MERCHANT));
        assertFalse(UserRole.canUpgradeRole(UserRole.OFFICIAL));
    }
}
