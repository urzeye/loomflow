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
package io.github.urzeye.loomflow;

import io.github.urzeye.loomflow.executor.ContextAwareExecutor;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * LoomFlow 核心上下文管理器。
 * <p>
 * 提供声明式的上下文作用域管理，基于 JDK 25 的 {@link ScopedValue} 实现。
 * 在虚拟线程和传统线程池场景下，都能安全高效地传递上下文。
 * </p>
 *
 * <h2>基础用法</h2>
 * <pre>{@code
 * // 定义上下文键
 * static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
 * static final ContextKey<User> USER = ContextKey.of("user");
 *
 * // 在作用域内运行代码
 * FlowContext.with(TRACE_ID, "abc-123")
 *     .and(USER, currentUser)
 *     .run(() -> {
 *         String traceId = FlowContext.get(TRACE_ID);
 *         processRequest();
 *     });
 *
 * // 带返回值
 * String result = FlowContext.with(TRACE_ID, "abc-123")
 *     .call(() -> computeResult());
 * }</pre>
 *
 * <h2>线程池场景</h2>
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 *
 * // 方式一：装饰整个线程池
 * ExecutorService contextAware = FlowContext.wrapExecutor(executor);
 *
 * // 方式二：单次包装任务
 * executor.submit(FlowContext.wrap(() -> doWork()));
 * }</pre>
 *
 * @author urzeye
 * @since 0.1.0
 */
public final class FlowContext {

    private FlowContext() {
        // 工具类，禁止实例化
    }

    // ==================== 上下文读取 ====================

    /**
     * 获取当前上下文中指定键的值。
     *
     * @param key 上下文键
     * @param <T> 值类型
     * @return 上下文值
     * @throws IllegalStateException 如果键未绑定且没有默认值
     */
    public static <T> T get(ContextKey<T> key) {
        Objects.requireNonNull(key, "key must not be null");
        ScopedValue<T> sv = key.scopedValue();
        if (sv.isBound()) {
            return sv.get();
        }
        return key.defaultValue()
                .orElseThrow(() -> new IllegalStateException(
                        "ContextKey '" + key.name() + "' is not bound and has no default value"));
    }

    /**
     * 获取当前上下文中指定键的值，如果未绑定则返回指定的默认值。
     *
     * @param key          上下文键
     * @param defaultValue 未绑定时的默认值
     * @param <T>          值类型
     * @return 上下文值或默认值
     */
    public static <T> T getOrDefault(ContextKey<T> key, T defaultValue) {
        Objects.requireNonNull(key, "key must not be null");
        ScopedValue<T> sv = key.scopedValue();
        if (sv.isBound()) {
            return sv.get();
        }
        return key.defaultValue().orElse(defaultValue);
    }

    /**
     * 检查指定键是否在当前上下文中绑定。
     *
     * @param key 上下文键
     * @return 如果已绑定返回 true
     */
    public static boolean isBound(ContextKey<?> key) {
        Objects.requireNonNull(key, "key must not be null");
        return key.scopedValue().isBound();
    }

    // ==================== 上下文绑定 ====================

    /**
     * 开始创建一个新的上下文作用域。
     *
     * @param key   上下文键
     * @param value 要绑定的值
     * @param <T>   值类型
     * @return 作用域构建器
     */
    public static <T> FlowScope with(ContextKey<T> key, T value) {
        return new FlowScope().and(key, value);
    }

    // ==================== 任务包装 ====================

    /**
     * 包装 Runnable，使其在执行时继承当前上下文。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        ContextCarrier carrier = ContextCarrier.capture();
        return () -> carrier.restore(task);
    }

    /**
     * 包装 Callable，使其在执行时继承当前上下文。
     *
     * @param task 原始任务
     * @param <T>  返回值类型
     * @return 包装后的任务
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        Objects.requireNonNull(task, "task must not be null");
        ContextCarrier carrier = ContextCarrier.capture();
        return () -> carrier.restore(task);
    }

    /**
     * 包装 Supplier，使其在执行时继承当前上下文。
     *
     * @param supplier 原始供应器
     * @param <T>      返回值类型
     * @return 包装后的供应器
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        ContextCarrier carrier = ContextCarrier.capture();
        return () -> carrier.restore(supplier);
    }

    // ==================== 执行器包装 ====================

    /**
     * 包装执行器，使提交的所有任务自动继承上下文。
     *
     * @param executor 原始执行器
     * @return 上下文感知的执行器
     */
    public static Executor wrapExecutor(Executor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        if (executor instanceof ContextAwareExecutor) {
            return executor;
        }
        return new ContextAwareExecutor(executor);
    }

    /**
     * 包装 ExecutorService，使提交的所有任务自动继承上下文。
     *
     * @param executor 原始执行器服务
     * @return 上下文感知的执行器服务
     */
    public static ExecutorService wrapExecutorService(ExecutorService executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        if (executor instanceof ContextAwareExecutor.Service) {
            return executor;
        }
        return new ContextAwareExecutor.Service(executor);
    }

    // ==================== CompletableFuture 支持 ====================

    /**
     * 创建一个在当前上下文中异步执行的 CompletableFuture。
     *
     * @param supplier 供应器
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(wrap(supplier));
    }

    /**
     * 创建一个在当前上下文中使用指定执行器异步执行的 CompletableFuture。
     *
     * @param supplier 供应器
     * @param executor 执行器
     * @param <T>      返回值类型
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, Executor executor) {
        return CompletableFuture.supplyAsync(wrap(supplier), executor);
    }

    /**
     * 创建一个在当前上下文中异步执行的 CompletableFuture。
     *
     * @param runnable 任务
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable) {
        return CompletableFuture.runAsync(wrap(runnable));
    }

    /**
     * 创建一个在当前上下文中使用指定执行器异步执行的 CompletableFuture。
     *
     * @param runnable 任务
     * @param executor 执行器
     * @return CompletableFuture
     */
    public static CompletableFuture<Void> runAsync(Runnable runnable, Executor executor) {
        return CompletableFuture.runAsync(wrap(runnable), executor);
    }
}
