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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoomFlow Agent - ForkJoinPool 测试")
class LoomFlowAgentForkJoinPoolTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

    @BeforeAll
    static void installAgent() {
        System.setProperty("loomflow.agent.bootstrap", "false");
        ByteBuddyAgent.install();
        LoomFlowAgent.premain(null, ByteBuddyAgent.getInstrumentation());
    }

    @Test
    @DisplayName("ForkJoinPool.submit 自动传递上下文")
    @Disabled("ForkJoinPool 位于 java.base，需要 Bootstrap ClassLoader 增强")
    void testForkJoinPoolSubmit() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(2);
        AtomicReference<String> captured = new AtomicReference<>();
        
        try {
            FlowContext.with(TRACE_ID, "fjp-test").run(() -> {
                try {
                    pool.submit(() -> {
                        captured.set(FlowContext.get(TRACE_ID));
                    }).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
            assertEquals("fjp-test", captured.get());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("Parallel Stream 自动传递上下文")
    @Disabled("Parallel Stream 使用 Common ForkJoinPool，需要 Bootstrap 增强")
    void testParallelStream() throws Exception {
        FlowContext.with(TRACE_ID, "stream-test").run(() -> {
            boolean allMatch = IntStream.range(0, 10)
                    .parallel()
                    .mapToObj(i -> FlowContext.get(TRACE_ID))
                    .allMatch("stream-test"::equals);
            
            assertTrue(allMatch);
        });
    }
}
