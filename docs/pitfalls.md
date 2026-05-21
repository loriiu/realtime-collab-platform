# 踩坑记录

## 1. Spring Cloud Gateway WebSocket 路由

**坑**：Gateway 默认不会自动转发 WebSocket 升级请求，需要显式配置 `lb:ws://` 前缀。

**解决**：在 `application.yml` 中添加：
```yaml
- id: message-service-ws
  uri: lb:ws://message-service
  predicates:
    - Path=/ws/**
```

**注意**：uri 使用 `lb:ws://` 而非 `lb://`，这是 Spring Cloud Gateway 对 WebSocket 负载均衡的特殊处理。

## 2. 获取 WebSocket Session 中的用户 ID

**坑**：Gateway 的 AuthGlobalFilter 是基于 WebFlux 的 `ServerWebExchange`，而 WebSocket 使用的是 Servlet 体系。Gateway 的 filter 链不会对 WebSocket 升级请求的后续消息生效。

**解决**：在 WebSocket 握手阶段通过 `JwtHandshakeInterceptor` 从 URI query parameter 中提取 token 并验证。WebSocket 连接建立后，后续消息不再经过 Gateway filter。

## 3. WebSocket 线程不应调 Redis

**坑**：WebSocket IO 线程（Netty worker）如果同步调用 Redis（网络 IO），会导致线程阻塞，影响其他连接的吞吐量。

**解决**：
- 除 `SETNX`（非阻塞单次操作，延迟极低）外，不在 WebSocket 线程中调 Redis
- 消息持久化使用 `CompletableFuture.runAsync` + 独立线程池
- 在线状态更新使用 Redis 异步操作

## 4. @ConditionalOnBean 与 Bean 初始化顺序

**坑**：`@ConditionalOnBean(RedisMessageSubscriber.class)` 依赖 RedisMessageSubscriber 先初始化。如果 Redis 不可用或配置错误，该 Bean 可能不会创建，导致 `LocalMessageBroker` 被选中。

**解决**：这是预期行为。单机模式不需要 Pub/Sub，`LocalMessageBroker`（no-op）是合理的降级方案。

## 5. Feign 调用返回类型不一致

**坑**：`UserFeignClient.batchGetUserInfo` 调用 user-service 的 `/user/batch` 接口，但 user-service 的 `UserController` 中可能还没有这个端点。当前 Phase 1 的 user-service 只有 `/user/info` 端点。

**解决**：需要在 user-service 中添加 `/user/batch` 端点。或通过 Fallback 机制返回兜底数据（nickname="未知用户"）。

## 6. Flyway 迁移文件版本号

**坑**：Flyway 使用严格排序，V2 和 V3 之间的版本号冲突或者 V1 已被 Phase 1 使用。

**解决**：V2 和 V3 从 Phase 2 开始，Phase 1 如果使用了 V1 迁移脚本，需要确认 V1 存在。Flyway 的 `baseline-on-migrate: true` 可以处理已有数据库的情况。

## 7. Message 默认值

**坑**：Lombok `@Data` 不会设置字段默认值。在 Java 中，类字段的默认值（如 `private String type = "TEXT"`）需要在字段声明时赋值。

**解决**：在 `MessageService.sendMessage()` 中显式设置默认值，不依赖实体类的默认值。

## 8. WebSocket Token 在 URL query 中的安全风险

**坑**：浏览器 `WebSocket` API 无法设置自定义 HTTP Header，因此 JWT token 只能通过 URL query parameter 传递（`ws://host/ws?token=xxx`）。这导致 token 明文出现在以下位置：
- Nginx / Gateway access log（默认记录完整 URI 包括 query string）
- 浏览器地址栏和 history
- 日志平台（ELK / Splunk / 阿里云 SLS）
- Referer 头（如果页面有 `<img>` 等资源请求）

**当前阶段（Phase 2）**：
- 这是权衡后的选择——浏览器 API 限制 + 学生项目复杂度约束
- 至少使用了 JWT + HandshakeInterceptor 校验（比裸奔强）
- 如果面试被追问，可以明确说出这个 trade-off，展示安全意识

**生产环境改进方案（Phase 3+）**：
| 方案 | 做法 | 适用 |
|------|------|------|
| Cookie-based | `Set-Cookie: ws_token=xxx; HttpOnly; Secure; SameSite=Strict`，WebSocket 连接自动携带 | Web 端最优方案 |
| Short-lived ticket | POST /ws/ticket 用 token 换 30s 有效的一次性 ticket，`ws://host/ws?ticket=xxx` | 非 Web 端（App/小程序） |
| First-message auth | WebSocket 先匿名连接，第一条消息发 `{type:"auth", token:"xxx"}` | 最安全但实现复杂度高 |

**参考**：OWASP 明确将 "Bearer token in URL" 列为反模式。
