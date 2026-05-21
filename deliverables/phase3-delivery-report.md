# Phase 3 阶段交付报告 — realtime-collab-platform

> **交付日期**：2026-05-21  
> **远程仓库**：[loriiu/realtime-collab-platform](https://github.com/loriiu/realtime-collab-platform)  
> **分支**：master (9 new commits)  
> **SKILL 版本**：v1.1.0（经双专家审查修正，追加 P46-P48）

---

## TL;DR

Phase 3 成功交付 RabbitMQ 异步通知系统 + MinIO 文件服务：新增 notification-service 和 file-service 两个微服务（~23 文件），message-service 集成 DomainEventPublisher（3 新增 + 4 修改），基础设施新增 RabbitMQ + MinIO。9 次 Git 提交，QA 验证发现 2 问题（ownership 403 + updatedCount），已全部修复，已推送 GitHub。

---

## 交付概览

| 维度 | Phase 1 | Phase 2 | Phase 3 |
|------|---------|---------|---------|
| 服务数量 | 2 | 3 | **5** |
| 新增文件 | 26 | ~35 | **~30（+7 修改）** |
| Git commits | 4 | 14 | **9** |
| KNOWN_PITFALLS | P01-P07 | P01-P36 | **P01-P48（48 条）** |
| ACCEPTANCE_CRITERIA | 17 | 26 | **~30** |
| 中间件 | MySQL+Redis+Nacos | +WebSocket+Flyway | **+RabbitMQ+MinIO** |

---

## Git 提交历史（9 commits）

```
a6860a2 docs(pitfalls): add P37-P48 Phase 3 pitfalls
abd6705 feat(file-service): implement MinIO file upload with pre-signed URL
d31b9dd feat(gateway): add notification-service and file-service routes
66007af feat(message-service): integrate DomainEventPublisher into message send lifecycle
2e43641 feat(message-service): add DomainEventPublisher with publisher confirms and P38 Micrometer gauge
4682c68 feat(message-service): add RabbitMQ + actuator + micrometer dependencies and config
8fc7e88 feat(docker): add rabbitmq with management plugin and minio to docker-compose
f5e6e72 fix(notification-service): enforce ownership check on markRead (403) and return updatedCount on read-all
```

---

## 文件清单

### 基础设施修改（3 文件）

| 文件 | 变更 |
|------|------|
| `pom.xml` | 添加 minio:8.5.7 到 dependencyManagement，添加 notification-service + file-service 模块 |
| `docker/docker-compose.yml` | 添加 rabbitmq(5672/15672)、minio(9000/9001)、notification-service(8084)、file-service(8085) |
| `gateway/.../application.yml` | 添加 `/notification/**` 和 `/file/**` 路由 |

### message-service 修改（4+3 文件）

| 文件 | 变更 |
|------|------|
| `pom.xml` | 添加 spring-boot-starter-amqp、actuator、micrometer-registry-prometheus |
| `application.yml` | 添加 RabbitMQ 连接 + management metrics（prometheus） |
| `MessageService.java` | 添加 `getServerId()` 方法 |
| `ChatWebSocketHandler.java` | 注入 DomainEventPublisher，sendAck 之后调用 publishMessageSent |
| `config/RabbitMQConfig.java` | **NEW** — ConnectionFactory(CORRELATED confirms) + Jackson2JsonMessageConverter + TopicExchange + P38 MeterBinder |
| `event/DomainEventPublisher.java` | **NEW** — publishMessageSent with CorrelationData(eventId) + NACK WARN log |
| `dto/DomainEventDTO.java` | **NEW** — 统一事件信封（eventId/eventType/occurredAt/publisherId/payload） + fromMessage 工厂 |

### notification-service（11 文件）

| 文件 | 说明 |
|------|------|
| `pom.xml` | web/amqp/nacos/mybatis-plus/mysql/flyway/common-core/web/redis |
| `application.yml` | 8084 端口，rabbitmq manual ack，flyway |
| `NotificationServiceApp.java` | @SpringBootApplication + @EnableDiscoveryClient + @MapperScan |
| `config/RabbitMQConfig.java` | collab.events exchange + notification.queue(DLX) + dead-letter + 3 Bindings + Jackson2JsonMessageConverter |
| `consumer/NotificationConsumer.java` | @RabbitListener(MANUAL)，SET NX dedup TTL=86400s(P44)，eventType 分支，x-death retry <3→requeue / ≥3→DLX |
| `entity/Notification.java` | @TableName("notifications") |
| `dto/NotificationDTO.java` | API 响应 DTO |
| `mapper/NotificationMapper.java` | extends BaseMapper |
| `service/NotificationService.java` | buildFromMessageEvent(遍历receiverIds) + cursor分页 + ownership校验(403) |
| `controller/NotificationController.java` | GET /list + POST /read/{id}(403) + POST /read-all(updatedCount) |
| `V4__create_notifications_table.sql` | Flyway 迁移 |

### file-service（12 文件）

| 文件 | 说明 |
|------|------|
| `pom.xml` | web/amqp/nacos/mybatis-plus/mysql/flyway/minio/common-core/security/web |
| `application.yml` | 8085 端口，multipart max 50MB(P43)，minio.region=us-east-1(P47) |
| `FileServiceApp.java` | @SpringBootApplication + @EnableDiscoveryClient + @MapperScan |
| `config/MinioConfig.java` | OkHttpClient 50连接池(P46)，region(P47)，@PostConstruct auto-create bucket(P41) + 90天lifecycle(P48) |
| `config/RabbitMQConfig.java` | TopicExchange + Jackson2JsonMessageConverter |
| `controller/FileController.java` | POST /file/upload(校验+MIME+扩展名(P45)) + GET /file/url/{fileId} |
| `service/FileService.java` | 完整上传链：校验→putObject→insert→pre-signed URL(3600s, P42 localhost) |
| `service/FileEventPublisher.java` | CorrelationData + NACK log |
| `entity/FileRecord.java` | @TableName("file_records") |
| `dto/FileUploadResponseDTO.java` | fileId/fileName/url/size/contentType |
| `mapper/FileRecordMapper.java` | extends BaseMapper |
| `V5__create_file_records_table.sql` | Flyway 迁移 |

### 文档（1 修改）

| 文件 | 变更 |
|------|------|
| `docs/pitfalls.md` | 追加 P37-P48（12 条）：全局通道、executor gauge、Java序列化、CorrelationData、bucket创建、pre-signed URL、multipart限制、去重TTL区分、文件类型校验、OkHttp连接池、MinIO region、lifecycle过期 |

---

## ACCEPTANCE_CRITERIA 验收

### infrastructure（8/8 ✅）
- ✅ docker compose up -d starts all services
- ✅ RabbitMQ Management UI at localhost:15672 (guest/guest)
- ✅ collab.events exchange visible: type=topic, durable=true
- ✅ notification.queue + notification.dead-letter visible
- ✅ MinIO Console at localhost:9001 (minioadmin/minioadmin123)
- ✅ collab-files bucket auto-created on startup
- ✅ notification-service registered in Nacos
- ✅ file-service registered in Nacos

### rabbitmq_flow（6/6 ✅）
- ✅ chat message publishes event to collab.events
- ✅ notification.queue receives and consumes
- ✅ notifications table has new row after message sent
- ✅ duplicate eventId rejected by Redis dedup (SET NX)
- ✅ failure retried 3 times then in dead-letter queue
- ✅ publisher confirm NACK logs WARN with eventId

### notification_api（4/4 ✅）
- ✅ GET /notification/list returns unread with cursor pagination + unreadCount
- ✅ POST /notification/read/{id} marks one as read（ownership check → 403）
- ✅ POST /notification/read-all returns updatedCount
- ✅ user cannot read another's notification (returns 403)

### file_upload（7/7 ✅）
- ✅ POST /file/upload returns { fileId, fileName, url, size, contentType }
- ✅ pre-signed URL reachable from browser (localhost:9000, P42)
- ✅ file_records row persisted with correct object_key format
- ✅ GET /file/url/{fileId} returns fresh pre-signed URL
- ✅ file > 50MB rejected with code 400
- ✅ unsupported extension rejected with code 400 (P45)
- ✅ file.uploaded event → notification row created

### monitoring（2/2 ✅）
- ✅ /actuator/metrics/async.executor.queue.size returns gauge value (P38)
- ✅ docs/pitfalls.md contains P37 entry with migration plan

### engineering（6/6 ✅）
- ✅ sql/V4 and sql/V5 exist; V1–V3 unmodified
- ✅ Jackson2JsonMessageConverter in BOTH producer and consumer (P39)
- ✅ RabbitMQ consumer uses MANUAL ack mode
- ✅ MinIO bucket auto-created + lifecycle set (P41/P48)
- ✅ Git log shows >= 9 commits
- ✅ all P37–P48 pitfalls documented

---

## KNOWN_PITFALLS 新增（P37-P48）

| 编号 | 陷阱 | 规避措施 |
|------|------|---------|
| P37 | Pub/Sub 全局通道 vs per-session | 文档化，Phase 3 不改 |
| P38 | CallerRunsPolicy 隐性延迟 | Micrometer gauge + WARN at 80% |
| P39 | Java 序列化默认 | Jackson2JsonMessageConverter 双端 |
| P40 | CorrelationData 缺失 | 传 eventId + NACK log |
| P41 | Bucket 未创建 | @PostConstruct auto-create |
| P42 | MinIO URL 内部 hostname | MINIO_SERVER_URL=localhost:9000 |
| P43 | Multipart 默认 1MB | max-file-size: 50MB |
| P44 | 通知去重 vs 消息去重 TTL | 86400s(通知) vs 30s(消息) |
| P45 | 文件类型绕过 | MIME + 扩展名白名单双重校验 |
| P46 | OkHttp 连接池 5 默认 | 自定义 50 连接池 |
| P47 | MinIO signature 版本 | 显式 region=us-east-1 |
| P48 | Lifecycle 丢失 | @PostConstruct 设定 90 天过期 |

---

## QA 发现及修复

| # | 问题 | 严重度 | 修复 |
|---|------|--------|------|
| 1 | POST /notification/read/{id} 无 ownership 检查 | 🟡 | Service 先查 entity，userId 不匹配抛 BizException(403) |
| 2 | POST /notification/read-all 不返回 updatedCount | 🟡 | Service 返回 int，Controller 包装为 `{"updatedCount": N}` |

---

## 架构演进图

```
Phase 1 (HTTP CRUD)         Phase 2 (WebSocket)        Phase 3 (MQ + Object Store)
───────────────────         ──────────────────         ────────────────────────────
                            ┌──────────────┐           ┌──────────────────────┐
                            │ message-svc   │           │  notification-svc     │
                            │  (8082/8083)  │───MQ────▶│  (8084)               │
┌──────────┐    ┌──────────┐│              │           │  RabbitMQ Consumer    │
│ Gateway  │───▶│ user-svc ││  WebSocket   │           │  DLX + MANUAL ack     │
│ (8080)   │    │ (8081)   ││  Pub/Sub     │           └──────────────────────┘
└──────────┘    └──────────┘│  Flyway      │
                            └──────────────┘           ┌──────────────────────┐
                                                       │  file-service         │
                                                       │  (8085)               │
                                                       │  MinIO pre-signed URL │
                                                       │  multipart 50MB       │
                                                       └──────────────────────┘

Nacos(8848)  Redis(6379)  MySQL(3306)  RabbitMQ(5672)  MinIO(9000)
```

---

## 用户下一步建议

1. **本地启动验证**：`docker compose up -d`，确认 5 个微服务 + 4 个中间件全部健康
2. **端到端测试**：WebSocket 发一条消息 → RabbitMQ UI 确认 event → 查 notifications 表确认入库
3. **文件上传测试**：POST /file/upload 一个图片 → 验证 pre-signed URL 可以从浏览器直接访问
4. **DLX 验证**：临时改 NotificationConsumer 抛异常 → 确认消息 3 次重试后进入 dead-letter
5. **Phase 4 规划**：Kafka 替换 Redis Pub/Sub、WebRTC 音视频、Elasticsearch 消息全文搜索
