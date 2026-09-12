# booking-backend · 场地预约排期系统（后端）

Java 17 + Spring Boot 3.4 + MyBatis-Plus + MySQL 8 + Redis；阶段三增加可选 PostgreSQL + pgvector。
当前已完成技术方案的**阶段一：并发防超卖**、**阶段二：Redis 与释放可靠性**以及**阶段三：向量知识库基础**。

## 跑起来

> **本机现状（已实测）**：MySQL 8.0.41 装在 `C:\Program Files\MySQL\MySQL Server 8.0` 跑在 3306；
> Redis 8.10.1 已是 Docker 容器 `redis` 跑在 6379。**两个中间件都齐了，不用执行 docker compose。**

```bash
# 1) 改密码：把 src/main/resources/application.yml 里的 password 改成你本机 MySQL 的密码

# 2) 建库建表 + 初始化数据（schema.sql 自带 CREATE DATABASE）
"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -h127.0.0.1 -uroot -p < src/main/resources/db/schema.sql
"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -h127.0.0.1 -uroot -p < src/main/resources/db/data.sql

# 3) 编译与启动（如果 Maven 没有加入 PATH，可直接使用本机安装目录）
"D:/apache-maven-3.9.16/bin/mvn.cmd" -Dmaven.repo.local=D:/booking-linked/.m2 clean package
java -jar target/booking-backend-1.0.0.jar
```

服务在 `http://127.0.0.1:8080`，统一前缀 `/api`。
首次启动会自动为每个场地铺 14 天时段，不用手动调内部接口。

前端切到真实后端：把 `booking-frontend/.env.development` 的 `VITE_USE_MOCK` 改成 `false`，
再配一个 Vite 代理把 `/api` 转发到 `127.0.0.1:8080`（或直接让后端 CORS 放行，已配置）。

### 可选启用阶段三向量知识库

默认不启用，现有 MySQL + Redis 模式无需 PostgreSQL 或模型密钥。需要使用知识重建时执行：

```bash
docker compose --profile vector up -d
$env:BOOKING_VECTOR_ENABLED = "true"
$env:OPENAI_API_KEY = "你的 Embedding 服务密钥"
"D:/apache-maven-3.9.16/bin/mvn.cmd" -Dmaven.repo.local=D:/booking-linked/.m2 clean package
java -jar target/booking-backend-1.0.0.jar
```

可通过 `BOOKING_VECTOR_DB_URL`、`BOOKING_VECTOR_DB_USERNAME`、`BOOKING_VECTOR_DB_PASSWORD`、`OPENAI_BASE_URL`、`OPENAI_EMBEDDING_PATH`、`OPENAI_EMBEDDING_MODEL` 和 `BOOKING_VECTOR_DIMENSIONS` 覆盖默认配置。生产环境不要把密钥写进配置文件。

若使用阿里云百炼的千问向量模型，可使用 OpenAI 兼容模式。推荐 `text-embedding-v4` 配置为 1536 维；百炼工作空间 Base URL 通常形如 `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`，此时设置 `OPENAI_EMBEDDING_PATH=/embeddings`。API Key 仍通过 `OPENAI_API_KEY` 传入，不能写入仓库。

## 接口契约自检

```bash
node verify-contract.mjs
```

复刻前端拦截器的解包逻辑，对真实后端逐个接口断言「该是数组的是数组」。
前端 el-table 的 `:data` 收到对象会渲染报错、loading 遮罩摘不掉，这个脚本专门防这类回归。

## 并发下单验证

确保后端、MySQL、Redis 已启动后执行：

```bash
node verify-concurrency.mjs
node verify-concurrency.mjs --concurrency 1000
```

脚本会选择未来 14 天内真实可用且未被演示账号占用的时段，同时发起并发下单，断言成功数不超过库存；随后取消测试订单，并再次断言 `available + locked + sold = total`。脚本只使用 `customer` 演示账号，若账号已有所有未来时段的有效订单，请先取消一条或改用测试账号。

## 目录

