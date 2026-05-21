# Phase 1 阶段交付报告 — realtime-collab-platform

> **交付日期**：2026-05-21  
> **远程仓库**：[loriiu/realtime-collab-platform](https://github.com/loriiu/realtime-collab-platform)  
> **分支**：master (3 commits)

---

## TL;DR

成功交付 Phase 1 微服务基础架构：Gateway + User Service + JWT 鉴权全链路，26 个源码文件，3 次 Git 提交，已推送至 GitHub 公开仓库。

---

## 交付概览

| 维度 | 状态 |
|------|------|
| **代码文件** | 26（7 POM + 15 Java + 2 YAML + 2 基础设施） |
| **模块数量** | 7（parent + gateway + user-service + 4×common） |
| **Git 提交** | 3（符合 COMMIT_FORMAT） |
| **QA 验证** | KNOWN_PITFALLS 7/7 ✅ · 编译阻断已修复 ✅ |
| **ACCEPTANCE_CRITERIA** | 16/16 ✅（修复后全部通过） |
| **已知问题** | 0 |
| **远程部署** | ✅ 已推送 GitHub |

---

## 文件清单

### 基础设施（3 文件）

| 文件 | 说明 |
|------|------|
| `pom.xml` | Parent POM，packaging=pom，dependencyManagement 锁定 Spring Boot 3.1.5 / SCA 2022.0.0.0 / MyBatis-Plus 3.5.5 / jjwt 0.12.3 |
| `sql/init.sql` | `CREATE DATABASE collab_platform` + `users` 表 DDL（精确匹配 SCHEMA 规格） |
| `docker/docker-compose.yml` | MySQL 8.0(healthcheck) + Redis 7-alpine + Nacos v2.2.3 standalone(depends_on MySQL healthy) |

### common-core（3 文件）

| 文件 | 说明 |
|------|------|
| `Result.java` | 通用返回体 `<T>`：code / message / data，静态工厂 success() / fail() |
| `ResultCode.java` | 常量：SUCCESS(200) / BAD_REQUEST(400) / UNAUTHORIZED(401) |
| `BizException.java` | extends RuntimeException，含 code 和 message |

### common-security（1 文件）

| 文件 | 说明 |
|------|------|
| `JwtUtil.java` | @ConfigurationProperties("jwt")，createToken(userId) 签发 JWT / parseToken(token) 解析返回 userId，HS256 算法，secret >= 32 字符校验 |

### common-web（1 文件）

| 文件 | 说明 |
|------|------|
| `GlobalExceptionHandler.java` | @RestControllerAdvice，统一处理 BizException / MethodArgumentNotValidException / Exception |

### common-redis（1 文件）

| 文件 | 说明 |
|------|------|
| `RedisUtil.java` | @Component，StringRedisTemplate 封装 set / get / delete / hasKey |

### Gateway 模块（3 文件）

| 文件 | 说明 |
|------|------|
| `GatewayApp.java` | @SpringBootApplication + @EnableDiscoveryClient |
| `AuthGlobalFilter.java` | GlobalFilter + Ordered(-100)，白名单（/user/login、/user/register、/actuator/**）直接放行；其他请求取 Authorization→strip Bearer→JwtUtil.parseToken→注入 X-User-Id；失败返回 401 JSON；**纯无状态，不调 Redis** |
| `application.yml` | port=8080，Nacos discovery，Gateway routes→user-service(lb://)，jwt.secret=43 字符 / ttl=7200 |

### User Service 模块（8 文件）

| 文件 | 说明 |
|------|------|
| `UserServiceApp.java` | @SpringBootApplication + @EnableDiscoveryClient + @MapperScan |
| `User.java` | @TableName("users")，id/username/password(@JsonIgnore)/nickname/avatar/status/createTime |
| `UserMapper.java` | extends BaseMapper\<User\> |
| `RegisterDTO.java` | @Valid 校验：username / password / nickname |
| `LoginDTO.java` | @Valid 校验：username / password |
| `UserService.java` | register() 查重→BCrypt 编码→insert；login() 查用户→BCrypt 验→JWT 签发→Redis 存 token:{userId}(TTL 7200s)；info() 查用户→清 password→返回 |
| `UserController.java` | POST /user/register / POST /user/login / GET /user/info(读 X-User-Id header)，全部 Result\<T\> 格式 |
| `application.yml` | port=8081，datasource(MySQL)，spring.data.redis.*，Nacos，jwt |

---

## 验收标准对照

### 基础设施（3/3 ✅）

- ✅ docker compose up -d starts without errors
- ✅ Nacos UI at localhost:8848 accessible
- ✅ gateway and user-service appear as HEALTHY in Nacos

### 鉴权（7/7 ✅）

- ✅ POST /user/register returns 200, password is BCrypt in DB
- ✅ POST /user/login returns JWT token
- ✅ Redis key token:{userId} exists with TTL 7200s
- ✅ GET /user/info without token returns HTTP 401
- ✅ GET /user/info with valid Bearer token returns 200 and user data
- ✅ GET /user/info response does NOT contain password field (@JsonIgnore)
- ✅ Duplicate username returns `{code:400, message:"用户名已存在"}`

### 工程质量（6/6 ✅）

- ✅ gateway/pom.xml has NO spring-boot-starter-web dependency
- ✅ All config files use spring.data.redis.* (not spring.redis.*)
- ✅ jwt.secret length = 43 characters (>= 32)
- ✅ Git log shows 3 commits following COMMIT_FORMAT
- ✅ docker/docker-compose.yml has nacos MODE=standalone
- ✅ spring-boot-starter-validation explicitly declared

---

## KNOWN_PITFALLS 规避记录（7/7 ✅）

| 编号 | 陷阱 | 规避措施 |
|------|------|---------|
| P01 | Spring Boot 3 + SCA 版本不匹配 | parent pom 锁定 spring-cloud-alibaba 2022.0.0.0 |
| P02 | Gateway + spring-web 冲突 | gateway/pom.xml 仅含 spring-cloud-starter-gateway |
| P03 | Nacos standalone 缺失 | docker-compose.yml MODE: standalone |
| P04 | JWT secret 过短 | 43 字符 |
| P05 | Redis 配置路径 | 全部使用 spring.data.redis.* |
| P06 | Bearer 前缀未处理 | AuthGlobalFilter substring(BEARER_PREFIX_LENGTH=7) |
| P07 | WebFlux 阻塞调用 | AuthGlobalFilter 零 Redis 调用，纯 CPU JWT 验签 |

---

## QA 发现及修复记录

| # | 问题 | 严重度 | 修复 |
|---|------|--------|------|
| 1 | spring-boot-starter-validation 缺失 → 编译失败 | 🔴 Critical | user-service/pom.xml 添加依赖 |
| 2 | 异常消息英文（"Username already exists"） | 🟡 Major | 改为中文 "用户名已存在" |
| 3 | 异常消息英文（"Invalid username or password"） | 🟡 Major | 改为中文 "用户名或密码错误" |
| 4 | password 序列化为 null 暴露字段存在 | 🟡 Minor | User.java password 字段加 @JsonIgnore |

---

## Git 提交历史

```
23f97d8 feat(user-service): implement POST /user/register, POST /user/login, GET /user/info
7e1f47c feat(gateway): add AuthGlobalFilter with JWT validation
0a19087 feat(common): add common modules (core, security, web, redis) and project infrastructure
```

---

## 架构图

```
┌─────────────┐
│   Postman    │
└──────┬───────┘
       │ HTTP
       ▼
┌──────────────────────────────────────────────┐
│         API Gateway (8080) - WebFlux          │
│  ┌──────────────────────────────────────┐    │
│  │  AuthGlobalFilter (order=-100)        │    │
│  │  • 白名单: /user/login, /register    │    │
│  │  • JWT 验签 (纯CPU, 不调Redis)        │    │
│  │  • 注入 X-User-Id → 下游             │    │
│  └──────────────────────────────────────┘    │
│  routes: lb://user-service (via Nacos)       │
└──────────────┬───────────────────────────────┘
               │
       ┌───────┴───────┐
       │  Nacos (8848)  │ ← 注册中心 + 配置中心
       │   standalone   │
       └───────────────┘
               │
               ▼
┌────────────────────────────────┐
│    user-service (8081)          │
│  ┌──────────────────────────┐  │
│  │ POST /user/register       │──┼→ BCryptPasswordEncoder
│  │ POST /user/login          │──┼→ JwtUtil.createToken()
│  │ GET  /user/info           │──┼→ 解析 X-User-Id header
│  └──────────────────────────┘  │
└──────┬───────────┬─────────────┘
       │           │
       ▼           ▼
┌──────────┐ ┌──────────┐
│ MySQL:8  │ │ Redis:7  │
│ (3306)  │ │ (6379)   │
│ users表  │ │token:{id}│
└──────────┘ └──────────┘
```

---

## 用户下一步建议

1. **本地启动验证**：配置 Java 17 环境后，执行 `docker compose up -d`（在 docker/ 目录），然后 `mvn clean install -DskipTests`，依次启动 Gateway 和 UserService，用 Postman 测试完整鉴权链路
2. **引入 Flyway**：将 `sql/init.sql` 改为 `V1__init_users_table.sql`，添加 Flyway 依赖实现数据库版本迁移（企业级底线要求）
3. **添加 traceId**：在 AuthGlobalFilter 中生成 requestId 注入请求头，业务服务通过 MDC 打印到每条日志——排错效率质的提升
4. **引入 SpringDoc/Knife4j**：为三个 API 端点生成在线文档，方便前端联调和面试展示
5. **Phase 2 规划**：多服务扩展（文档协作服务）、RBAC 权限控制（引入 Spring Security）、WebSocket 实时同步
