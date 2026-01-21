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

import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 结构化并发便捷工具类。
 * <p>
 * 提供简化的 API 来执行并发任务，同时自动传递上下文。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 并行执行多个任务，全部成功才返回
 * FlowContext.with(TRACE_ID, "abc").run(() -> {
 *     List<String> results = FlowTasks.invokeAll(
 *         () -> fetchFromServiceA(),
 *         () -> fetchFromServiceB()
 *     );
 * });
 *
 * // 任意一个成功就返回
 * String fastest = FlowTasks.invokeAny(
 *     () -> fetchFromCDN1(),
 *     () -> fetchFromCDN2()
 * );
 * }</pre>
 *
 * @author urzeye
 * @since 0.2.0
 */
public final class FlowTasks {

    private FlowTasks() {
        // 工具类，禁止实例化
    }

    /**
     * 并行执行所有任务，等待全部完成。
     * <p>
     * 上下文会自动传递给所有子任务。
     * 如果任何任务失败，抛出第一个异常。
     * </p>
     *
     * @param tasks 要执行的任务
     * @param <T>   返回类型
     * @return 所有任务的结果列表
     * @throws Exception 如果任何任务失败
     */
    @SafeVarargs
    public static <T> List<T> invokeAll(Callable<T>... tasks) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<T>> subtasks = new java.util.ArrayList<>();
            
            for (Callable<T> task : tasks) {
                subtasks.add(scope.fork(task::call));
            }
            
            scope.join();
            scope.throwIfFailed();
            
            return subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 并行执行所有任务，返回第一个成功的结果。
     * <p>
     * 上下文会自动传递给所有子任务。
     * 一旦有任务成功，其他任务将被取消。
     * </p>
     *
     * @param tasks 要执行的任务
     * @param <T>   返回类型
     * @return 第一个成功任务的结果
     * @throws Exception 如果所有任务都失败
     */
    @SafeVarargs
    public static <T> T invokeAny(Callable<T>... tasks) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnSuccess<T>()) {
            for (Callable<T> task : tasks) {
                scope.fork(task::call);
            }
            
            scope.join();
            return scope.result();
        }
    }

    /**
     * 并行执行多个任务，并对结果进行聚合。
     *
     * @param combiner 结果组合器
     * @param tasks    要执行的任务
     * @param <T>      任务返回类型
     * @param <R>      组合后的返回类型
     * @return 组合后的结果
     * @throws Exception 如果任何任务失败
     */
    @SafeVarargs
    public static <T, R> R invokeAllAndCombine(Function<List<T>, R> combiner, Callable<T>... tasks) throws Exception {
        List<T> results = invokeAll(tasks);
        return combiner.apply(results);
    }
}