```
src/main/java/com/example/booking/
├── BookingApplication.java          启动类（@MapperScan + @EnableScheduling）
├── DataInitializer.java             首次启动建演示账号 + 绑场馆 + 铺时段
├── common/          Result / BizException / GlobalExceptionHandler / UserContext
├── config/          JacksonConfig / WebMvcConfig（CORS + 拦截器注册）
│                    AuthInterceptor / SecurityBeansConfig（BCrypt）
├── controller/      Auth / Venue / Court / Reservation / Merchant
├── recommendation/  知识文档工厂、PgVector 重建服务、商家重建入口
├── service/         SlotService、ReservationService、ReleaseScheduler
│   └── impl/
├── mapper/          MyBatis-Plus，库存变动全部是「单条带条件 UPDATE」
├── domain/
│   ├── entity/      Venue / Court / Slot / Reservation / IdempotentRecord
│   ├── enums/       ReservationStatusEnum
│   ├── vo/          SlotVO / ReservationVO
│   └── dto/         CreateReservationRequest
├── job/             SlotGenerateJob（每天凌晨滚动生成时段）
└── DataInitializer  首次启动自动铺时段
```

## 三个核心实现，面试就讲这三个

### 1. 防超卖：一条 SQL 就够了

```sql
UPDATE slot SET available = available - 1, locked = locked + 1, version = version + 1
 WHERE id = #{id} AND available > 0;
```

命中主键 → InnoDB 加行锁，并发请求在同一行上自然串行；
判断和扣减在同一条 SQL 内 → 不存在 check-then-act 竞态。
影响行数 0 就是被抢走了。见 `SlotMapper#deductAvailable`。

### 2. 「超时释放」与「用户支付」的竞态，不需要分布式锁

两者都带 `AND status = 0`：

```sql
UPDATE reservation SET status = #{target}, version = version + 1
 WHERE order_no = #{orderNo} AND status = #{expect};
```

竞争同一行的行锁，只有一个能拿到影响行数 1，输的直接跳过后续步骤。
见 `ReservationMapper#updateStatus`、`ReservationServiceImpl#confirm/release`。

### 3. 超时释放：持久化任务 + 兜底扫描

- 主：`ReleaseScheduler` 把到期任务写入 `reservation_release_task`，下单事务回滚时任务也回滚；进程重启后仍可继续处理
- 抢占：任务通过条件 UPDATE 从「待执行」变成「处理中」，多实例不会重复执行同一任务
- 重试：释放异常时按 5、15、30、60 秒退避重试，处理中任务超时会被恢复
- 兜底：`@Scheduled` 每分钟扫描 `status=0 AND expire_at < NOW()`，覆盖历史数据、手工数据和异常任务
- 幂等：`release()` 靠订单状态 CAS，重复调用绝不重复归还库存

## 幂等三道防线

1. 进入确认页先取令牌，下单时 `UPDATE ... WHERE result IS NULL` 抢占，抢占失败即重复提交
2. `reservation.uk_user_active_slot(user_id, active_slot_id)` 唯一索引，兜住缓存与锁全部失效的极端情况
3. 状态机 CAS，所有状态流转都带 `AND status = ?`

## 阶段二：Redis 与可靠性实现

1. `SlotServiceImpl#listByCourtAndDate` 使用 Redis Hash 缓存 `slot:day:{courtId}:{date}`，读取失败自动回源数据库
2. `ReservationServiceImpl#create` 扣库前使用 Lua 预扣 Redis，MySQL 条件 UPDATE 仍是最终裁决
3. Redis 计数漂移或不可用时，自动降级到 MySQL，并以数据库最新库存回写 Redis
4. 下单、确认、取消、超时释放后刷新库存缓存并删除日期缓存；缓存异常不回滚数据库事务
5. 持久化释放任务替代进程内 `DelayQueue`，当前以 MySQL 任务表实现 Outbox；后续接入 MQ 时可复用任务状态机
6. 并发脚本与单元测试验证库存不超卖、释放重试和库存守恒

## 阶段三：PgVector 知识库

阶段三只建设知识入库基础，不调用聊天模型，也不让模型参与交易：

1. `KnowledgeDocumentFactory` 将场馆、场地类型、价格、营业时间、设施标签和预约规则转换成公开知识文档
2. 每个场地使用稳定键 `venue:{venueId}:court:{courtId}:profile`，并保存 `documentType`、`venueId`、`courtId`、`courtType`、`status`、`updatedAt`、`contentHash` 元数据
3. `POST /api/merchant/knowledge/rebuild` 由商家触发单个场地重建；服务端先按稳定键删除旧版本，再写入新版本，重复执行不会累积旧文档
4. PostgreSQL 通过 `booking_vector_store` 保存向量；实时库存仍只从 MySQL 查询，向量库不保存订单、用户聊天和库存账本
5. 未开启 `BOOKING_VECTOR_ENABLED` 时，重建接口返回明确的 5030 业务错误，交易接口继续正常运行

