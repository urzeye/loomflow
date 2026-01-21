# LoomFlow

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)

`LoomFlow` 是面向 JDK 21+ 虚拟线程（Project Loom）场景的上下文管理框架。它基于 `ScopedValue` 和 `StructuredTaskScope` 标准 API 构建，旨在解决虚拟线程在线程池复用场景下的上下文（Context）传递与保持问题。

## 📖 简介

在 JDK 21 引入虚拟线程后，虽然依然存在线程池复用的场景（如 `ExecutorService` 或旧有代码迁移），但传统的 `ThreadLocal` 方案存在内存泄漏风险且不支持结构化并发，而原生的 `ScopedValue` 仅支持在词法作用域内传递，无法直接穿透线程池。

`LoomFlow` 提供了一套完整的解决方案，通过 Java Agent 低侵入地增强 JDK 核心类，实现上下文在线程池、异步任务中的透明传递，并天然支持结构化并发模式。

### 核心功能

* **透明上下文传递**: 无需修改业务代码，自动在 `ExecutorService`, `CompletableFuture`, `ForkJoinPool` 中传递上下文。
* **ScopedValue 原生**: 基于 JEP 429/446 标准，性能优于 `ThreadLocal`，零拷贝开销。
* **结构化并发增强**: 扩展 `StructuredTaskScope`，子任务自动继承父作用域上下文。
* **生态集成**: 提供 Spring Boot Starter、SLF4J MDC 桥接、OpenTelemetry 支持。

---

## 💡 设计哲学

### 为什么不是 set/get？

