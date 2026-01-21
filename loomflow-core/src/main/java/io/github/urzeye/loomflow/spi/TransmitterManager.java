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
package io.github.urzeye.loomflow.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 上下文传递器管理器。
 * <p>
 * 负责 SPI 加载和管理。
 * </p>
 *
 * @since 0.3.0
 */
public class TransmitterManager {

    private static final List<ContextTransmitter> TRANSMITTERS = new CopyOnWriteArrayList<>();

    static {
        refresh();
    }

    /**
     * 重新加载所有的 ContextTransmitter
     */
    public synchronized static void refresh() {
        List<ContextTransmitter> loaded = new ArrayList<>();
        ServiceLoader<ContextTransmitter> loader = ServiceLoader.load(ContextTransmitter.class);
        for (ContextTransmitter transmitter : loader) {
            loaded.add(transmitter);
        }
        TRANSMITTERS.clear();
        TRANSMITTERS.addAll(loaded);
    }

    /**
     * 获取当前注册的所有传递器
     */
    public static List<ContextTransmitter> getTransmitters() {
        return Collections.unmodifiableList(TRANSMITTERS);
    }
    
    /**
     * 手动注册传递器 (便于测试或非 SPI 场景)
     */
    public static void register(ContextTransmitter transmitter) {
        if (!TRANSMITTERS.contains(transmitter)) {
            TRANSMITTERS.add(transmitter);
        }
    }
}
