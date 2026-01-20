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
package io.github.urzeye.loomflow.executor;

import io.github.urzeye.loomflow.FlowContext;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 上下文感知的执行器装饰器。
 * <p>
 * 包装一个普通的 {@link Executor}，使提交的所有任务自动继承提交时的上下文。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 * Executor contextAware = new ContextAwareExecutor(executor);
 *
 * FlowContext.with(TRACE_ID, "abc-123").run(() -> {
 *     contextAware.execute(() -> {
 *         // 这里可以访问 TRACE_ID
 *         String traceId = FlowContext.get(TRACE_ID);
 *     });
 * });
 * }</pre>
 *
 * @author urzeye
 * @since 0.1.0
 */
public class ContextAwareExecutor implements Executor {

    protected final Executor delegate;

    public ContextAwareExecutor(Executor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(FlowContext.wrap(command));
    }

    /**
     * 上下文感知的 ExecutorService 装饰器。
     */
    public static class Service extends ContextAwareExecutor implements ExecutorService {

        private final ExecutorService delegateService;

        public Service(ExecutorService delegate) {
            super(delegate);
            this.delegateService = delegate;
        }

        @Override
        public void shutdown() {
            delegateService.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegateService.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegateService.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegateService.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegateService.awaitTermination(timeout, unit);
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegateService.submit(FlowContext.wrap(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegateService.submit(FlowContext.wrap(task), result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegateService.submit(FlowContext.wrap(task));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegateService.invokeAll(wrapAll(tasks));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
                                              long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegateService.invokeAll(wrapAll(tasks), timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return delegateService.invokeAny(wrapAll(tasks));
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
                               long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegateService.invokeAny(wrapAll(tasks), timeout, unit);
        }

        private <T> Collection<Callable<T>> wrapAll(Collection<? extends Callable<T>> tasks) {
            return tasks.stream()
                    .map(FlowContext::wrap)
                    .toList();
        }
    }
}