生产环境可先执行 `src/main/resources/db/vector-schema.sql`，开发环境也可以让 Spring AI 在启用向量配置后初始化表结构。

## 用户体系与两端拆分

### 演示账号（首次启动自动创建）

| 账号 | 密码 | 角色 |
|---|---|---|
| `customer` | `123456` | 顾客 |
| `merchant` | `123456` | 商家（名下挂着 3 个演示场馆） |

### 会话方案：随机 token + Redis，而不是 JWT

取舍：JWT 无状态、不依赖 Redis，但**无法主动失效**——登出、封号、踢下线都要等过期，且续期麻烦。
预约类系统里「封号要立刻生效」是硬需求，所以选 Redis 会话：
可以主动 revoke、可以滑动续期、能查到某用户全部在线会话，代价是每请求多一次 Redis 查询。

存储：`auth:token:{token}` → `userId`，TTL 7 天，剩余不足 1 天时自动续期。

### 鉴权与角色

`AuthInterceptor` 统一拦截：

| 开放接口（无需登录） | 需要登录 | 需要商家角色 |
|---|---|---|
| `/auth/login`、`/auth/register` | `/reservations/**` | `/merchant/**` |
| `/venues`、`/courts`、`/courts/*/slots` | `/auth/me`、`/auth/logout` | |

当前用户在拦截器里写入 `UserContext`（ThreadLocal），业务层直接取，
**前端传的 userId 一律不认**——否则就能替别人下单。请求结束会 `remove()`，防止线程池复用导致用户串号。

### 防越权

除了「谁能访问哪个接口」，还做了「谁能访问哪条数据」：

- 查/确认/取消订单时校验 `reservation.user_id == 当前用户`，否则 4030
- 商家生成时段时校验 `court.venue.merchant_id == 当前商家`，否则 4030

只做前者会造成水平越权：换个订单号就能操作别人的预约，A 商家能生成 B 商家的时段。

### 密码

BCrypt（`spring-security-crypto`，未引入整套 `spring-boot-starter-security`，避免过滤链自动配置）。
登录失败时「用户名不存在」与「密码错误」返回同一提示，防止被用来枚举账号。

## 已知简化

- 没有真实支付，`confirm` 就是确认动作
- 注册时可直接选商家角色，真实项目应走资质审核 + 线下签约
- 当前没有接入真实支付和 MQ；超时释放使用 MySQL 持久化任务表 + 定时处理，已具备重启恢复能力
- `DataInitializer` 预置演示账号，真实环境应移除

## 分层约定

```
controller/   只做协议转换：收参数 → 调 Service → 包 Result。不出现业务判断，不注入 Mapper
service/      业务规则 + 事务边界。统一「接口 + impl」组织，impl 在 service/impl 下
              例外：ReleaseScheduler 是调度组件不是业务服务，保持为普通类
mapper/       单条 SQL，不含业务判断
```

`SlotService` 没有同名 Controller —— 它的两个调用方按 REST 资源划分：
查时段挂在 `CourtController`（`/courts/{courtId}/slots`，时段是场地的子资源），
生成时段挂在 `MerchantController`。**Controller 和 Service 不是一一对应的。**

## 时段生成是「幂等补齐」，不是「删除重建」

`POST /merchant/slots/generate` 的行为：

- 已存在的时段**原样保留**，不重建、不覆盖 —— 保住 `sold` / `locked` 计数
- 被任何订单引用过的时段**一行都不删** —— 否则订单 `slot_id` 悬空、已售库存归零，该时间段会被重复卖出
- 只清理「不在新营业时间窗口内 且 无订单引用」的时段
- 只补缺失的窗口，返回新建个数（0 表示已是最新）

> 早期实现是「先 `DELETE` 掉 `biz_date >= today` 的全部时段再重建」。
> 这在有真实订单的数据上会直接摧毁已售库存 —— 是一个只在有数据时才会暴露的 bug。
> 已修复，`SlotServiceImpl.generate` 里保留了详细注释说明为什么不能那样写。
