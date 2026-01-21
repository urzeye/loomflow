/*
 * Copyright 2026 urzeye
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.urzeye.loomflow.integration.otel;

import io.opentelemetry.context.Context;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class OtelBridgeTest {

    @Test
    void testWrap() throws InterruptedException {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        Runnable wrapped = OtelBridge.wrap(() -> {
            executed.set(true);
        });
        
        Thread thread = new Thread(wrapped);
        thread.start();
        thread.join();
        
        assertTrue(executed.get());
    }

    @Test
    void testWrapBoth() throws InterruptedException {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        Runnable wrapped = OtelBridge.wrapBoth(() -> {
            executed.set(true);
        });
        
        Thread thread = new Thread(wrapped);
        thread.start();
        thread.join();
        
        assertTrue(executed.get());
    }

    @Test
    void testGetTraceIdWithoutSpan() {
        // 没有活跃 Span 时返回 null
        String traceId = OtelBridge.getTraceId();
        assertNull(traceId);
    }
}
