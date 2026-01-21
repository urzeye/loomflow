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

import io.github.urzeye.loomflow.spi.ContextTransmitter;
import io.github.urzeye.loomflow.spi.TransmitterManager;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 上下文载体，用于捕获和恢复上下文快照。
 * <p>
 * 这是一个内部 API，用于在跨线程传递时保存和恢复上下文状态。
 * </p>
 *
 * @author urzeye
 * @since 0.1.0
 */
public final class ContextCarrier {

    // 注册的所有 ContextKey，使用 CopyOnWriteArrayList 避免读取时加锁
    private static final CopyOnWriteArrayList<ContextKey<?>> REGISTERED_KEYS = new CopyOnWriteArrayList<>();

    // 扁平化数组：[key1, value1, key2, value2, ...]
    private final Object[] scopedValueData;
    // 扁平化数组：[transmitter1, snapshot1, transmitter2, snapshot2, ...]
    private final Object[] transmitterData;

    private ContextCarrier(Object[] scopedValueData, Object[] transmitterData) {
        this.scopedValueData = scopedValueData;
        this.transmitterData = transmitterData;
    }

    /**
     * 注册一个 ContextKey，使其可以被捕获。
     * <p>
     * 通常在应用启动时调用，或者通过 ContextKey 的静态工厂自动注册。
     * </p>
     *
     * @param key 要注册的键
     */
    public static void register(ContextKey<?> key) {
        REGISTERED_KEYS.addIfAbsent(key);
    }

    /**
     * 捕获当前线程的上下文快照。
     *
     * @return 上下文载体，如果没有任何上下文需要传递，则返回 null
     */
    public static ContextCarrier capture() {
        // 1. Capture ScopedValues
        // 先收集数据，避免多次分配。由于 Key 数量通常很少，使用临时列表或直接遍历两次（一次计数一次填充）
        // 考虑到 Key 数量少，直接遍历两次的开销小于创建 ArrayList
        int svBoundCount = 0;
        for (ContextKey<?> key : REGISTERED_KEYS) {
            if (key.scopedValue().isBound()) {
                svBoundCount++;
            }
        }

        Object[] svData = null;
        if (svBoundCount > 0) {
            svData = new Object[svBoundCount * 2];
            int index = 0;
            for (ContextKey<?> key : REGISTERED_KEYS) {
                ScopedValue<?> sv = key.scopedValue();
                if (sv.isBound()) {
                    svData[index++] = key;
                    svData[index++] = sv.get();
                }
            }
        }
        
        // 2. Capture SPI Transmitters
        List<ContextTransmitter> transmitters = TransmitterManager.getTransmitters();
        int txCount = 0;
        
        // 优化策略：使用临时数组暂存，因为 transmitter 数量是固定的
        Object[] tempTxData = null; 
        if (!transmitters.isEmpty()) {
            tempTxData = new Object[transmitters.size() * 2];
            for (ContextTransmitter tx : transmitters) {
                Object snapshot = tx.capture();
                if (snapshot != null) {
                    tempTxData[txCount * 2] = tx;
                    tempTxData[txCount * 2 + 1] = snapshot;
                    txCount++;
                }
            }
        }

        Object[] txData = null;
        if (txCount > 0) {
            if (txCount == transmitters.size()) {
                txData = tempTxData;
            } else {
                // 压缩数组
                txData = new Object[txCount * 2];
                System.arraycopy(tempTxData, 0, txData, 0, txCount * 2);
            }
        }

        if (svData == null && txData == null) {
            return null;
        }

        return new ContextCarrier(svData, txData);
    }

    /**
     * 在恢复的上下文中执行任务。
     *
     * @param task 要执行的任务
     */
    public void restore(Runnable task) {
        restoreWithScopedValues(0, task);
    }

