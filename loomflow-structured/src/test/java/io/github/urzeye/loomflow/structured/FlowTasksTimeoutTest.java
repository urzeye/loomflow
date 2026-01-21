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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowTasks 超时控制测试")
class FlowTasksTimeoutTest {

    @Test
    @DisplayName("invokeAll: 任务在超时前完成")
    void testInvokeAllWithinTimeout() throws Exception {
        List<String> results = FlowTasks.invokeAll(
                Duration.ofSeconds(5),
                () -> {
                    Thread.sleep(100);
                    return "A";
                },
                () -> {
                    Thread.sleep(100);
                    return "B";
                }
        );
        
        assertEquals(2, results.size());
        assertTrue(results.contains("A"));
        assertTrue(results.contains("B"));
    }

    @Test
    @DisplayName("invokeAll: 超时抛出 TimeoutException")
    void testInvokeAllTimeout() {
        assertThrows(TimeoutException.class, () -> {
            FlowTasks.invokeAll(
                    Duration.ofMillis(100),
                    () -> {
                        Thread.sleep(5000); // 长时间任务
                        return "slow";
                    }
            );
        });
    }

    @Test
    @DisplayName("invokeAny: 任务在超时前成功")
    void testInvokeAnyWithinTimeout() throws Exception {
        String result = FlowTasks.invokeAny(
                Duration.ofSeconds(5),
                () -> {
                    Thread.sleep(100);
                    return "fast";
                },
                () -> {
                    Thread.sleep(2000);
                    return "slow";
                }
        );
        
        assertEquals("fast", result);
    }

    @Test
    @DisplayName("invokeAny: 超时抛出 TimeoutException")
    void testInvokeAnyTimeout() {
        assertThrows(TimeoutException.class, () -> {
            FlowTasks.invokeAny(
                    Duration.ofMillis(100),
                    () -> {
                        Thread.sleep(5000);
                        return "slow1";
                    },
                    () -> {
                        Thread.sleep(5000);
                        return "slow2";
                    }
            );
        });
    }

    @Test
    @DisplayName("FlowTaskScope.join(Duration): 超时控制")
    void testFlowTaskScopeJoinTimeout() {
        assertThrows(TimeoutException.class, () -> {
            try (var scope = FlowTaskScope.shutdownOnFailure()) {
                scope.fork(() -> {
                    Thread.sleep(5000);
                    return "slow";
                });
                
                scope.join(Duration.ofMillis(100)); // 超时
            }
        });
    }

    @Test
    @DisplayName("FlowTaskScope.join(Duration): 任务在超时前完成")
    void testFlowTaskScopeJoinWithinTimeout() throws Exception {
        try (var scope = FlowTaskScope.shutdownOnFailure()) {
            var subtask = scope.fork(() -> {
                Thread.sleep(50);
                return "done";
            });
            
            scope.join(Duration.ofSeconds(5));
            
            assertEquals("done", subtask.get());
        }
    }
}
