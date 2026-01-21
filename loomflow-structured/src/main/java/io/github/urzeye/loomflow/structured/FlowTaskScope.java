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
     * 使用默认配置创建任务作用域
     */
    public FlowTaskScope() {
        this.delegate = StructuredConcurrencySupport.openRawScope();
    }

    /**
     * 使用指定名称创建任务作用域
     *
     * @param name 作用域名称，用于调试
     */
    public FlowTaskScope(String name) {
        // JDK 25/21 Raw Scope abstraction doesn't support name easily via current Shim, 
        // fallback to default raw scope for compatibility.
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
        public ShutdownOnSuccess(StructuredConcurrencySupport.ScopeHandle delegate) {
            super(delegate);
        }

        @SuppressWarnings("unchecked")
        public T result() throws java.util.concurrent.ExecutionException, InterruptedException {
            try {
                java.lang.reflect.Method m = super.delegate.rawScope.getClass().getMethod("result");
                return (T) m.invoke(super.delegate.rawScope);
            } catch (NoSuchMethodException e) {
                // JDK 25: Joiner 行为，join() 返回 scope
                // 如果 JDK 25 移除了 result()，此包装器难以实现，除非我们手动追踪 subtask
                // 暂时抛出不支持异常，建议使用 FlowTasks
                throw new UnsupportedOperationException("On JDK 25, please use FlowTasks.invokeAny() or examine Subtasks directly.");
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
