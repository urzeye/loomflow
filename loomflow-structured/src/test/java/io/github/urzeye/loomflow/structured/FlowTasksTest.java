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
package io.github.urzeye.loomflow.structured;

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 结构化并发测试。
 */
@DisplayName("结构化并发测试")
class FlowTasksTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

    @Test
    @DisplayName("invokeAll - 所有任务继承上下文")
    void testInvokeAllInheritsContext() throws Exception {
        FlowContext.with(TRACE_ID, "structured-test").run(() -> {
            try {
                List<String> results = FlowTasks.invokeAll(
                        () -> FlowContext.get(TRACE_ID),
                        () -> FlowContext.get(TRACE_ID),
                        () -> FlowContext.get(TRACE_ID)
                );
                
                assertEquals(3, results.size());
                assertTrue(results.stream().allMatch("structured-test"::equals));
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("invokeAny - 第一个成功的任务继承上下文")
    void testInvokeAnyInheritsContext() throws Exception {
        FlowContext.with(TRACE_ID, "any-test").run(() -> {
            try {
                String result = FlowTasks.invokeAny(
                        () -> {
                            Thread.sleep(10);
                            return FlowContext.get(TRACE_ID);
                        },
                        () -> FlowContext.get(TRACE_ID)  // 这个应该更快返回
                );
                
                assertEquals("any-test", result);
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("FlowTaskScope.fork - 子任务自动继承上下文")
    void testFlowTaskScopeFork() throws Exception {
        FlowContext.with(TRACE_ID, "scope-test").run(() -> {
            // 使用 shutdownOnFailure() 工厂方法，兼容 JDK 21 和 JDK 25
            try (var scope = FlowTaskScope.shutdownOnFailure()) {
                var subtask = scope.fork(() -> FlowContext.get(TRACE_ID));
                
                scope.join();
                scope.throwIfFailed();
                
                assertEquals("scope-test", subtask.get());
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("ShutdownOnFailure - 失败时取消其他任务")
    void testShutdownOnFailure() {
        FlowContext.with(TRACE_ID, "failure-test").run(() -> {
            try (var scope = FlowTaskScope.<String>shutdownOnFailure()) {
                scope.fork(() -> FlowContext.get(TRACE_ID));
                scope.fork(() -> {
                    Thread.sleep(100);
                    return FlowContext.get(TRACE_ID);
                });
                
                scope.join();
                scope.throwIfFailed();
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    @Test
    @DisplayName("ShutdownOnSuccess - 获取第一个成功结果")
    void testShutdownOnSuccess() {
        FlowContext.with(TRACE_ID, "success-test").run(() -> {
            try (var scope = FlowTaskScope.<String>shutdownOnSuccess()) {
                scope.fork(() -> {
                    Thread.sleep(100); // 慢任务
                    return "slow";
                });
                scope.fork(() -> FlowContext.get(TRACE_ID)); // 快任务，应该先返回
                
                scope.join();
                String result = scope.result();
                
                // 验证返回了第一个成功的结果（快任务）
                assertEquals("success-test", result);
            } catch (Exception e) {
                fail(e);
            }
        });
    }
}
