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
package io.github.urzeye.loomflow.agent;

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoomFlowAgent 集成测试。
 * <p>
 * 测试应用类增强能力（不依赖 Bootstrap ClassLoader）。
 * </p>
 */
@DisplayName("LoomFlow Agent 测试")
class LoomFlowAgentTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

    @BeforeAll
    static void installAgent() {
        // 禁用 Bootstrap 增强（测试环境）
        System.setProperty("loomflow.agent.bootstrap", "false");
        // 动态安装 Agent
        ByteBuddyAgent.install();
        LoomFlowAgent.premain(null, ByteBuddyAgent.getInstrumentation());
    }

    /**
     * 自定义 Executor 实现，用于测试应用类增强
     */
    static class CustomExecutor implements Executor {
        private final ExecutorService delegate = Executors.newSingleThreadExecutor();
        
        @Override
        public void execute(Runnable command) {
            delegate.execute(command);
        }
        
        public void shutdown() {
            delegate.shutdown();
        }
    }

    @Test
    @DisplayName("自定义 Executor 透明传递上下文（需要 JVM 启动时加载 Agent）")
    @org.junit.jupiter.api.Disabled("动态安装的 Agent 无法转换已加载的类，需要 -javaagent 方式测试")
    void testCustomExecutor() throws Exception {
        CustomExecutor executor = new CustomExecutor();
        AtomicReference<String> captured = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            FlowContext.with(TRACE_ID, "custom-executor-test").run(() -> {
                // Agent 应该自动包装，无需手动调用 FlowContext.wrap()
                executor.execute(() -> {
                    captured.set(FlowContext.get(TRACE_ID));
                    latch.countDown();
                });
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals("custom-executor-test", captured.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("手动包装仍然有效")
    void testManualWrap() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<String> captured = new AtomicReference<>();

        try {
            FlowContext.with(TRACE_ID, "manual-wrap-test").run(() -> {
                try {
                    // 手动包装方式
                    Future<?> future = executor.submit(FlowContext.wrap(() -> {
                        captured.set(FlowContext.get(TRACE_ID));
                    }));
                    future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail(e);
                }
            });

            assertEquals("manual-wrap-test", captured.get());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("ContextAwareExecutor 包装器有效")
    void testContextAwareExecutor() throws Exception {
        ExecutorService rawExecutor = Executors.newFixedThreadPool(2);
        var executor = new io.github.urzeye.loomflow.executor.ContextAwareExecutor.Service(rawExecutor);
        AtomicReference<String> captured = new AtomicReference<>();

        try {
            FlowContext.with(TRACE_ID, "aware-executor-test").run(() -> {
                try {
                    Future<?> future = executor.submit(() -> {
                        captured.set(FlowContext.get(TRACE_ID));
                    });
                    future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail(e);
                }
            });

            assertEquals("aware-executor-test", captured.get());
        } finally {
            executor.shutdown();
        }
    }
}
