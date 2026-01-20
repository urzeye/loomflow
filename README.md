# LoomFlow

[![Java](https://img.shields.io/badge/Java-25+-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> 下一代 Java 上下文管理库，面向 JDK 25+ 虚拟线程时代，零 Agent、Loom-First。

## ✨ 特性

- **零 Agent**：纯依赖库，无需 Java Agent，无侵入式改造
- **Loom-First**：基于 JDK 25 ScopedValue 设计，原生支持虚拟线程
- **类型安全**：泛型 ContextKey，编译时类型检查
- **声明式 API**：`FlowContext.with().run()` 链式调用，清晰优雅
- **自动传递**：线程池装饰器自动传递上下文，无需手动包装

## 📦 安装

### Maven

```xml
<dependency>
    <groupId>io.github.urzeye</groupId>
    <artifactId>loomflow</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 系统要求

- **JDK 25+**：推荐，ScopedValue 为正式特性
- **JDK 21-24**：需添加 `--enable-preview` 参数

## 🚀 快速开始

### 1. 定义上下文键

```java
import io.github.urzeye.loomflow.ContextKey;
import io.github.urzeye.loomflow.FlowContext;

// 定义类型安全的上下文键
public static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
public static final ContextKey<User> CURRENT_USER = ContextKey.of("currentUser");
public static final ContextKey<String> TENANT = ContextKey.of("tenant", "default");
```

### 2. 在作用域内运行代码

```java
FlowContext.with(TRACE_ID, "abc-123")
    .and(CURRENT_USER, user)
    .run(() -> {
        // 在任意位置获取上下文
        String traceId = FlowContext.get(TRACE_ID);
        User user = FlowContext.get(CURRENT_USER);
        
        processRequest();
    });

// 带返回值
String result = FlowContext.with(TRACE_ID, "abc-123")
    .call(() -> computeResult());
```

### 3. 线程池场景

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

// 方式一：装饰整个线程池（推荐）
ExecutorService contextAware = FlowContext.wrapExecutorService(executor);

FlowContext.with(TRACE_ID, "request-1").run(() -> {
    contextAware.submit(() -> {
        // 自动继承上下文
        String traceId = FlowContext.get(TRACE_ID); // "request-1"
    });
});

// 方式二：单次包装
executor.submit(FlowContext.wrap(() -> {
    String traceId = FlowContext.get(TRACE_ID);
}));
```

### 4. CompletableFuture

```java
FlowContext.with(TRACE_ID, "async-trace").run(() -> {
    // 使用 FlowContext 静态方法
    CompletableFuture<String> future = FlowContext.supplyAsync(() ->
        "Trace: " + FlowContext.get(TRACE_ID)
    );
});
```

### 5. 虚拟线程

```java
ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
ExecutorService wrapped = FlowContext.wrapExecutorService(virtualExecutor);

FlowContext.with(TRACE_ID, "virtual-thread-test").run(() -> {
    // 创建百万级虚拟线程，上下文依然正确传递
    for (int i = 0; i < 1_000_000; i++) {
        wrapped.submit(() -> {
            processWithContext();
        });
    }
});
```

## 🔧 API 参考

### ContextKey

| 方法 | 描述 |
|------|------|
| `ContextKey.of(name)` | 创建无默认值的键 |
| `ContextKey.of(name, default)` | 创建带默认值的键 |

### FlowContext

| 方法 | 描述 |
|------|------|
| `with(key, value)` | 开始创建作用域 |
| `get(key)` | 获取上下文值 |
| `getOrDefault(key, default)` | 获取值或默认值 |
| `isBound(key)` | 检查是否已绑定 |
| `wrap(Runnable)` | 包装任务 |
| `wrapExecutorService(executor)` | 包装线程池 |
| `supplyAsync(supplier)` | 创建上下文感知的 Future |

### FlowScope

| 方法 | 描述 |
|------|------|
| `and(key, value)` | 添加更多绑定 |
| `run(Runnable)` | 执行任务 |
| `call(Callable)` | 执行并返回结果 |

## 📋 与 TTL 对比

| 特性 | LoomFlow | TTL |
|------|----------|-----|
| Agent 依赖 | ❌ 不需要 | ✅ 可选 |
| JDK 版本 | 21+ | 8+ |
| 虚拟线程 | ✅ 原生支持 | ⚠️ 需要适配 |
| 内存占用 | 极低（ScopedValue） | 较高（ThreadLocal 拷贝） |
| 不可变性 | ✅ 天然不可变 | ❌ 可修改 |
| API 风格 | 声明式 | 命令式 |

## 🛣️ 路线图

- [x] 核心 API（FlowContext、ContextKey）
- [x] 线程池装饰器
- [x] CompletableFuture 支持
- [ ] MDC 桥接插件
- [ ] Spring 集成
- [ ] StructuredTaskScope 适配

## 📄 许可证

[Apache License 2.0](LICENSE)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

Made with ❤️ for the Java Loom era.
