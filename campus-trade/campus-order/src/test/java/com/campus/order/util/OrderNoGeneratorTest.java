package com.campus.order.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderNoGeneratorTest {

    @Test
    void generatesGloballySizedUniqueIdentifiers() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            String orderNo = OrderNoGenerator.generate();
            assertEquals(32, orderNo.length());
            values.add(orderNo);
        }
        assertEquals(10_000, values.size());
    }
}
