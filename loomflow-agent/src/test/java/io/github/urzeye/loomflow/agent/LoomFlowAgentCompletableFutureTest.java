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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoomFlow Agent - CompletableFuture 测试")
class LoomFlowAgentCompletableFutureTest {

    static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

    @BeforeAll
    static void installAgent() {
        System.setProperty("loomflow.agent.bootstrap", "false"); // Dynamic attach limitation
        ByteBuddyAgent.install();
        LoomFlowAgent.premain(null, ByteBuddyAgent.getInstrumentation());
    }

    @Test
    @DisplayName("CompletableFuture.supplyAsync 自动传递上下文")
    @Disabled("CompletableFuture 位于 java.base，需要 Bootstrap ClassLoader 增强，动态 Attach 无法生效，需 -javaagent 运行")
    void testSupplyAsync() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        
        FlowContext.with(TRACE_ID, "cf-supply-test").run(() -> {
            CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
                captured.set(FlowContext.get(TRACE_ID));
                return null;
            });
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("cf-supply-test", captured.get());
    }

    @Test
    @DisplayName("CompletableFuture.runAsync 自动传递上下文")
    @Disabled("需要 Bootstrap ClassLoader 增强")
    void testRunAsync() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        
        FlowContext.with(TRACE_ID, "cf-run-test").run(() -> {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                captured.set(FlowContext.get(TRACE_ID));
            });
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertEquals("cf-run-test", captured.get());
    }
}
