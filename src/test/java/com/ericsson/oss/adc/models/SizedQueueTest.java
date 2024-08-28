package com.ericsson.oss.adc.models;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SizedQueueTest {
    SizedQueue<String> sizedQueue;
    int limit;
    @BeforeEach
    public void init(){
        final int ropPeriodMinutes = 15;
        final int retentionPeriodMinutes = 60;
        sizedQueue = new SizedQueue<>(ropPeriodMinutes, retentionPeriodMinutes);
        limit = sizedQueue.getLimit();
    }

    @Test
    public void test_addWithRemove_DoesntExceedLimit() {
        for (int i = 0; i < 20; i++) {
            sizedQueue.addWithRemove("test_string");
        }
        assertEquals(limit, sizedQueue.size());
    }

    @Test
    public void test_addWithRemove_ReturnsRemovedElementOrNull() {
        for (int i = 0; i < 4; i++) {
            assertNull(sizedQueue.addWithRemove("test_string"));
        }
        assertEquals("test_string", sizedQueue.addWithRemove("test_string"));
    }

}
