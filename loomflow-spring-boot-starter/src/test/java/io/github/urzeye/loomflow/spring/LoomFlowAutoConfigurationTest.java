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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoomFlow Spring Boot 自动配置测试")
class LoomFlowAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LoomFlowAutoConfiguration.class));

    @Test
    @DisplayName("默认配置下 Bean 应该存在")
    void testDefaultConfiguration() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasSingleBean(LoomFlowProperties.class);
                    assertThat(context).hasSingleBean(TaskExecutorBeanPostProcessor.class);
                    // 实际上注入的是 Advisor，而不是 Interceptor 类本身
                    assertThat(context).hasBean("asyncContextAdvisor");
                });
    }

    @Test
    @DisplayName("可以通过属性禁用")
    void testDisabled() {
        contextRunner
                .withPropertyValues("loomflow.enabled=false")
                .run(context -> {
                    // 禁用时，AutoConfiguration 被跳过，Properties Bean 也不应该存在
                    assertThat(context).doesNotHaveBean(LoomFlowProperties.class);
                    assertThat(context).doesNotHaveBean(TaskExecutorBeanPostProcessor.class);
                    assertThat(context).doesNotHaveBean("asyncContextAdvisor");
                });
    }

    @Test
    @DisplayName("TaskExecutorBeanPostProcessor 应该包装 TaskExecutor")
    void testTaskExecutorWrapping() {
        contextRunner
                .withBean("myTaskExecutor", ThreadPoolTaskExecutor.class, ThreadPoolTaskExecutor::new)
                .run(context -> {
                    // BeanPostProcessor 已生效，原始 Bean 被包装
                    assertThat(context).hasSingleBean(TaskExecutorBeanPostProcessor.class);
                    
                    // 获取 Bean，应该是被包装后的类型
                    Object bean = context.getBean("myTaskExecutor");
                    assertThat(bean).isInstanceOf(org.springframework.core.task.TaskExecutor.class);
                    assertThat(bean).isNotInstanceOf(ThreadPoolTaskExecutor.class); // 确认已被包装
                    assertThat(bean.getClass().getSimpleName()).contains("ContextAwareTaskExecutor");
                });
    }
}
