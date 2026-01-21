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
package io.github.urzeye.loomflow.integration;

import io.github.urzeye.loomflow.FlowContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 测试 SPI 自动上下文传递功能。
 * 验证无需手动调用 Bridge，仅依赖 FlowContext.wrap 即可传递 MDC。
 */
public class AutoSpiTest {

    @Test
    void testAutoMdcPropagation() throws ExecutionException, InterruptedException {
        // 1. 设置主线程 MDC
        String traceId = "auto-spi-test-id";
        MDC.put("traceId", traceId);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            // 2. 提交任务，仅使用 FlowContext.wrap，不使用 MdcBridge
            // 由于 MdcTransmitter 已注册，FlowContext.wrap 应自动捕获 MDC
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                // 子线程: 验证 MDC 是否被还原
                return MDC.get("traceId"); // 应该得到 "auto-spi-test-id"
            }, FlowContext.wrapExecutorService(executor)); // 或者使用 .supplyAsync(..., FlowContext.wrapExecutor(exec))

            // 3. 验证结果
            String result = future.get();
            Assertions.assertEquals(traceId, result, "MDC context should be automatically propagated via SPI");
        } finally {
            MDC.clear();
        }
    }
}
