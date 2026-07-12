package com.campus.user.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpMethodContractTest {

    @Test
    void userControllersUseOnlyGetAndPostMappings() {
        assertNoForbiddenMappings(UserController.class);
        assertNoForbiddenMappings(MerchantController.class);
        assertNoForbiddenMappings(AdminMerchantController.class);
        assertNoForbiddenMappings(ProfileController.class);
        assertNoForbiddenMappings(FollowController.class);
    }

    private void assertNoForbiddenMappings(Class<?> controllerClass) {
        for (Method method : controllerClass.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(PutMapping.class), method + " must not use PUT");
            assertFalse(method.isAnnotationPresent(DeleteMapping.class), method + " must not use DELETE");
            assertFalse(method.isAnnotationPresent(PatchMapping.class), method + " must not use PATCH");
        }
    }
}
