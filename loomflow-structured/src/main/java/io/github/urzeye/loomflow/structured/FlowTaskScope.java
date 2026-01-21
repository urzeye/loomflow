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

import java.util.concurrent.StructuredTaskScope;

/**
 * 结构化并发任务作用域。
 * <p>
 * 扩展 {@link StructuredTaskScope}，利用 ScopedValue 的天然继承特性，
 * 使 fork 的子任务自动继承父任务的上下文。
 * </p>
 *
 * <h2>核心优势</h2>
 * <p>
 * 在结构化并发中，{@link java.lang.ScopedValue} 天然被子任务继承，
 * <b>无需任何包装代码</b>，这是 JDK 设计的行为。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
 *
 * FlowContext.with(TRACE_ID, "abc-123").run(() -> {
 *     try (var scope = new FlowTaskScope<String>()) {
 *         Subtask<String> task1 = scope.fork(() -> {
 *             // 自动继承 TRACE_ID
 *             return FlowContext.get(TRACE_ID);
 *         });
 *         
 *         Subtask<String> task2 = scope.fork(() -> {
 *             // 同样可以访问 TRACE_ID
 *             return doSomething();
 *         });
 *         
 *         scope.join();
 *         
 *         String result1 = task1.get();
 *         String result2 = task2.get();
 *     }
 * });
 * }</pre>
 *
 * @param <T> 子任务的返回类型
 * @author urzeye
 * @since 0.2.0
 */
public class FlowTaskScope<T> extends StructuredTaskScope<T> {

    /**
     * 使用默认配置创建任务作用域
     */
    public FlowTaskScope() {
        super();
    }

    /**
     * 使用指定名称创建任务作用域
     *
     * @param name 作用域名称，用于调试
     */
    public FlowTaskScope(String name) {
        super(name, Thread.ofVirtual().factory());
    }

    /**
     * 创建一个"快速失败"作用域。
     * <p>
     * 任何子任务失败后，其他子任务将被取消。
     * </p>
     *
     * @param <T> 子任务返回类型
     * @return ShutdownOnFailure 作用域
     */
    public static <T> StructuredTaskScope.ShutdownOnFailure shutdownOnFailure() {
        return new StructuredTaskScope.ShutdownOnFailure();
    }

    /**
     * 创建一个"快速成功"作用域。
     * <p>
     * 任何子任务成功后，其他子任务将被取消。
     * </p>
     *
     * @param <T> 子任务返回类型
     * @return ShutdownOnSuccess 作用域
     */
    public static <T> StructuredTaskScope.ShutdownOnSuccess<T> shutdownOnSuccess() {
        return new StructuredTaskScope.ShutdownOnSuccess<>();
    }
}
