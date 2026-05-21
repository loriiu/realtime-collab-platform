# Redis Pub/Sub 设计说明

## 概述

在多实例部署场景下，WebSocket 连接可能落在不同的 message-service 实例上。当发送者和接收者不在同一实例时，需要通过 Redis Pub/Sub 进行跨实例消息广播。

## 架构

```
┌──────────────────────┐         ┌──────────────────────┐
│  message-service-1   │         │  message-service-2   │
│                      │         │                      │
│  RedisMessagePublisher│        │  RedisMessagePublisher│
│       ↓              │         │       ↓              │
│  RedisPubSubBroker   │         │  RedisPubSubBroker   │
│                      │         │                      │
│  RedisMsgSubscriber  │         │  RedisMsgSubscriber  │
│       ↑              │         │       ↑              │
└───────┬──────────────┘         └───────┬──────────────┘
        │                                │
        │     Redis Pub/Sub              │
        │   collab:msg:chat              │
        └────────────┬───────────────────┘
                     │
              ┌──────┴──────┐
              │    Redis    │
              └─────────────┘
```

## 消息流转

1. **发送**：`ChatWebSocketHandler` 先通过 `WsMessageDispatcher` 尝试本地投递
2. **广播**：`MessageBroker.publish()` 将消息序列化为 JSON，通过 `StringRedisTemplate.convertAndSend()` 发布到 `collab:msg:chat` 频道
3. **接收**：每个实例的 `RedisMessageSubscriber` 收到消息后：
   - 反序列化为 `WsMessageDTO`
   - **去重检查**：`SETNX collab:msg:dedup:{msgId} EX 86400`，防止同一消息被多次投递
   - 检查本地是否有接收者的 WebSocket session
   - 如果有，通过 `WsMessageDispatcher.pushToSession()` 投递
   - 如果没有，跳过（消息已持久化到数据库，接收者可通过离线消息接口获取）

## 模式切换

使用 Spring `@ConditionalOnBean` / `@ConditionalOnMissingBean` 自动切换：

| 条件 | Broker | 行为 |
|------|--------|------|
| `RedisMessageSubscriber` Bean 存在 | `RedisPubSubMessageBroker` (@Primary) | 发布到 Redis |
| `RedisMessageSubscriber` Bean 不存在 | `LocalMessageBroker` | 空操作（本地已投递） |

## 去重机制 (Dedup)

- 每条消息通过 `messageId`（Snowflake 全局唯一）进行去重
- 使用 Redis `SET NX EX` 原子操作
- TTL 设置为 86400 秒（24小时），防止内存无限增长
- 发送者在本地投递 + Pub/Sub 广播 → 同一实例可能收到两份，去重保证只投递一次

## ACK 回传

ACK 消息也通过 Pub/Sub 回传：
- 接收者收到消息后发送 ACK（status=1 送达 / 2 已读）
- ACK 通过相同的 Pub/Sub 通道广播
- 发送者所在实例的 subscriber 收到 ACK 后更新消息状态
