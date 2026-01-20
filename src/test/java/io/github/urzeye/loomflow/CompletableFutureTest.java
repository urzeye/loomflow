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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompletableFuture 上下文传递测试。
 */
@DisplayName("CompletableFuture 上下文传递测试")
class CompletableFutureTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

    @Test
    @DisplayName("supplyAsync 传递上下文")
    void testSupplyAsync() throws Exception {
        String result = FlowContext.with(TRACE_ID, "async-trace")
                .callUnchecked(() -> {
                    CompletableFuture<String> future = FlowContext.supplyAsync(() ->
                        "Trace: " + FlowContext.get(TRACE_ID)
                    );
                    return future.get(5, TimeUnit.SECONDS);
                });

        assertEquals("Trace: async-trace", result);
    }

    @Test
    @DisplayName("runAsync 传递上下文")
    void testRunAsync() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> captured = 
            new java.util.concurrent.atomic.AtomicReference<>();

        FlowContext.with(TRACE_ID, "run-async-trace").run(() -> {
            try {
                CompletableFuture<Void> future = FlowContext.runAsync(() -> {
                    captured.set(FlowContext.get(TRACE_ID));
                });
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail(e);
            }
        });

        assertEquals("run-async-trace", captured.get());
    }

    @Test
    @DisplayName("链式调用传递上下文")
    void testChainedCalls() throws Exception {
        String result = FlowContext.with(TRACE_ID, "chained")
                .callUnchecked(() -> {
                    CompletableFuture<String> future = FlowContext.supplyAsync(() ->
                        FlowContext.get(TRACE_ID)
                    ).thenApply(id -> {
                        // 注意：thenApply 默认在完成线程执行，可能丢失上下文
                        // 如需保证，应使用 thenApplyAsync + wrapped executor
                        return "Processed: " + id;
                    });
                    return future.get(5, TimeUnit.SECONDS);
                });

        assertEquals("Processed: chained", result);
    }

    @Test
    @DisplayName("多个 CompletableFuture 并行执行")
    void testParallelFutures() throws Exception {
        FlowContext.with(TRACE_ID, "parallel-test").run(() -> {
            try {
                CompletableFuture<String> f1 = FlowContext.supplyAsync(() ->
                    "F1:" + FlowContext.get(TRACE_ID)
                );
                CompletableFuture<String> f2 = FlowContext.supplyAsync(() ->
                    "F2:" + FlowContext.get(TRACE_ID)
                );
                CompletableFuture<String> f3 = FlowContext.supplyAsync(() ->
                    "F3:" + FlowContext.get(TRACE_ID)
                );

                CompletableFuture.allOf(f1, f2, f3).get(5, TimeUnit.SECONDS);

                assertEquals("F1:parallel-test", f1.get());
                assertEquals("F2:parallel-test", f2.get());
                assertEquals("F3:parallel-test", f3.get());
            } catch (Exception e) {
                fail(e);
            }
        });
    }
}
