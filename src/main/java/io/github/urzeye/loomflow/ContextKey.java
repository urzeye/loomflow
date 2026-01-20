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
package io.github.urzeye.loomflow;

import java.util.Objects;
import java.util.Optional;

/**
 * 类型安全的上下文键。
 * <p>
 * 用于在 {@link FlowContext} 中存取特定类型的值，提供编译时类型检查。
 * </p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 定义上下文键
 * public static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
 * public static final ContextKey<User> CURRENT_USER = ContextKey.of("currentUser", User.ANONYMOUS);
 *
 * // 使用上下文键
 * FlowContext.with(TRACE_ID, "abc-123").run(() -> {
 *     String traceId = FlowContext.get(TRACE_ID);
 * });
 * }</pre>
 *
 * @param <T> 上下文值的类型
 * @author urzeye
 * @since 0.1.0
 */
public final class ContextKey<T> {

    private final String name;
    private final T defaultValue;
    private final ScopedValue<T> scopedValue;

    private ContextKey(String name, T defaultValue) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.defaultValue = defaultValue;
        this.scopedValue = ScopedValue.newInstance();
        // 自动注册到 ContextCarrier，使其可以被捕获
        ContextCarrier.register(this);
    }

    /**
     * 创建一个没有默认值的上下文键。
     *
     * @param name 键名称，用于调试和日志
     * @param <T>  值类型
     * @return 新的上下文键
     */
    public static <T> ContextKey<T> of(String name) {
        return new ContextKey<>(name, null);
    }

    /**
     * 创建一个带有默认值的上下文键。
     *
     * @param name         键名称，用于调试和日志
     * @param defaultValue 当上下文中没有绑定值时返回的默认值
     * @param <T>          值类型
     * @return 新的上下文键
     */
    public static <T> ContextKey<T> of(String name, T defaultValue) {
        return new ContextKey<>(name, defaultValue);
    }

    /**
     * 获取键名称。
     *
     * @return 键名称
     */
    public String name() {
        return name;
    }

    /**
     * 获取默认值。
     *
     * @return 默认值的 Optional 包装
     */
    public Optional<T> defaultValue() {
        return Optional.ofNullable(defaultValue);
    }

    /**
     * 检查是否有默认值。
     *
     * @return 如果有默认值返回 true
     */
    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    /**
     * 获取底层的 ScopedValue 实例。
     * <p>
     * 这是一个内部 API，不建议直接使用。
     * </p>
     *
     * @return ScopedValue 实例
     */
    ScopedValue<T> scopedValue() {
        return scopedValue;
    }

    @Override
    public String toString() {
        return "ContextKey[" + name + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextKey<?> that = (ContextKey<?>) o;
        // 每个 ContextKey 实例都是唯一的，使用引用相等
        return this == that;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
