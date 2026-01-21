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
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.scheduling.annotation.Async;

/**
 * @Async 方法拦截器，在异步方法执行前捕获上下文。
 * <p>
 * 通过 AOP 拦截带有 @Async 注解的方法，自动传递上下文。
 * </p>
 *
 * @author urzeye
 * @since 0.2.0
 */
public class AsyncContextInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 捕获当前上下文
        var carrier = io.github.urzeye.loomflow.ContextCarrier.capture();
        
        // 在恢复的上下文中执行
        try {
            java.util.concurrent.Callable<Object> callable = () -> {
                try {
                    return invocation.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            };
            return carrier.restore(callable);
        } catch (RuntimeException e) {
            if (e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        }
    }

    /**
     * 创建 @Async 切面
     */
    public static Advisor createAdvisor() {
        Pointcut pointcut = new AnnotationMatchingPointcut(null, Async.class, true);
        return new DefaultPointcutAdvisor(pointcut, new AsyncContextInterceptor());
    }
}
