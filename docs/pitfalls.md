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

## 9. P37: WebSocket/broker/session 代码保持不变

**原则**：Phase 3 扩展 RabbitMQ 通知和文件服务，不应修改 WebSocket broker 和 session 核心代码。WebSocket 实时通道继续使用 Redis Pub/Sub（或 Local fallback），消息发送的核心链路不受影响。

**经验**：新增横向功能（通知、文件）时，通过领域事件解耦，避免侵入已有的实时通信链路。

## 10. P38: Micrometer Gauge 监控异步线程池队列

**坑**：message-service 使用 `CompletableFuture.runAsync` 异步持久化消息，如果线程池队列积压，消息可能迟迟未写入 MySQL。没有监控就无法发现这个问题。

**解决**：在 `RabbitMQConfig` 中注册 `MeterBinder`，暴露 `async.executor.queue.size` Gauge。Prometheus + Grafana 可以设置告警阈值。

**注意**：`@Qualifier("messagePersistenceExecutor")` 注入的是 `ExecutorService`（原生 ThreadPoolExecutor），不是 Spring 的 `ThreadPoolTaskExecutor`，需要 `instanceof` 判断后强转。

## 11. P39: Jackson2JsonMessageConverter 双端必须一致

**坑**：如果 producer 用 Jackson2JsonMessageConverter 序列化，但 consumer 没有配置该 converter，RabbitMQ 会尝试用 `SimpleMessageConverter` 反序列化，导致 `ClassCastException` 或反序列化失败。

**解决**：producer 端（message-service、file-service）和 consumer 端（notification-service）都配置 `Jackson2JsonMessageConverter` Bean。Spring Boot AutoConfiguration 会检测到该 Bean 并自动装配到 `RabbitListenerContainerFactory`。

## 12. P40: Publisher Confirms + CorrelationData

**坑**：RabbitMQ 默认不开启 publisher confirms。如果 exchange 不存在或消息无法路由，producer 无从得知，事件会静默丢失。

**解决**：`CachingConnectionFactory.setPublisherConfirmType(CORRELATED)` + 每条消息绑定 `CorrelationData(eventId)`。通过 `cd.getFuture().whenComplete(...)` 异步监听 ACK/NACK，NACK 时打印 warn 日志。

**注意**：Publisher confirms 是异步的，不应阻塞调用线程。只记录日志，由运维人员或告警系统关注。

## 13. P41: MinIO Bucket 自动创建

**坑**：MinIO 首次启动时 bucket 不存在，`io.minio.errors.ErrorResponseException: The specified bucket does not exist`。

**解决**：`MinioConfig.@PostConstruct ensureBucketExists()` 在服务启动时调用 `bucketExists()` + `makeBucket()`，确保 bucket 一定存在。幂等操作，多次执行无副作用。

## 14. P42: MinIO Pre-signed URL 使用 localhost

**坑**：MinIO 生成 pre-signed URL 时默认使用请求的 Host 头。如果服务通过 Docker 内部网络访问 MinIO（如 `http://minio:9000`），生成的 URL 会包含 `minio:9000`，外部客户端无法访问。

**解决**：设置 `MINIO_SERVER_URL=http://localhost:9000` 环境变量，让 MinIO 在签名 URL 中使用 localhost。也可以通过 `minio.endpoint` 配置项显式指定端点。

## 15. P43: Multipart 文件大小限制

**坑**：Spring Boot 默认 `spring.servlet.multipart.max-file-size=1MB`。如果不配置，上传超过 1MB 的文件会抛 `MaxUploadSizeExceededException`。

**解决**：`application.yml` 中设置 `max-file-size: 50MB` 和 `max-request-size: 55MB`（预留 5MB 给其他表单字段）。同时 FileService 中也做硬校验（≤ 52428800 bytes）作为双重保障。

## 16. P44: Notification 去重 TTL

**坑**：RabbitMQ 的 at-least-once 语义 + consumer 重试可能导致同一事件被重复消费，产生重复通知。

**解决**：Redis `SET NX notif:dedup:{eventId}` + TTL=86400s（24小时）。业务上，同一个 eventId 的通知只插入一次。

**为什么是 86400s**：通知是异步生成的非实时数据，去重窗口覆盖一整天足以处理大部分重试和网络抖动。比消息去重（30s）更长，因为通知的重试间隔可能更长（DLX 延迟）。

## 17. P45: 文件扩展名白名单

**坑**：不限制上传文件类型会导致安全问题（如上传 `.jsp`、`.exe`、`.sh` 恶意文件）和存储浪费。

**解决**：双重校验 — MIME 类型 + 扩展名白名单：
- MIME: `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `application/pdf`, `text/plain`, `application/zip`
- 扩展名: `jpg`, `jpeg`, `png`, `gif`, `webp`, `pdf`, `txt`, `zip`

**注意**：MIME 类型来自客户端 `Content-Type` 头，可被伪造。扩展名白名单是兜底校验。

## 18. P46: MinIO OkHttpClient 连接池

**坑**：MinIO SDK 默认 OkHttpClient 连接池只有 5 个连接，高并发上传时连接不够用，请求排队等待。

**解决**：自定义 OkHttpClient，`ConnectionPool(50, 5, MINUTES)`，connect timeout 30s，read/write timeout 60s。通过 `MinioClient.builder().httpClient(httpClient)` 传入。

## 19. P47: MinIO Region 显式设置

**坑**：MinIO 默认 region 为空字符串。某些 MinIO 版本或部署模式下，缺少 region 会导致 API 调用报错（如 `The authorization mechanism you have provided is not supported`）。

**解决**：`minio.region=us-east-1`（AWS S3 兼容的默认 region），配置在 `application.yml` 并通过 `@Value` 注入，`MinioClient.builder().region(region)` 显式设置。

## 20. P48: MinIO Bucket Lifecycle 自动过期

**坑**：文件只上传不删除，存储成本持续增长。手工清理不可靠。

**解决**：`ensureBucketExists()` 中设置 90 天过期策略：`new Expiration(null, 90, null)`，对象创建 90 天后自动删除。适合协作平台的文件临时存储场景。
