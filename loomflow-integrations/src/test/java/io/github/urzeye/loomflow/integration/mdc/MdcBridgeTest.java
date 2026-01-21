/*
 * Copyright 2026 urzeye
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.urzeye.loomflow.integration.mdc;

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class MdcBridgeTest {

    @Test
    void testMdcBridgePut() {
        // 在测试方法内创建 key，确保实例一致
        ContextKey<String> traceId = ContextKey.of("traceId");
        
        FlowContext.with(traceId, "test-trace").run(() -> {
            // 验证 FlowContext 能正常获取值
            String contextValue = FlowContext.get(traceId);
            assertEquals("test-trace", contextValue, "FlowContext should have value");
            
            // 验证 getOrNull 能获取值
            String orNullValue = FlowContext.getOrNull(traceId);
            assertEquals("test-trace", orNullValue, "getOrNull should return value");
            
            // 同步到 MDC
            MdcBridge.put(traceId);
            assertEquals("test-trace", MDC.get("traceId"), "MDC should have value");
        });
    }

    @Test
    void testWrapWithMdc() throws InterruptedException {
        MDC.put("userId", "user-456");
        
        Runnable wrapped = MdcBridge.wrapWithMdc(() -> {
            assertEquals("user-456", MDC.get("userId"));
        });
        
        MDC.clear();
        
        Thread thread = new Thread(wrapped);
        thread.start();
        thread.join();
    }
}
