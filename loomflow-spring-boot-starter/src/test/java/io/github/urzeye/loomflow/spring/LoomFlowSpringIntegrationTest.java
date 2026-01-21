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
package io.github.urzeye.loomflow.spring;

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = LoomFlowSpringIntegrationTest.TestConfig.class)
@DisplayName("LoomFlow Spring 集成测试")
class LoomFlowSpringIntegrationTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

    @Autowired
    private AsyncService asyncService;

    @Autowired
    private Executor myExecutor;

    @Test
    @DisplayName("@Async 方法自动传递上下文")
    void testAsyncAnnotation() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        
        FlowContext.with(TRACE_ID, "async-test-123").run(() -> {
            try {
                // 调用异步方法
                CompletableFuture<String> future = asyncService.doAsync();
                captured.set(future.get(5, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("async-test-123", captured.get());
    }

    @Test
    @DisplayName("TaskExecutor Bean 自动包装且传递上下文")
    void testExecutorBean() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        FlowContext.with(TRACE_ID, "executor-bean-test").run(() -> {
            // TaskExecutor Bean 已经被包装为 ContextAwareTaskExecutor
            // 直接调用 execute 方法，并在其中捕获上下文
            myExecutor.execute(() -> {
                captured.set(FlowContext.get(TRACE_ID));
                latch.countDown();
            });
        });

        // 等待任务执行
        latch.await(5, TimeUnit.SECONDS);

        assertEquals("executor-bean-test", captured.get());
        
        // 额外断言：验证确实是包装类
        assertThat(myExecutor.getClass().getSimpleName()).contains("ContextAwareTaskExecutor");
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableAsync
    static class TestConfig {
        
        @Bean
        public AsyncService asyncService() {
            return new AsyncService();
        }

        @Bean(name = "myExecutor")
        public Executor myExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2);
            executor.initialize();
            return executor;
        }
    }

    static class AsyncService {
        @Async("myExecutor")
        public CompletableFuture<String> doAsync() {
            return CompletableFuture.completedFuture(FlowContext.get(TRACE_ID));
        }
    }
}
