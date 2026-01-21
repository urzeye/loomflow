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

/**
 * 上下文传递器 SPI。
 * <p>
 * 用于扩展 LoomFlow 的能力，使第三方上下文（如 MDC、OpenTelemetry、ThreadLocal）能够跟随
 * FlowContext 跨线程传递。
 * </p>
 *
 * @since 0.3.0
 */
public interface ContextTransmitter {

    /**
     * 在当前线程（父线程）抓取快照。
     *
     * @return 上下文快照，如果无须传递可返回 null
     */
    Object capture();

    /**
     * 在目标线程（子线程）重放上下文。
     *
     * @param snapshot {@link #capture()} 返回的快照对象
     * @return 备份对象，用于在 {@link #restore(Object)} 时恢复现场
     */
    Object replay(Object snapshot);

    /**
     * 在目标线程（子线程）执行完任务后，恢复现场。
     *
     * @param backup {@link #replay(Object)} 返回的备份对象
     */
    void restore(Object backup);
}
