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
package io.github.urzeye.loomflow.integration.mdc;

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;
import org.slf4j.MDC;

import java.util.Map;

/**
 * MDC 与 FlowContext 桥接器。
 * <p>
 * 提供 SLF4J MDC 与 LoomFlow 上下文之间的同步功能。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
 *
 * FlowContext.with(TRACE_ID, "abc-123").run(() -> {
 *     MdcBridge.put(TRACE_ID);  // 同步到 MDC
 *     logger.info("Processing request");  // 日志会包含 traceId
 * });
 * }</pre>
 *
 * @author urzeye
 * @since 0.2.0
 */
public final class MdcBridge {

    private MdcBridge() {
    }

    /**
     * 将指定 ContextKey 的值同步到 MDC。
     *
     * @param key ContextKey（使用 key name 作为 MDC key）
     * @param <T> 值类型
     */
    public static <T> void put(ContextKey<T> key) {
        put(key, key.name());
    }

    /**
     * 将指定 ContextKey 的值同步到 MDC。
     *
     * @param key    ContextKey
     * @param mdcKey MDC 中的 key 名称
     * @param <T>    值类型
     */
    public static <T> void put(ContextKey<T> key, String mdcKey) {
        T value = FlowContext.getOrNull(key);
        if (value != null) {
            MDC.put(mdcKey, String.valueOf(value));
        }
    }

    /**
     * 从 MDC 中移除指定 key。
     *
     * @param mdcKey MDC key 名称
     */
    public static void remove(String mdcKey) {
        MDC.remove(mdcKey);
    }

    /**
     * 清除所有 MDC 内容。
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 包装 Runnable，自动传递 MDC 上下文到新线程。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrapWithMdc(Runnable task) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        
        return () -> {
            if (mdcContext != null) {
                MDC.setContextMap(mdcContext);
            }
            try {
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
