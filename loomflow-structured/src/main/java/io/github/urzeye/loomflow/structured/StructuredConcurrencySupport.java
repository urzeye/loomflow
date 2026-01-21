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
package io.github.urzeye.loomflow.structured;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;

/**
 * 结构化并发兼容性支持层
 * <p>
 * 屏蔽 JDK 21 (Class-based policies) 与 JDK 25+ (Joiner-based) 的 API 差异
 * </p>
 */
final class StructuredConcurrencySupport {

    private static final MethodHandle OPEN_FAILURE_SCOPE_MH;
    private static final MethodHandle OPEN_SUCCESS_SCOPE_MH;

    // JDK 25 Joiner 句柄 (如果可用)
    private static final Object JOINER_FAILURE; // Joiner.awaitAllSuccessfulOrThrow()
    private static final Object JOINER_SUCCESS; // Joiner.awaitFirstSuccessfulOrThrow()

    private static final boolean IS_JDK25_STYLE;

    // 实例方法的方法句柄
    private static final MethodHandle FORK_MH;
    private static final MethodHandle JOIN_MH;
    private static final MethodHandle CLOSE_MH;

    // 原始 StructuredTaskScope 支持 (无特定策略，仅 fork/join)
    private static final MethodHandle OPEN_RAW_MH; // 如果是 JDK 21 则为 null (使用构造函数反射)
    private static final java.lang.reflect.Constructor<?> RAW_CONSTRUCTOR; // 如果是 JDK 25 则为 null

    static {
        MethodHandle failMh = null;
        MethodHandle successMh = null;
        Object joinerFail = null;
        Object joinerSuccess = null;
        boolean jdk25 = false;

        MethodHandle forkMh = null;
        MethodHandle joinMh = null;
        MethodHandle closeMh = null;

        MethodHandle openFailTemp = null;
        MethodHandle openSuccessTemp = null;

        MethodHandle rawMh = null;
        java.lang.reflect.Constructor<?> rawCtor = null;

        try {
            // 通用方法查找 (尝试在基类/接口上查找)
            // 在 JDK 21 中 StructuredTaskScope 是类，在 25 中是接口
            // 通常在实例的运行时类上查找，但这里扫描编译时 (JDK 21) 或运行时 (JDK 25) 可用的 StructuredTaskScope

            // 为了安全起见，在运行时可用的 StructuredTaskScope 类型上按名称查找方法
            // 注意：如果运行在 JDK 25 上，StructuredTaskScope 是接口
            // 针对 JDK 21 编译，它是一个类
            // 使用加载的类 "java.util.concurrent.StructuredTaskScope"
            Class<?> scopeType = StructuredTaskScope.class;

            try {
                // JDK 21 ShutdownOnFailure
                Class<?> failClass = Class.forName("java.util.concurrent.StructuredTaskScope$ShutdownOnFailure");
                failMh = MethodHandles.publicLookup().findConstructor(failClass, MethodType.methodType(void.class));

                Class<?> successClass = Class.forName("java.util.concurrent.StructuredTaskScope$ShutdownOnSuccess");
                successMh = MethodHandles.publicLookup().findConstructor(successClass, MethodType.methodType(void.class));

                // 对于 JDK 21，open 方法就是构造函数本身
                openFailTemp = failMh;
                openSuccessTemp = successMh;

                // JDK 21: 通过反射直接实例化 StructuredTaskScope
                // 以避免在源码中使用子类导致 JDK 25 编译失败 (extends Interface vs Class)
                // 构造函数是 protected 的：允许访问
                try {
                    rawCtor = scopeType.getDeclaredConstructor(String.class, java.util.concurrent.ThreadFactory.class);
                    rawCtor.setAccessible(true);
                } catch (NoSuchMethodException e) {
                }

            } catch (ClassNotFoundException e) {
                // JDK 25 路径
                jdk25 = true;
                Class<?> joinerClass = Class.forName("java.util.concurrent.StructuredTaskScope$Joiner");
                // 查找 joiner
                MethodHandle failJoinerMh = MethodHandles.publicLookup().findStatic(joinerClass,
                        "awaitAllSuccessfulOrThrow", MethodType.methodType(joinerClass));
                joinerFail = failJoinerMh.invoke();

                // Joiner.awaitFirstSuccessfulOrThrow()
                MethodHandle successJoinerMh = MethodHandles.publicLookup().findStatic(joinerClass,
                        "awaitFirstSuccessfulOrThrow", MethodType.methodType(joinerClass));
                joinerSuccess = successJoinerMh.invoke();

                // StructuredTaskScope.open(Joiner)
                MethodHandle openMh = MethodHandles.publicLookup().findStatic(scopeType,
                        "open", MethodType.methodType(scopeType, joinerClass));
                openFailTemp = openMh;
                openSuccessTemp = openMh;

                // JDK 25: StructuredTaskScope.open()
                rawMh = MethodHandles.publicLookup().findStatic(scopeType, "open", MethodType.methodType(scopeType));
            }

            // 在 Scope 类型上查找 fork/join/close
            forkMh = MethodHandles.publicLookup().findVirtual(scopeType, "fork",
                    MethodType.methodType(StructuredTaskScope.Subtask.class, Callable.class));

            // join 通常返回 scope 本身
            joinMh = MethodHandles.publicLookup().findVirtual(scopeType, "join",
                    MethodType.methodType(scopeType));

            closeMh = MethodHandles.publicLookup().findVirtual(scopeType, "close",
                    MethodType.methodType(void.class));

        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }

        FORK_MH = forkMh;
        JOIN_MH = joinMh;
        CLOSE_MH = closeMh;

        OPEN_FAILURE_SCOPE_MH = openFailTemp;
        OPEN_SUCCESS_SCOPE_MH = openSuccessTemp;

        JOINER_FAILURE = joinerFail;
        JOINER_SUCCESS = joinerSuccess;

        OPEN_RAW_MH = rawMh;
        RAW_CONSTRUCTOR = rawCtor;

        IS_JDK25_STYLE = jdk25;
    }

