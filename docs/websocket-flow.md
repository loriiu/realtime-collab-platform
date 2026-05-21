# WebSocket 连接流程

## 连接建立

```
客户端                          Gateway                          message-service
  │                                │                                    │
  │  ws://host/ws?token=xxx        │                                    │
  │ ──────────────────────────────>│                                    │
  │                                │  load-balance to msg instance      │
  │                                │ ──────────────────────────────────>│
  │                                │                                    │ JwtHandshakeInterceptor
  │                                │                                    │   ├─ 提取 query token
  │                                │                                    │   ├─ JwtUtil.parseToken()
  │                                │                                    │   ├─ 存入 attributes["userId"]
  │                                │                                    │   └─ return true
  │                                │                                    │
  │                                │                                    │ ChatWebSocketHandler
  │                                │                                    │   ├─ afterConnectionEstablished
  │                                │                                    │   ├─ WsSessionManager.put(userId, session)
  │                                │                                    │   └─ OnlineService.markOnline(userId)
  │                                │                                    │       └─ Redis SET collab:msg:online:{userId} {serverId} EX 120
  │  <── WebSocket connected ──────│<───────────────────────────────────│
```

## 消息发送流程

```
发送者                          message-service (实例A)                    Redis                    message-service (实例B)     接收者
  │                                    │                                    │                              │                    │
  │  {"content":"hello",               │                                    │                              │                    │
  │   "receiverId":2,                  │                                    │                              │                    │
  │   "sessionId":100}                 │                                    │                              │                    │
  │ ──────────────────────────────────>│                                    │                              │                    │
  │                                    │ 1. MessageRateLimiter.tryAcquire   │                              │                    │
  │                                    │ 2. Snowflake.nextId() → msgId      │                              │                    │
  │                                    │ 3. Build Message entity            │                              │                    │
  │                                    │ 4. CompletableFuture.runAsync →    │                              │                    │
  │                                    │    messageMapper.insert(msg)        │                              │                    │
  │                                    │ 5. UserFeignClient.getUserInfo →    │                              │                    │
  │                                    │    WsMessageDTO.fromMessage()      │                              │                    │
  │                                    │ 6. WsMessageDispatcher.dispatch()  │                              │                    │
  │                                    │    ├─ 本地有 receiver session?     │                              │                    │
  │                                    │    │  ├─ YES → session.sendMessage │                              │                    │
  │                                    │    │  └─ NO  → 继续 step 7         │                              │                    │
  │                                    │ 7. MessageBroker.publish()          │                              │                    │
  │                                    │ ──────────────────────────────────>│ PUBLISH collab:msg:chat       │                    │
  │                                    │                                    │ ─────────────────────────────>│                    │
  │                                    │                                    │                              │ RedisMessageSubscriber.onMessage
  │                                    │                                    │                              │ ├─ deserialize WsMessageDTO
  │                                    │                                    │                              │ ├─ SETNX dedup key
  │                                    │                                    │                              │ ├─ receiver has local session?
  │                                    │                                    │                              │ └─ YES → pushToSession ──>│
  │  <── ERROR (if rate-limited) ──────│                                    │                              │                    │
```

## 心跳机制

```
客户端 ── {"type":"PING"} ──> ChatWebSocketHandler.handleTextMessage
                                   ├─ 回复 {"type":"PONG"}
                                   └─ OnlineService.refreshHeartbeat(userId)
                                       └─ Redis EXPIRE collab:msg:online:{userId} 120
```

## 连接断开

```
ChatWebSocketHandler.afterConnectionClosed
  ├─ WsSessionManager.remove(userId)
  └─ OnlineService.markOffline(userId)
      └─ Redis DEL collab:msg:online:{userId}
```
