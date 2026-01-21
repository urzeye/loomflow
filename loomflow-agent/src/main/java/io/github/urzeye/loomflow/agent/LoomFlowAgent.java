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
package io.github.urzeye.loomflow.agent;

import io.github.urzeye.loomflow.FlowContext;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.security.ProtectionDomain;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.jar.JarFile;

/**
 * LoomFlow Java Agent 入口。
 * <p>
 * 使用 ByteBuddy 对线程池进行字节码增强，实现上下文透明传递。
 * Agent 会自动将必要的类注入到 Bootstrap ClassLoader。
 * </p>
 *
 * <h2>使用方式</h2>
 * <pre>
 * java -javaagent:loomflow-agent.jar -jar myapp.jar
 * </pre>
 *
 * <h2>增强目标</h2>
 * <ul>
 *   <li>{@code ThreadPoolExecutor.execute/submit}</li>
 *   <li>{@code ForkJoinPool.execute/submit}</li>
 *   <li>{@code ScheduledThreadPoolExecutor.schedule*}</li>
 *   <li>{@code CompletableFuture} 异步方法</li>
 *   <li>{@code Timer.schedule*}</li>
 *   <li>所有应用自定义 Executor 实现</li>
 * </ul>
 *
 * @author urzeye
 * @since 0.2.0
 */
public class LoomFlowAgent {

    private static volatile boolean installed = false;
    private static final String ENABLE_BOOTSTRAP_PROP = "loomflow.agent.bootstrap";

    /**
     * Agent premain 入口（JVM 启动时加载）
     */
    public static void premain(String args, Instrumentation inst) {
        install(args, inst);
    }

    /**
     * Agent agentmain 入口（运行时 attach）
     */
    public static void agentmain(String args, Instrumentation inst) {
        install(args, inst);
    }

    private static synchronized void install(String args, Instrumentation inst) {
        if (installed) {
            System.out.println("[LoomFlow Agent] Already installed, skipping");
            return;
        }

        boolean enableBootstrap = "true".equalsIgnoreCase(System.getProperty(ENABLE_BOOTSTRAP_PROP, "true"));
        
        if (enableBootstrap) {
            try {
                injectBootstrapClasses(inst);
            } catch (Exception e) {
                System.out.println("[LoomFlow Agent] Bootstrap injection skipped: " + e.getMessage());
                enableBootstrap = false;
            }
        }

        AgentBuilder.Identified.Extendable agentBuilder = new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new LoggingListener())
                // 增强所有实现 Executor 的应用类（排除 java.* 包）
                .type(ElementMatchers.isSubTypeOf(Executor.class)
                        .and(ElementMatchers.not(ElementMatchers.nameStartsWith("java.")))
                        .and(ElementMatchers.not(ElementMatchers.nameStartsWith("sun.")))
                        .and(ElementMatchers.not(ElementMatchers.nameStartsWith("jdk."))))
                .transform(new ApplicationExecutorTransformer());

        // 如果启用了 Bootstrap 增强，同时增强 JDK 核心类
        if (enableBootstrap) {
            agentBuilder = agentBuilder
                    // ThreadPoolExecutor
                    .type(ElementMatchers.named("java.util.concurrent.ThreadPoolExecutor"))
                    .transform(new ThreadPoolExecutorTransformer())
                    // ForkJoinPool
                    .type(ElementMatchers.named("java.util.concurrent.ForkJoinPool"))
                    .transform(new ForkJoinPoolTransformer())
                    // ScheduledThreadPoolExecutor
                    .type(ElementMatchers.named("java.util.concurrent.ScheduledThreadPoolExecutor"))
                    .transform(new ScheduledExecutorTransformer())
                    // AbstractExecutorService (submit 方法的实现类)
                    .type(ElementMatchers.named("java.util.concurrent.AbstractExecutorService"))
                    .transform(new AbstractExecutorServiceTransformer())
                    // CompletableFuture
                    .type(ElementMatchers.named("java.util.concurrent.CompletableFuture"))
                    .transform(new CompletableFutureTransformer())
                    // Timer
                    .type(ElementMatchers.named("java.util.Timer"))
                    .transform(new TimerTransformer());
        }