    /**
     * 统一的 Scope Handle 包装器
     */
    static class ScopeHandle implements AutoCloseable {
        // 底层的 StructuredTaskScope
        final Object rawScope;

        ScopeHandle(Object rawScope) {
            this.rawScope = rawScope;
        }

        @SuppressWarnings("unchecked")
        <T> StructuredTaskScope.Subtask<T> fork(Callable<? extends T> task) {
            try {
                return (StructuredTaskScope.Subtask<T>) FORK_MH.invoke(rawScope, task);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to fork task", e);
            }
        }

        void join() throws InterruptedException {
            try {
                JOIN_MH.invoke(rawScope);
            } catch (InterruptedException e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new RuntimeException("Failed to join scope", e);
            }
        }

        @Override
        public void close() {
            try {
                CLOSE_MH.invoke(rawScope);
            } catch (Throwable e) {
                throw new RuntimeException("Failed to close scope", e);
            }
        }
    }

    static ScopeHandle openFailureScope() {
        try {
            Object scope;
            if (!IS_JDK25_STYLE) {
                scope = OPEN_FAILURE_SCOPE_MH.invoke();
            } else {
                scope = OPEN_FAILURE_SCOPE_MH.invoke(JOINER_FAILURE);
            }
            return new ScopeHandle(scope);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to open failure scope", e);
        }
    }

    static ScopeHandle openSuccessScope() {
        try {
            Object scope;
            if (!IS_JDK25_STYLE) {
                scope = OPEN_SUCCESS_SCOPE_MH.invoke();
            } else {
                scope = OPEN_SUCCESS_SCOPE_MH.invoke(JOINER_SUCCESS);
            }
            return new ScopeHandle(scope);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to open success scope", e);
        }
    }

    static ScopeHandle openRawScope() {
        if (!IS_JDK25_STYLE) {
            // JDK 21 兼容方式：通过反射实例化
            if (RAW_CONSTRUCTOR == null) {
                throw new UnsupportedOperationException("Raw StructuredTaskScope cannot be instantiated on this JDK 21 variant.");
            }
            try {
                // name=null, factory=Thread.ofVirtual().factory()
                return new ScopeHandle(RAW_CONSTRUCTOR.newInstance(null, Thread.ofVirtual().factory()));
            } catch (ReflectiveOperationException e) {
                // 如果是抽象类，会抛出 InstantiationException
                throw new RuntimeException("Failed to instantiate raw StructuredTaskScope", e);
            }
        } else {
            try {
                return new ScopeHandle(OPEN_RAW_MH.invoke());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }
}
