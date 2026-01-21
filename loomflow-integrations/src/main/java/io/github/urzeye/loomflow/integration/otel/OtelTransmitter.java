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
package io.github.urzeye.loomflow.integration.otel;

import io.github.urzeye.loomflow.spi.ContextTransmitter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * OpenTelemetry 上下文传递器。
 *
 * @since 0.3.0
 */
public class OtelTransmitter implements ContextTransmitter {

    @Override
    public Object capture() {
        return Context.current();
    }

    @Override
    public Object replay(Object snapshot) {
        if (snapshot instanceof Context) {
            return ((Context) snapshot).makeCurrent();
        }
        return null;
    }

    @Override
    public void restore(Object backup) {
        if (backup instanceof Scope) {
            ((Scope) backup).close();
        }
    }
}
