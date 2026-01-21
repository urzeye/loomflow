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
package io.github.urzeye.loomflow.integration.mdc;

import io.github.urzeye.loomflow.spi.ContextTransmitter;
import org.slf4j.MDC;

import java.util.Map;

/**
 * SLF4J MDC 上下文传递器。
 *
 * @since 0.3.0
 */
public class MdcTransmitter implements ContextTransmitter {

    @Override
    public Object capture() {
        return MDC.getCopyOfContextMap();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object replay(Object snapshot) {
        // 备份当前线程的 MDC
        Map<String, String> backup = MDC.getCopyOfContextMap();

        // 恢复快照
        if (snapshot instanceof Map) {
            MDC.setContextMap((Map<String, String>) snapshot);
        } else {
            MDC.clear();
        }
        
        return backup;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void restore(Object backup) {
        if (backup instanceof Map) {
            MDC.setContextMap((Map<String, String>) backup);
        } else {
            MDC.clear();
        }
    }
}
