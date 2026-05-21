# 面试要点

## 1. 分布式 WebSocket 的核心挑战是什么？

**答**：核心挑战是**跨实例消息路由**。在单实例中，所有 WebSocket 连接在同一进程内，可以直接通过内存中的 Map 找到接收者。在多实例中，发送者和接收者可能连接在不同实例上，需要通过外部通信机制（如 Redis Pub/Sub）进行跨实例消息传递。

## 2. 为什么选择 Redis Pub/Sub 而不是其他方案？

**答**：
- **简单**：Spring Data Redis 原生支持，无需额外中间件
- **实时**：消息即时推送，延迟低
- **无状态**：不需要持久化（消息已落库 MySQL），Pub/Sub 仅做广播
- **轻量**：相比 Kafka/RabbitMQ，Redis Pub/Sub 更轻量，适合消息广播场景

局限性：消息不持久化、不保证送达。但本项目中消息已持久化到 MySQL，Pub/Sub 仅用于实时推送，离线消息通过数据库拉取。

## 3. 如何保证消息不丢失？

**答**：
1. **先持久化再广播**：消息先通过异步线程池写入 MySQL，再通过 Pub/Sub 广播
2. **离线消息拉取**：接收者重连后通过 `/session/offline` 接口拉取未收到的消息
3. **去重机制**：Redis SETNX 防止同一消息被多次投递

## 4. Snowflake ID 的 worker-id 如何分配？

**答**：通过环境变量 `SNOWFLAKE_WORKER_ID` 在部署时注入，每个实例分配不同的 worker-id，保证全局唯一。在 docker-compose 中，实例1 使用 worker-id=1，实例2 使用 worker-id=2。

## 5. 如何处理消息顺序？

**答**：
- Snowflake ID 基于时间戳自增，天然保证全局大致有序
- 数据库查询使用 `ORDER BY create_time` 保证返回顺序
- Pub/Sub 广播不保证顺序，但每条消息独立，通过 messageId 排序即可

## 6. 连接断开后如何恢复？

**答**：
1. WebSocket `afterConnectionClosed` 触发 → `WsSessionManager.remove()` + `OnlineService.markOffline()`
2. 客户端重连时携带 JWT token → 握手拦截器验证 → 建立新连接
3. 重连后调用 `/session/offline?lastMessageTime=xxx` 拉取离线消息
4. 前端根据 messageId 去重显示

## 7. 如何做限流？

**答**：使用 Redis INCR + EXPIRE 实现滑动窗口计数器，每秒最多 20 条消息/用户。超限返回 ERROR 消息，不阻塞连接。
