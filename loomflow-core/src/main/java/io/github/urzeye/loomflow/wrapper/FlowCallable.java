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
package io.github.urzeye.loomflow.wrapper;

import io.github.urzeye.loomflow.ContextCarrier;
import io.github.urzeye.loomflow.spi.LoomFlowWrapped;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * LoomFlow 上下文包装的 Callable。
 *
 * @param <T> 返回值类型
 * @since 0.3.0
 */
public final class FlowCallable<T> implements Callable<T>, LoomFlowWrapped {

    private final ContextCarrier carrier;
    private final Callable<T> task;

    public FlowCallable(ContextCarrier carrier, Callable<T> task) {
        this.carrier = Objects.requireNonNull(carrier, "carrier must not be null");
        this.task = Objects.requireNonNull(task, "task must not be null");
    }

    @Override
    public T call() throws Exception {
        return carrier.restore(task);
    }

    public Callable<T> getOriginalTask() {
        return task;
    }
}
