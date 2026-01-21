# LoomFlow Roadmap

## 📊 项目现状

| 模块 | 功能 | 状态 |
|------|------|------|
| `loomflow-core` | FlowContext, ContextKey, ContextCarrier | ✅ 完善 |
| `loomflow-agent` | 线程池/CompletableFuture 增强 | ✅ 基本可用 |
| `loomflow-structured` | FlowTaskScope, FlowTasks, 超时控制 | ✅ 完善 |
| `loomflow-spring-boot-starter` | TaskExecutor/@Async 增强 | ✅ 完善 |
| `loomflow-integrations` | MDC/OpenTelemetry 桥接 | ⚠️ 需验证 |

---

## ⚠️ 当前不足

1. **测试覆盖不全** - Agent Bootstrap 增强需 `-javaagent`，Integrations 缺少测试
2. **生态集成待验证** - OTel 桥接未经实战，缺少 gRPC/WebFlux 支持
3. **性能基准缺失** - 无 TTL/ScopedValue 对比数据
4. **文档不完整** - 缺少 Javadoc 和英文文档

---

## 🚀 开发计划

### 短期 (1-2 周)

- [ ] 性能基准测试（TTL vs ThreadLocal vs ScopedValue）
- [ ] MDC/OTel 集成测试
- [ ] Javadoc 完善与发布

### 中期 (1-2 月)

- [ ] 响应式支持（WebFlux/Reactor 上下文传递）
- [ ] gRPC 集成（Interceptor 自动传递）
- [ ] 异常传播增强（子任务异常附加上下文信息）

### 长期（探索性）

- [ ] JFR 事件发布（可观测性增强）
- [ ] Kotlin Coroutine 支持
- [ ] Virtual Thread Pin 检测集成

---

## 版本规划

| 版本 | 目标 |
|------|------|
| 0.4.0 | 性能基准 + MDC/OTel 验证 |
| 0.5.0 | 响应式/gRPC 支持 |
| 1.0.0 | 稳定版，API 冻结 |
