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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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

    // 注册的所有 ContextKey，用于遍历捕获
    private static final List<ContextKey<?>> REGISTERED_KEYS = new ArrayList<>();

    private final List<Snapshot<?>> snapshots;
    private final List<TransmitterSnapshot> transmitterSnapshots;

    private ContextCarrier(List<Snapshot<?>> snapshots, List<TransmitterSnapshot> transmitterSnapshots) {
        this.snapshots = snapshots;
        this.transmitterSnapshots = transmitterSnapshots;
    }

    /**
     * 注册一个 ContextKey，使其可以被捕获。
     * <p>
     * 通常在应用启动时调用，或者通过 ContextKey 的静态工厂自动注册。
     * </p>
     *
     * @param key 要注册的键
     */
    public static synchronized void register(ContextKey<?> key) {
        if (!REGISTERED_KEYS.contains(key)) {
            REGISTERED_KEYS.add(key);
        }
    }

    /**
     * 捕获当前线程的上下文快照。
     *
     * @return 上下文载体
     */
    public static ContextCarrier capture() {
        // 1. Capture ScopedValues
        List<Snapshot<?>> snapshots = new ArrayList<>();
        synchronized (ContextCarrier.class) {
            for (ContextKey<?> key : REGISTERED_KEYS) {
                captureKey(key, snapshots);
            }
        }
        
        // 2. Capture SPI Transmitters
        List<TransmitterSnapshot> transmitterSnapshots = new ArrayList<>();
        for (ContextTransmitter transmitter : TransmitterManager.getTransmitters()) {
            Object captured = transmitter.capture();
            if (captured != null) {
                transmitterSnapshots.add(new TransmitterSnapshot(transmitter, captured));
            }
        }

        return new ContextCarrier(snapshots, transmitterSnapshots);
    }

    private static <T> void captureKey(ContextKey<T> key, List<Snapshot<?>> snapshots) {
        ScopedValue<T> sv = key.scopedValue();
        if (sv.isBound()) {
            snapshots.add(new Snapshot<>(key, sv.get()));
        }
    }

    /**
     * 在恢复的上下文中执行任务。
     *
     * @param task 要执行的任务
     */
    public void restore(Runnable task) {
        restoreWithSnapshots(0, task);
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
        return restoreWithSnapshots(0, task);
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
            return restoreWithSnapshots(0, supplier::get);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void restoreWithSnapshots(int index, Runnable task) {
        if (index >= snapshots.size()) {
            // Replay all transmitters
            replayAndRun(task);
            return;
        }
        Snapshot<?> snapshot = snapshots.get(index);
        restoreSnapshot(snapshot, () -> restoreWithSnapshots(index + 1, task));
    }

    private <R> R restoreWithSnapshots(int index, Callable<R> task) throws Exception {
        if (index >= snapshots.size()) {
            // Replay all transmitters
            return replayAndCall(task);
        }
        Snapshot<?> snapshot = snapshots.get(index);
        return restoreSnapshotCall(snapshot, () -> restoreWithSnapshots(index + 1, task));
    }

    private void replayAndRun(Runnable task) {
        // Replay
        List<TransmitterBackup> backups = new ArrayList<>(transmitterSnapshots.size());
        for (TransmitterSnapshot ts : transmitterSnapshots) {
            Object backup = ts.transmitter.replay(ts.snapshot);
            backups.add(new TransmitterBackup(ts.transmitter, backup));
        }

        try {
            task.run();
        } finally {
            // Restore (Reverse order)
            for (int i = backups.size() - 1; i >= 0; i--) {
                TransmitterBackup tb = backups.get(i);
                tb.transmitter.restore(tb.backup);
            }
        }
    }

    private <R> R replayAndCall(Callable<R> task) throws Exception {
        // Replay
        List<TransmitterBackup> backups = new ArrayList<>(transmitterSnapshots.size());
        for (TransmitterSnapshot ts : transmitterSnapshots) {
            Object backup = ts.transmitter.replay(ts.snapshot);
            backups.add(new TransmitterBackup(ts.transmitter, backup));
        }

        try {
            return task.call();
        } finally {
            // Restore (Reverse order)
            for (int i = backups.size() - 1; i >= 0; i--) {
                TransmitterBackup tb = backups.get(i);
                tb.transmitter.restore(tb.backup);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void restoreSnapshot(Snapshot<T> snapshot, Runnable continuation) {
        ScopedValue.where((ScopedValue<T>) snapshot.key().scopedValue(), snapshot.value())
                .run(continuation);
    }

    @SuppressWarnings("unchecked")
    private <T, R> R restoreSnapshotCall(Snapshot<T> snapshot, Callable<R> continuation) throws Exception {
        return ScopedValue.where((ScopedValue<T>) snapshot.key().scopedValue(), snapshot.value())
                .call(continuation);
    }

    /**
     * 快照记录。
     */
    private record Snapshot<T>(ContextKey<T> key, T value) {
    }

    private record TransmitterSnapshot(ContextTransmitter transmitter, Object snapshot) {
    }
    
    private record TransmitterBackup(ContextTransmitter transmitter, Object backup) {
    }
}