    /**
     * 在恢复的上下文中执行任务并返回结果。
     *
     * @param task 要执行的任务
     * @param <T>  返回值类型
     * @return 任务的返回值
     * @throws Exception 如果任务抛出异常
     */
    public <T> T restore(Callable<T> task) throws Exception {
        return restoreWithScopedValues(0, task);
    }

    /**
     * 在恢复的上下文中执行供应器并返回结果。
     *
     * @param supplier 要执行的供应器
     * @param <T>      返回值类型
     * @return 供应器的返回值
     */
    public <T> T restore(java.util.function.Supplier<T> supplier) {
        try {
            return restoreWithScopedValues(0, supplier::get);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void restoreWithScopedValues(int index, Runnable task) {
        if (scopedValueData == null || index >= scopedValueData.length) {
            replayAndRun(task);
            return;
        }
        ContextKey<?> key = (ContextKey<?>) scopedValueData[index];
        Object value = scopedValueData[index + 1];
        restoreSingleScopedValue(key, value, () -> restoreWithScopedValues(index + 2, task));
    }

    private <R> R restoreWithScopedValues(int index, Callable<R> task) throws Exception {
        if (scopedValueData == null || index >= scopedValueData.length) {
            return replayAndCall(task);
        }
        ContextKey<?> key = (ContextKey<?>) scopedValueData[index];
        Object value = scopedValueData[index + 1];
        return restoreSingleScopedValueCall(key, value, () -> restoreWithScopedValues(index + 2, task));
    }

    private void replayAndRun(Runnable task) {
        if (transmitterData == null) {
            task.run();
            return;
        }

        // Replay
        int count = transmitterData.length / 2;
        // Backup array
        Object[] backups = new Object[count];

        for (int i = 0; i < count; i++) {
            ContextTransmitter tx = (ContextTransmitter) transmitterData[i * 2];
            Object snapshot = transmitterData[i * 2 + 1];
            backups[i] = tx.replay(snapshot);
        }

        try {
            task.run();
        } finally {
            // Restore (Reverse order)
            for (int i = count - 1; i >= 0; i--) {
                ContextTransmitter tx = (ContextTransmitter) transmitterData[i * 2];
                tx.restore(backups[i]);
            }
        }
    }

    private <R> R replayAndCall(Callable<R> task) throws Exception {
        if (transmitterData == null) {
            return task.call();
        }

        // Replay
        int count = transmitterData.length / 2;
        Object[] backups = new Object[count];
        
        for (int i = 0; i < count; i++) {
            ContextTransmitter tx = (ContextTransmitter) transmitterData[i * 2];
            Object snapshot = transmitterData[i * 2 + 1];
            backups[i] = tx.replay(snapshot);
        }

        try {
            return task.call();
        } finally {
            // Restore (Reverse order)
            for (int i = count - 1; i >= 0; i--) {
                ContextTransmitter tx = (ContextTransmitter) transmitterData[i * 2];
                tx.restore(backups[i]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void restoreSingleScopedValue(ContextKey<?> key, Object value, Runnable continuation) {
        ScopedValue.where((ScopedValue<T>) key.scopedValue(), (T) value)
                .run(continuation);
    }

    @SuppressWarnings("unchecked")
    private <T, R> R restoreSingleScopedValueCall(ContextKey<?> key, Object value, Callable<R> continuation) throws Exception {
        // 使用 run 代替 call 以兼容 JDK 21 和 JDK 25 的 API 差异
        // JDK 25 的 ScopedValue.call 方法签名变更为了 CallableOp，导致 Callable 无法直接匹配
        // [0]=Result, [1]=Exception
        Object[] resultHolder = new Object[2];
        ScopedValue.where((ScopedValue<T>) key.scopedValue(), (T) value)
                .run(() -> {
                    try {
                        resultHolder[0] = continuation.call();
                    } catch (Exception e) {
                        resultHolder[1] = e;
                    }
                });

        if (resultHolder[1] != null) {
            throw (Exception) resultHolder[1];
        }
        return (R) resultHolder[0];
    }
}
