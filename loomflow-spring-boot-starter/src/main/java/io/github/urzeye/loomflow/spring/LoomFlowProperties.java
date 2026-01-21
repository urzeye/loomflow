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
package io.github.urzeye.loomflow.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LoomFlow Spring Boot 配置属性。
 *
 * @author urzeye
 * @since 0.2.0
 */
@ConfigurationProperties(prefix = "loomflow")
public class LoomFlowProperties {

    /**
     * 是否启用 LoomFlow 上下文传递
     */
    private boolean enabled = true;

    /**
     * 是否自动包装 TaskExecutor
     */
    private boolean wrapTaskExecutor = true;

    /**
     * 是否自动包装 @Async 方法
     */
    private boolean wrapAsync = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWrapTaskExecutor() {
        return wrapTaskExecutor;
    }

    public void setWrapTaskExecutor(boolean wrapTaskExecutor) {
        this.wrapTaskExecutor = wrapTaskExecutor;
    }

    public boolean isWrapAsync() {
        return wrapAsync;
    }

    public void setWrapAsync(boolean wrapAsync) {
        this.wrapAsync = wrapAsync;
    }
}
