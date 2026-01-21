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

import io.github.urzeye.loomflow.FlowContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * TaskExecutor 后置处理器，自动包装所有 TaskExecutor。
 *
 * @author urzeye
 * @since 0.2.0
 */
public class TaskExecutorBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof TaskExecutor taskExecutor) {
            return new ContextAwareTaskExecutor(taskExecutor);
        }
        return bean;
    }

    /**
     * 上下文感知的 TaskExecutor 包装器
     */
    static class ContextAwareTaskExecutor implements TaskExecutor {
        
        private final TaskExecutor delegate;

        ContextAwareTaskExecutor(TaskExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable task) {
            delegate.execute(FlowContext.wrap(task));
        }
    }
}