如果你熟悉 [TTL (transmittable-thread-local)](https://github.com/alibaba/transmittable-thread-local)，可能会疑惑：为什么 LoomFlow 需要 `with().run()` 的写法，而不是简单的 `set()`/`get()`？

```java
// TTL 风格
TRANSMITTABLE_TL.set("value");
String v = TRANSMITTABLE_TL.get();
TRANSMITTABLE_TL.remove();  // ⚠️ 忘记调用 = 内存泄漏

// LoomFlow 风格 (遵循 ScopedValue 设计)
FlowContext.with(KEY, "value").run(() -> {
    String v = FlowContext.get(KEY);
}); // ✅ 自动清理，不可能泄漏
```

**这是有意为之的设计**，源自 JDK ScopedValue 的核心理念：

| 特性 | ThreadLocal/TTL | ScopedValue/LoomFlow |
|------|-----------------|----------------------|
| 生命周期 | 隐式（需手动 remove） | 显式（词法作用域自动管理） |
| 内存泄漏风险 | ⚠️ 高 | ✅ 无 |
| 结构化并发 | ❌ 不支持 | ✅ 天然支持 |
| 性能 | 一般 | 更优（栈分配，零拷贝） |

### 最佳实践：场景化封装

虽然核心 API 需要显式传递 `ContextKey`，但你可以按业务领域封装便捷工具类：

```java
public class RequestContext {
    private static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");
    private static final ContextKey<String> USER_ID = ContextKey.of("userId");
    
    // 语义化的 getter，无需传 Key
    public static String traceId() { return FlowContext.get(TRACE_ID); }
    public static String userId() { return FlowContext.get(USER_ID); }
    
    // 便捷的作用域构建器
    public static FlowScope forRequest(String traceId, String userId) {
        return FlowContext.with(TRACE_ID, traceId).with(USER_ID, userId);
    }
}

// 使用体验接近 TTL，但保持 ScopedValue 的安全性
RequestContext.forRequest("abc-123", "user-1").run(() -> {
    log.info("trace={}, user={}", RequestContext.traceId(), RequestContext.userId());
});
```

> **建议**：每个微服务定义自己的 `RequestContext` / `SecurityContext` / `TenantContext`，既保持类型安全，又简化调用。

---

## 📦 依赖引入

推荐使用 BOM 进行版本管理：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.urzeye</groupId>
            <artifactId>loomflow-bom</artifactId>
            <version>0.3.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 1. 核心库 (Required)

```xml
<dependency>
    <groupId>io.github.urzeye</groupId>
    <artifactId>loomflow-core</artifactId>
</dependency>
```

### 2. 生态扩展 (Optional)

```xml
<!-- 结构化并发增强 -->
<dependency>
    <artifactId>loomflow-structured</artifactId>
</dependency>

<!-- Spring Boot 自动配置 -->
<dependency>
    <artifactId>loomflow-spring-boot-starter</artifactId>
</dependency>

<!-- 日志与链路追踪集成 -->
<dependency>
    <artifactId>loomflow-integrations</artifactId>
</dependency>
```

---

## 🚀 快速集成

### 方式一：Java Agent 透明增强 (推荐)

在启动命令中添加 Agent 参数，即可实现全自动的上下文传递，无需手动包装 `Runnable`/`Callable`。

```bash
java -javaagent:/path/to/loomflow-agent.jar -jar your-app.jar
```

**支持的组件：**

* `java.util.concurrent.ThreadPoolExecutor`
* `java.util.concurrent.ScheduledThreadPoolExecutor`
* `java.util.concurrent.ForkJoinPool`
* `java.util.concurrent.CompletableFuture` (`supplyAsync`, `runAsync`)

> **Note**: 对于 Spring Boot 应用，Agent 方式配合 Starter 使用效果最佳。

### 方式二：手动 API (无 Agent)

如果不便使用 Agent，也可以通过 API 手动包装任务：

```java
// 1. 定义 ContextKey
static final ContextKey<String> TRACE_ID = ContextKey.of("traceId");

// 2. 也是 ScopedValue 的标准用法
FlowContext.with(TRACE_ID, "uuid-1234").run(() -> {
    
    // 手动包装任务以跨线程传递
    executor.submit(FlowContext.wrap(() -> {
        String id = FlowContext.get(TRACE_ID);
        System.out.println("TraceId: " + id);
    }));
    
});
```

---

## 🧩 进阶使用

### 1. 结构化并发 (Structured Concurrency)

LoomFlow 扩展了 `StructuredTaskScope`，解决了原生 API 在 `fork` 时无法自动继承父线程 `ScopedValue` 的限制（注：原生 ScopedValue 仅在同一个 Thread 或通过 `ScopedValue.Carrier` 显式传递）。

```java
// 使用工厂方法创建作用域（兼容 JDK 21 和 JDK 25）
try (var scope = FlowTaskScope.shutdownOnFailure()) {
    // fork 的子任务自动继承当前 FlowContext
    scope.fork(() -> fetchDataA());
    scope.fork(() -> fetchDataB());
    
    scope.join();
    scope.throwIfFailed();
}

// 或者使用便捷 API
List<String> results = FlowTasks.invokeAll(task1, task2);
```

**超时控制**：

```java
// 方式 1: FlowTaskScope
scope.join(Duration.ofSeconds(5)); // 超时 5 秒，抛出 TimeoutException

// 方式 2: FlowTasks
List<String> results = FlowTasks.invokeAll(Duration.ofSeconds(5), task1, task2);
String fastest = FlowTasks.invokeAny(Duration.ofSeconds(5), task1, task2);
```

### 2. Spring Boot 集成

引入 `loomflow-spring-boot-starter` 后，提供如下开箱即用的能力：

* **TaskExecutor 增强**: 自动装饰容器中的 `TaskExecutor` Bean。
* **@Async 支持**: 拦截 `@Async` 注解方法，透明传递上下文。

配置项 (`application.yml`):

```yaml
loomflow:
  enabled: true
  wrap-task-executor: true # 默认为 true
  wrap-async: true         # 默认为 true
```

### 3. MDC 与 Trace 集成 (Auto SPI)

LoomFlow 利用 Java SPI 机制实现了对第三方上下文的自动传递。只需引入 `loomflow-integrations` 依赖，以下上下文将自动跨线程传递，**无需任何手动配置或代码修改**：

* **SLF4J MDC**: 自动捕获主线程 MDC，传递由于线程池，并在任务结束时清理。
* **OpenTelemetry**: 自动传递当前的 OpenTelemetry Clip Context。

```xml
<dependency>
    <artifactId>loomflow-integrations</artifactId>
</dependency>
```

**效果：**

```java
MDC.put("traceId", "abc-123");
executor.submit(() -> {
     // 自动生效！无需 MdcBridge.wrap()
     logger.info("Business processing..."); // log 包含 traceId: abc-123
});
```

---

## ⚠️ 已知限制

### java.util.Timer

由于 `Timer` 内部实现机制（单线程死循环处理队列，缺乏扩展点），Agent 无法在不破坏 `cancel()` 语义的前提下实现透明增强。

**建议方案**：

1. **推荐**：使用 `ScheduledThreadPoolExecutor` 替代 `Timer`（Agent 已完美支持）。
2. **兼容**：如果必须使用，需手动包装并注意取消操作的对象：

```java
TimerTask wrapped = FlowContext.wrap(originTask);
timer.schedule(wrapped, 1000);

// WRONG: originTask.cancel(); // 无效
// RIGHT: wrapped.cancel();    // 有效
```

---

## 关于

本项目参考了 [Alibaba/transmittable-thread-local](https://github.com/alibaba/transmittable-thread-local) 的设计思想，将其理念适配到 JDK 21+ 的虚拟线程与 ScopedValue 生态中。

> **LoomFlow 不需要成为下一个 TTL，而应该成为 “ScopedValue 时代的 TTL”。**
>
> 重点不应是兼容所有老旧场景（那交给 TTL 就好），而是解决 **虚拟线程池化** 和 **结构化并发跨线程** 这两个核心新痛点。

### License

[Apache 2.0 License](LICENSE)
