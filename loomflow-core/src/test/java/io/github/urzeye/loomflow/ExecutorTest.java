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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线程池上下文传递测试。
 */
@DisplayName("ExecutorService 上下文传递测试")
class ExecutorTest {

    static final ContextKey<String> REQUEST_ID = ContextKey.of("requestId");

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    @DisplayName("wrapExecutorService 自动传递上下文")
    void testWrapExecutorService() throws Exception {
        ExecutorService wrapped = FlowContext.wrapExecutorService(executor);
        AtomicReference<String> captured = new AtomicReference<>();

        FlowContext.with(REQUEST_ID, "req-12345").run(() -> {
            try {
                Future<?> future = wrapped.submit(() -> {
                    captured.set(FlowContext.get(REQUEST_ID));
                });
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                fail(e);
            }
        });

        assertEquals("req-12345", captured.get());
    }

    @Test
    @DisplayName("submit Callable 传递上下文")
    void testSubmitCallable() throws Exception {
        ExecutorService wrapped = FlowContext.wrapExecutorService(executor);

        String result = FlowContext.with(REQUEST_ID, "callable-test")
                .callUnchecked(() -> {
                    Future<String> future = wrapped.submit(() -> 
                        "Got: " + FlowContext.get(REQUEST_ID)
                    );
                    return future.get(5, TimeUnit.SECONDS);
                });

        assertEquals("Got: callable-test", result);
    }

    @Test
    @DisplayName("多任务并发保持各自上下文")
    void testConcurrentTasks() throws Exception {
        ExecutorService wrapped = FlowContext.wrapExecutorService(executor);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final String requestId = "req-" + i;
            FlowContext.with(REQUEST_ID, requestId).run(() -> {
                Future<String> future = wrapped.submit(() -> 
                    FlowContext.get(REQUEST_ID)
                );
                futures.add(future);
            });
        }

        for (int i = 0; i < 10; i++) {
            assertEquals("req-" + i, futures.get(i).get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("虚拟线程执行器传递上下文")
    void testVirtualThreadExecutor() throws Exception {
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ExecutorService wrapped = FlowContext.wrapExecutorService(virtualExecutor);

        try {
            AtomicReference<String> captured = new AtomicReference<>();

            FlowContext.with(REQUEST_ID, "virtual-thread-test").run(() -> {
                try {
                    Future<?> future = wrapped.submit(() -> {
                        captured.set(FlowContext.get(REQUEST_ID));
                    });
                    future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail(e);
                }
            });

            assertEquals("virtual-thread-test", captured.get());
        } finally {
            virtualExecutor.shutdown();
        }
    }
}
