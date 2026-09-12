# booking-backend · 场地预约排期系统（后端）

Java 17 + Spring Boot 3.3 + MyBatis-Plus + MySQL 8 + Redis。
实现的是技术方案里的**阶段一：数据库行锁防超卖**，阶段二的 Redis 预扣留了接入点（见文末）。

## 跑起来

> **本机现状（已实测）**：MySQL 8.0.41 装在 `C:\Program Files\MySQL\MySQL Server 8.0` 跑在 3306；
> Redis 8.10.1 已是 Docker 容器 `redis` 跑在 6379。**两个中间件都齐了，不用执行 docker compose。**

```bash
# 1) 改密码：把 src/main/resources/application.yml 里的 password 改成你本机 MySQL 的密码

# 2) 建库建表 + 初始化数据（schema.sql 自带 CREATE DATABASE）
"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -h127.0.0.1 -uroot -p < src/main/resources/db/schema.sql
"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -h127.0.0.1 -uroot -p < src/main/resources/db/data.sql

# 3) 启动
mvn spring-boot:run
# 或者 mvn clean package && java -jar target/booking-backend-1.0.0.jar
```

服务在 `http://127.0.0.1:8080`，统一前缀 `/api`。
首次启动会自动为每个场地铺 14 天时段，不用手动调内部接口。

前端切到真实后端：把 `booking-frontend/.env.development` 的 `VITE_USE_MOCK` 改成 `false`，
再配一个 Vite 代理把 `/api` 转发到 `127.0.0.1:8080`（或直接让后端 CORS 放行，已配置）。

## 接口契约自检

```bash
node verify-contract.mjs
```

复刻前端拦截器的解包逻辑，对真实后端逐个接口断言「该是数组的是数组」。
前端 el-table 的 `:data` 收到对象会渲染报错、loading 遮罩摘不掉，这个脚本专门防这类回归。

## 目录

```
src/main/java/com/example/booking/
├── BookingApplication.java          启动类（@MapperScan + @EnableScheduling）
├── DataInitializer.java             首次启动建演示账号 + 绑场馆 + 铺时段
├── common/          Result / BizException / GlobalExceptionHandler / UserContext
├── config/          JacksonConfig / WebMvcConfig（CORS + 拦截器注册）
│                    AuthInterceptor / SecurityBeansConfig（BCrypt）
├── controller/      Auth / Venue / Court / Reservation / Merchant
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

### 3. 超时释放：主链路 + 兜底，两条最终都走幂等 release

- 主：`ReleaseScheduler` 用 `DelayQueue` 投递到期任务（真实项目换 RocketMQ 定时消息 / RabbitMQ 延迟插件）
- 兜：`@Scheduled` 每分钟扫 `status=0 AND expire_at < NOW()`，覆盖进程重启与消息丢失
- 幂等：`release()` 靠状态机 CAS，重复调用绝不重复归还库存

## 幂等三道防线

1. 进入确认页先取令牌，下单时 `UPDATE ... WHERE result IS NULL` 抢占，抢占失败即重复提交
2. `reservation.uk_user_slot(user_id, slot_id)` 唯一索引，兜住缓存与锁全部失效的极端情况
3. 状态机 CAS，所有状态流转都带 `AND status = ?`

## 阶段二：接 Redis 预扣（还没做，给你留的位置）

1. `SlotServiceImpl#listByCourtAndDate` 里加 Redis Hash 缓存 `slot:day:{courtId}:{date}`，5 分钟 TTL + 随机抖动
2. `ReservationServiceImpl#create` 扣库前先跑一段 Lua 预扣 Redis，扣不到直接快速失败
3. 扣库改走 MQ 异步落库，消费端按 orderNo 幂等
4. 每日对账 Job 校验 `available + locked + sold = total`
5. 热点时段加逻辑过期 + SETNX 重建锁，防缓存击穿

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
- 延迟释放用内存 DelayQueue，进程重启靠补偿 Job 兜（真实项目应换 MQ）
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
