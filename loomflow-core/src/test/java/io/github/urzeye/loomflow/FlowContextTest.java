/*
 * Copyright 2026 urzeye
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.urzeye.loomflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FlowContext 核心功能测试。
 */
@DisplayName("FlowContext 测试")
class FlowContextTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
    static final ContextKey<Integer> USER_ID = ContextKey.of("userId");
    static final ContextKey<String> WITH_DEFAULT = ContextKey.of("withDefault", "default-value");

    @Test
    @DisplayName("基本上下文绑定和读取")
    void testBasicBinding() {
        FlowContext.with(TRACE_ID, "test-trace-123").run(() -> {
            assertEquals("test-trace-123", FlowContext.get(TRACE_ID));
            assertTrue(FlowContext.isBound(TRACE_ID));
        });
    }

    @Test
    @DisplayName("多值绑定")
    void testMultipleBindings() {
        FlowContext.with(TRACE_ID, "trace-abc")
                .and(USER_ID, 42)
                .run(() -> {
                    assertEquals("trace-abc", FlowContext.get(TRACE_ID));
                    assertEquals(42, FlowContext.get(USER_ID));
                });
    }

    @Test
    @DisplayName("默认值")
    void testDefaultValue() {
        // 未绑定时使用默认值
        assertEquals("default-value", FlowContext.getOrDefault(WITH_DEFAULT, "fallback"));
        
        // 绑定后使用绑定值
        FlowContext.with(WITH_DEFAULT, "custom").run(() -> {
            assertEquals("custom", FlowContext.get(WITH_DEFAULT));
        });
    }

    @Test
    @DisplayName("未绑定且无默认值时抛出异常")
    void testUnboundThrows() {
        assertFalse(FlowContext.isBound(TRACE_ID));
        assertThrows(IllegalStateException.class, () -> FlowContext.get(TRACE_ID));
    }

    @Test
    @DisplayName("getOrDefault 返回指定默认值")
    void testGetOrDefault() {
        String result = FlowContext.getOrDefault(TRACE_ID, "fallback");
        assertEquals("fallback", result);
    }

    @Test
    @DisplayName("嵌套作用域覆盖")
    void testNestedScopes() {
        FlowContext.with(TRACE_ID, "outer").run(() -> {
            assertEquals("outer", FlowContext.get(TRACE_ID));
            
            FlowContext.with(TRACE_ID, "inner").run(() -> {
                assertEquals("inner", FlowContext.get(TRACE_ID));
            });
            
            // 退出内层后恢复外层值
            assertEquals("outer", FlowContext.get(TRACE_ID));
        });
    }

    @Test
    @DisplayName("call 方法返回值")
    void testCallWithReturn() throws Exception {
        String result = FlowContext.with(TRACE_ID, "for-call")
                .call(() -> "Result: " + FlowContext.get(TRACE_ID));
        
        assertEquals("Result: for-call", result);
    }

    @Test
    @DisplayName("callUnchecked 包装异常")
    void testCallUnchecked() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            FlowContext.with(TRACE_ID, "test")
                    .callUnchecked(() -> {
                        throw new Exception("test exception");
                    });
        });
        assertEquals("test exception", ex.getCause().getMessage());
    }

    @Test
    @DisplayName("wrap Runnable 传递上下文")
    void testWrapRunnable() throws InterruptedException {
        AtomicReference<String> captured = new AtomicReference<>();
        AtomicReference<Thread> threadRef = new AtomicReference<>();
        
        FlowContext.with(TRACE_ID, "wrapped-value").run(() -> {
            Runnable wrapped = FlowContext.wrap(() -> {
                captured.set(FlowContext.get(TRACE_ID));
            });
            
            // 在新线程中执行
            Thread thread = new Thread(wrapped);
            thread.start();
            threadRef.set(thread);
        });
        
        threadRef.get().join();
        assertEquals("wrapped-value", captured.get());
    }
}