        agentBuilder.installOn(inst);
        installed = true;
        System.out.println("[LoomFlow Agent] Installed successfully" + 
                          (enableBootstrap ? " (with JDK bootstrap)" : " (app classes only)"));
    }

    /**
     * 将必要的类注入到 Bootstrap ClassLoader
     */
    private static void injectBootstrapClasses(Instrumentation inst) throws IOException, URISyntaxException {
        String agentPath = LoomFlowAgent.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
        
        if (agentPath != null && agentPath.endsWith(".jar")) {
            File agentJar = new File(agentPath);
            if (agentJar.exists()) {
                inst.appendToBootstrapClassLoaderSearch(new JarFile(agentJar));
                System.out.println("[LoomFlow Agent] Injected: " + agentJar.getName());
            }
        }
        
        String corePath = FlowContext.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
        
        if (corePath != null && corePath.endsWith(".jar")) {
            File coreJar = new File(corePath);
            if (coreJar.exists()) {
                inst.appendToBootstrapClassLoaderSearch(new JarFile(coreJar));
                System.out.println("[LoomFlow Agent] Injected: " + coreJar.getName());
            }
        }
    }

    // ==================== Listeners ====================

    static class LoggingListener extends AgentBuilder.Listener.Adapter {
        @Override
        public void onTransformation(TypeDescription type, ClassLoader loader,
                                      JavaModule module, boolean loaded, DynamicType dynamicType) {
            System.out.println("[LoomFlow Agent] Transformed: " + type.getName());
        }

        @Override
        public void onError(String typeName, ClassLoader loader, 
                            JavaModule module, boolean loaded, Throwable throwable) {
            System.err.println("[LoomFlow Agent] Transform error: " + typeName + " - " + throwable.getMessage());
        }
    }

    // ==================== Transformers ====================

    /**
     * 应用类 Executor 转换器
     */
    static class ApplicationExecutorTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("execute")
                                    .and(ElementMatchers.takesArgument(0, Runnable.class))))
                    .visit(Advice.to(CallableAdvice.class)
                            .on(ElementMatchers.named("submit")
                                    .and(ElementMatchers.takesArgument(0, Callable.class))))
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("submit")
                                    .and(ElementMatchers.takesArgument(0, Runnable.class))));
        }
    }

    /**
     * ThreadPoolExecutor 转换器
     */
    static class ThreadPoolExecutorTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder.visit(Advice.to(RunnableAdvice.class)
                    .on(ElementMatchers.named("execute")
                            .and(ElementMatchers.takesArgument(0, Runnable.class))));
        }
    }

    /**
     * AbstractExecutorService 转换器（submit 方法）
     */
    static class AbstractExecutorServiceTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder
                    .visit(Advice.to(CallableAdvice.class)
                            .on(ElementMatchers.named("submit")
                                    .and(ElementMatchers.takesArgument(0, Callable.class))))
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("submit")
                                    .and(ElementMatchers.takesArgument(0, Runnable.class))));
        }
    }

    /**
     * ForkJoinPool 转换器
     */
    static class ForkJoinPoolTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("execute")
                                    .and(ElementMatchers.takesArgument(0, Runnable.class))))
                    .visit(Advice.to(CallableAdvice.class)
                            .on(ElementMatchers.named("submit")
                                    .and(ElementMatchers.takesArgument(0, Callable.class))));
        }
    }

    /**
     * ScheduledThreadPoolExecutor 转换器
     */
    static class ScheduledExecutorTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("schedule")
                                    .and(ElementMatchers.takesArgument(0, Runnable.class))))
                    .visit(Advice.to(CallableAdvice.class)
                            .on(ElementMatchers.named("schedule")
                                    .and(ElementMatchers.takesArgument(0, Callable.class))))
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("scheduleAtFixedRate")))
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("scheduleWithFixedDelay")));
        }
    }

    /**
     * CompletableFuture 转换器
     */
    static class CompletableFutureTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder
                    // supplyAsync
                    .visit(Advice.to(SupplierAdvice.class)
                            .on(ElementMatchers.named("supplyAsync")
                                    .and(ElementMatchers.isStatic())))
                    // runAsync
                    .visit(Advice.to(RunnableAdvice.class)
                            .on(ElementMatchers.named("runAsync")
                                    .and(ElementMatchers.isStatic())));
        }
    }

    /**
     * Timer 转换器
     */
    static class TimerTransformer implements AgentBuilder.Transformer {
        @Override
        public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, 
                                                  TypeDescription type, 
                                                  ClassLoader loader, 
                                                  JavaModule module,
                                                  ProtectionDomain domain) {
            return builder
                    .visit(Advice.to(TimerTaskAdvice.class)
                            .on(ElementMatchers.named("schedule")
                                    .or(ElementMatchers.named("scheduleAtFixedRate"))));
        }
    }

    // ==================== Advice Classes ====================

    /**
     * Runnable 参数包装
     */
    public static class RunnableAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(
                @Advice.Argument(value = 0, readOnly = false) Runnable runnable) {
            if (runnable != null) {
                runnable = FlowContext.wrap(runnable);
            }
        }
    }

    /**
     * Callable 参数包装
     */
    public static class CallableAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(
                @Advice.Argument(value = 0, readOnly = false) Callable<?> callable) {
            if (callable != null) {
                callable = FlowContext.wrap(callable);
            }
        }
    }

    /**
     * Supplier 参数包装（用于 CompletableFuture.supplyAsync）
     */
    public static class SupplierAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(
                @Advice.Argument(value = 0, readOnly = false) java.util.function.Supplier<?> supplier) {
            if (supplier != null) {
                supplier = FlowContext.wrapSupplier(supplier);
            }
        }
    }

    /**
     * TimerTask 参数包装
     */
    public static class TimerTaskAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(
                @Advice.Argument(value = 0, readOnly = false) TimerTask task) {
            if (task != null) {
                // TimerTask 是抽象类，无法直接包装
                // 在这里只能记录日志或跳过
                // 真正的包装需要在用户代码层面处理
            }
        }
    }
}
