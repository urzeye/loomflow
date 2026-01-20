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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 上下文作用域构建器。
 * <p>
 * 用于声明式地绑定多个上下文值，并在作用域内执行代码。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * FlowContext.with(TRACE_ID, "abc-123")
 *     .and(USER, currentUser)
 *     .and(TENANT_ID, "tenant-1")
 *     .run(() -> processRequest());
 * }</pre>
 *
 * @author urzeye
 * @since 0.1.0
 */
public final class FlowScope {

    private final List<Binding<?>> bindings = new ArrayList<>();

    FlowScope() {
    }

    /**
     * 添加一个上下文绑定。
     *
     * @param key   上下文键
     * @param value 要绑定的值
     * @param <T>   值类型
     * @return 当前构建器（支持链式调用）
     */
    public <T> FlowScope and(ContextKey<T> key, T value) {
        Objects.requireNonNull(key, "key must not be null");
        bindings.add(new Binding<>(key, value));
        return this;
    }

    /**
     * 在当前作用域内执行任务。
     *
     * @param task 要执行的任务
     */
    public void run(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        runWithBindings(0, task);
    }

    /**
     * 在当前作用域内执行任务并返回结果。
     *
     * @param task 要执行的任务
     * @param <T>  返回值类型
     * @return 任务的返回值
     * @throws Exception 如果任务抛出异常
     */
    public <T> T call(Callable<T> task) throws Exception {
        Objects.requireNonNull(task, "task must not be null");
        return callWithBindings(0, task);
    }

    /**
     * 在当前作用域内执行任务并返回结果（不抛出受检异常）。
     *
     * @param task 要执行的任务
     * @param <T>  返回值类型
     * @return 任务的返回值
     * @throws RuntimeException 如果任务抛出异常，将被包装为 RuntimeException
     */
    public <T> T callUnchecked(Callable<T> task) {
        try {
            return call(task);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 递归绑定所有值后执行任务
    private void runWithBindings(int index, Runnable task) {
        if (index >= bindings.size()) {
            task.run();
            return;
        }
        Binding<?> binding = bindings.get(index);
        runBinding(binding, () -> runWithBindings(index + 1, task));
    }

    private <T> T callWithBindings(int index, Callable<T> task) throws Exception {
        if (index >= bindings.size()) {
            return task.call();
        }
        Binding<?> binding = bindings.get(index);
        return callBinding(binding, () -> callWithBindings(index + 1, task));
    }

    @SuppressWarnings("unchecked")
    private <T> void runBinding(Binding<T> binding, Runnable continuation) {
        ScopedValue.where((ScopedValue<T>) binding.key().scopedValue(), binding.value())
                .run(continuation);
    }

    @SuppressWarnings("unchecked")
    private <T, R> R callBinding(Binding<T> binding, Callable<R> continuation) throws Exception {
        return ScopedValue.where((ScopedValue<T>) binding.key().scopedValue(), binding.value())
                .call(continuation);
    }

    /**
     * 内部绑定记录。
     */
    private record Binding<T>(ContextKey<T> key, T value) {
    }
}
