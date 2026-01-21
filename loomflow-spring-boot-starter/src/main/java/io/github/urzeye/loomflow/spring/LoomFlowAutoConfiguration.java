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

import org.springframework.aop.Advisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;

/**
 * LoomFlow Spring Boot 自动配置。
 * <p>
 * 自动启用上下文传递功能：
 * </p>
 * <ul>
 *   <li>自动包装所有 {@code TaskExecutor} Bean</li>
 *   <li>自动拦截 {@code @Async} 方法传递上下文</li>
 * </ul>
 *
 * <h2>配置属性</h2>
 * <pre>
 * loomflow.enabled=true              # 总开关
 * loomflow.wrap-task-executor=true   # 是否包装 TaskExecutor
 * loomflow.wrap-async=true           # 是否拦截 @Async
 * </pre>
 *
 * @author urzeye
 * @since 0.2.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.github.urzeye.loomflow.FlowContext")
@ConditionalOnProperty(name = "loomflow.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LoomFlowProperties.class)
public class LoomFlowAutoConfiguration {

    /**
     * TaskExecutor 后置处理器
     */
    @Bean
    @ConditionalOnProperty(name = "loomflow.wrap-task-executor", havingValue = "true", matchIfMissing = true)
    public TaskExecutorBeanPostProcessor taskExecutorBeanPostProcessor() {
        return new TaskExecutorBeanPostProcessor();
    }

    /**
     * @Async 拦截切面
     */
    @Bean
    @ConditionalOnClass(Async.class)
    @ConditionalOnProperty(name = "loomflow.wrap-async", havingValue = "true", matchIfMissing = true)
    public Advisor asyncContextAdvisor() {
        return AsyncContextInterceptor.createAdvisor();
    }
}
