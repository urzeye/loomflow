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
public class FlowTaskScope<T> implements AutoCloseable {

    protected final StructuredConcurrencySupport.ScopeHandle delegate;

    /**
     * 使用默认配置创建任务作用域。
     * <p>
     * <b>注意：此构造函数仅在 JDK 25+ 上可用。</b>
     * 在 JDK 21 上请使用 {@link #shutdownOnFailure()} 或 {@link #shutdownOnSuccess()} 工厂方法。
     * </p>
     *
     * @throws UnsupportedOperationException 在 JDK 21 上调用时抛出
     */
    public FlowTaskScope() {
        this.delegate = StructuredConcurrencySupport.openRawScope();
    }

    /**
     * 使用指定名称创建任务作用域。
     * <p>
     * <b>注意：此构造函数仅在 JDK 25+ 上可用。</b>
     * 在 JDK 21 上请使用 {@link #shutdownOnFailure()} 或 {@link #shutdownOnSuccess()} 工厂方法。
     * </p>
     *
     * @param name 作用域名称，用于调试（当前版本忽略此参数）
     * @throws UnsupportedOperationException 在 JDK 21 上调用时抛出
     */
    public FlowTaskScope(String name) {
        this.delegate = StructuredConcurrencySupport.openRawScope();
    }

    // Internal constructor for wrappers
    protected FlowTaskScope(StructuredConcurrencySupport.ScopeHandle delegate) {
        this.delegate = delegate;
    }

    public StructuredTaskScope.Subtask<T> fork(java.util.concurrent.Callable<? extends T> task) {
        return delegate.fork(task);
    }

    public FlowTaskScope<T> join() throws InterruptedException {
        delegate.join();
        return this;
    }

    /**
     * 等待所有子任务完成，或在指定时间后超时。
     * <p>
     * 如果在截止时间前所有任务都完成，正常返回；
     * 否则抛出 {@link java.util.concurrent.TimeoutException}。
     * </p>
     *
     * @param timeout 超时时长
     * @return this
     * @throws InterruptedException 如果当前线程被中断
     * @throws java.util.concurrent.TimeoutException 如果超时
     */
    public FlowTaskScope<T> join(java.time.Duration timeout) 
            throws InterruptedException, java.util.concurrent.TimeoutException {
        delegate.joinUntil(java.time.Instant.now().plus(timeout));
        return this;
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * 创建一个"快速失败"作用域
     */
    public static ShutdownOnFailure shutdownOnFailure() {
        return new ShutdownOnFailure(StructuredConcurrencySupport.openFailureScope());
    }

    /**
     * 创建一个"快速成功"作用域
     */
    public static <T> ShutdownOnSuccess<T> shutdownOnSuccess() {
        return new ShutdownOnSuccess<>(StructuredConcurrencySupport.openSuccessScope());
    }

    // ShutdownOnFailure 包装器
    public static class ShutdownOnFailure extends FlowTaskScope<Object> {
        public ShutdownOnFailure(StructuredConcurrencySupport.ScopeHandle delegate) {
            super(delegate);
        }

        public void throwIfFailed() throws java.util.concurrent.ExecutionException, InterruptedException {
            // 由于不知道底层 delegate 的确切 API (JDK 21 vs 25)
            // 为了行为一致性，我们尝试手动检查或委托
            // 但 delegate.join() 不会抛出异常
            // 手动实现最安全且一致，但这里无法访问 subtasks 列表

            // 优化逻辑:
            // 尝试通过反射调用 throwIfFailed (如果存在，即 JDK 21)
            // 如果不存在 (JDK 25)，假设 join() 如果失败在当时就已经抛出了异常
            // 实际上在 JDK 25，Joiner.awaitAllSuccessfulOrThrow 会导致 join() 抛出异常
            // 如果 join() 没抛异常，说明成功
            try {
                java.lang.reflect.Method m = super.delegate.rawScope.getClass().getMethod("throwIfFailed");
                m.invoke(super.delegate.rawScope);
            } catch (NoSuchMethodException e) {
                // JDK 25: join() 已处理异常，此处无需操作。
            } catch (Exception e) {
                if (e instanceof java.lang.reflect.InvocationTargetException) {
                    Throwable target = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                    if (target instanceof java.util.concurrent.ExecutionException) {
                        throw (java.util.concurrent.ExecutionException) target;
                    }
                    if (target instanceof RuntimeException) {
                        throw (RuntimeException) target;
                    }
                    throw new RuntimeException(target);
                }
                throw new RuntimeException(e);
            }
        }
    }

    // ShutdownOnSuccess 包装器
    public static class ShutdownOnSuccess<T> extends FlowTaskScope<T> {
        // 用于缓存 JDK 25 上 join() 返回的结果
        private volatile Object joinResult;

        public ShutdownOnSuccess(StructuredConcurrencySupport.ScopeHandle delegate) {
            super(delegate);
        }

        @Override
        public FlowTaskScope<T> join() throws InterruptedException {
            if (StructuredConcurrencySupport.isJdk25Style()) {
                // JDK 25: join() 直接返回第一个成功的结果
                this.joinResult = delegate.joinAndGetResult();
            } else {
                // JDK 21: join() 不返回值，需要调用 result()
                delegate.join();
            }
            return this;
        }

        /**
         * 获取第一个成功任务的结果。
         * <p>
         * 必须在 {@link #join()} 之后调用。
         * </p>
         *
         * @return 第一个成功任务的结果
         * @throws java.util.concurrent.ExecutionException 如果任务执行失败
         * @throws InterruptedException 如果被中断
         */
        @SuppressWarnings("unchecked")
        public T result() throws java.util.concurrent.ExecutionException, InterruptedException {
            if (StructuredConcurrencySupport.isJdk25Style()) {
                // JDK 25: 返回 join() 时缓存的结果
                return (T) joinResult;
            } else {
                // JDK 21: 调用底层 scope 的 result() 方法
                try {
                    java.lang.reflect.Method m = super.delegate.rawScope.getClass().getMethod("result");
                    return (T) m.invoke(super.delegate.rawScope);
                } catch (NoSuchMethodException e) {
                    throw new UnsupportedOperationException("result() method not found", e);
                } catch (Exception e) {
                    if (e instanceof java.lang.reflect.InvocationTargetException) {
                        Throwable target = ((java.lang.reflect.InvocationTargetException) e).getTargetException();
                        if (target instanceof java.util.concurrent.ExecutionException)
                            throw (java.util.concurrent.ExecutionException) target;
                        throw new RuntimeException(target);
                    }
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
