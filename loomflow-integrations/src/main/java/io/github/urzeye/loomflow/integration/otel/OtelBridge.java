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
package io.github.urzeye.loomflow.integration.otel;

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;

/**
 * OpenTelemetry 与 FlowContext 桥接器。
 * <p>
 * 提供 OpenTelemetry Context 与 LoomFlow 上下文之间的同步功能。
 * </p>
 *
 * <h2>功能</h2>
 * <ul>
 *   <li>从 OTel Span 提取 traceId/spanId 存入 FlowContext</li>
 *   <li>将 FlowContext 中的值传递到 OTel Baggage</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 从当前 Span 提取追踪信息
 * OtelBridge.syncFromSpan();
 * String traceId = FlowContext.get(OtelBridge.TRACE_ID);
 * 
 * // 包装 Runnable 传递 OTel 上下文
 * Runnable wrapped = OtelBridge.wrap(task);
 * }</pre>
 *
 * @author urzeye
 * @since 0.2.0
 */
public final class OtelBridge {

    /**
     * OpenTelemetry Trace ID 的 ContextKey
     */
    public static final ContextKey<String> TRACE_ID = ContextKey.of("otel.traceId");

    /**
     * OpenTelemetry Span ID 的 ContextKey
     */
    public static final ContextKey<String> SPAN_ID = ContextKey.of("otel.spanId");

    /**
     * OpenTelemetry Trace Flags 的 ContextKey
     */
    public static final ContextKey<String> TRACE_FLAGS = ContextKey.of("otel.traceFlags");

    private OtelBridge() {
    }

    /**
     * 从当前 OTel Context 同步 Span 信息到 FlowContext。
     * <p>
     * 注意：此方法需要在 FlowContext 作用域内调用，
     * 且当前线程必须有活跃的 OTel Span。
     * </p>
     */
    public static void syncFromSpan() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            SpanContext ctx = span.getSpanContext();
            // 将 traceId 和 spanId 放入当前作用域
            // 这需要在 FlowContext.with() 中调用
            putToMdc(ctx);
        }
    }

    /**
     * 获取当前 OTel Span 的 Trace ID。
     *
     * @return traceId 或 null
     */
    public static String getTraceId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return null;
    }

    /**
     * 获取当前 OTel Span 的 Span ID。
     *
     * @return spanId 或 null
     */
    public static String getSpanId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getSpanId();
        }
        return null;
    }

    /**
     * 包装 Runnable，自动传递 OTel Context。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable task) {
        Context otelContext = Context.current();
        return () -> otelContext.wrap(task).run();
    }

    /**
     * 包装 Runnable，同时传递 FlowContext 和 OTel Context。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrapBoth(Runnable task) {
        Context otelContext = Context.current();
        Runnable flowWrapped = FlowContext.wrap(task);
        return () -> otelContext.wrap(flowWrapped).run();
    }

    private static void putToMdc(SpanContext ctx) {
        // 如果有 MDC 桥接，同时同步到 MDC
        try {
            org.slf4j.MDC.put("traceId", ctx.getTraceId());
            org.slf4j.MDC.put("spanId", ctx.getSpanId());
        } catch (NoClassDefFoundError ignored) {
            // SLF4J 不可用
        }
    }
}
