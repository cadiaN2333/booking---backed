# 场地预约排期系统

一个面向体育场馆、会议室和自习空间的预约排期平台，采用前后端分离架构，覆盖顾客预约、商家资源管理、平台审核、库存控制和订单状态流转。

## 项目架构

```text
booking-linked/
├── booking-frontend/       Vue 3 + TypeScript + Vite + Element Plus
└── booking-backend/        Java 17 + Spring Boot + MyBatis-Plus
    ├── MySQL                业务数据、订单、库存和持久化任务
    └── Redis                会话、库存缓存和高并发预扣
```

系统采用分层设计：

```text
Controller  协议转换、参数校验和统一响应
    ↓
Service     业务规则、权限校验和事务边界
    ↓
Mapper      MyBatis-Plus 数据访问与条件更新
    ↓
MySQL       用户、场馆、场地、时段、订单和释放任务
```

## 核心业务模块

### 顾客端

- 浏览已审核并上架的场馆和场地
- 根据日期、类型、价格、时间和实时库存筛选可预约时段
- 创建预约、确认预约、取消预约和查看预约记录
- 场地推荐结果直接关联到具体场馆、场地和可预约时段

### 商家端

- 创建和维护自有场馆、场地及营业时间
- 配置场地价格、营业时间和时段长度
- 补齐未来排期并查看场地经营数据
- 资源提交后进入平台审核，审核通过后自动上架

### 管理端

- 审核商家提交的场馆和场地
- 审核通过后自动发布资源
- 驳回时记录原因，商家可修改后重新提交
- 统一查看资源状态和审核记录

## 关键技术实现

### 1. 并发预约与防超卖

库存采用 `total + available + locked + sold` 三段式模型，预约扣减使用带条件的原子更新：

```sql
UPDATE slot
SET available = available - 1,
    locked = locked + 1,
    version = version + 1
WHERE id = ?
  AND available > 0;
```

通过数据库行锁和影响行数判断，避免并发请求中的 check-then-act 竞态。

### 2. Redis 与数据库双重校验

- Redis 用于会话、时段缓存和库存快速预扣
- Redis 不可用时自动回源 MySQL，保证核心预约链路仍可用
- MySQL 条件更新作为最终库存裁决
- 下单、确认、取消和超时释放后同步库存缓存

### 3. 订单状态机与可靠释放

- 订单状态通过 CAS 条件更新完成流转
- 超时订单写入持久化释放任务表
- 定时任务负责到期处理、失败重试和卡死任务恢复
- 释放逻辑基于订单状态保证幂等，避免重复归还库存

### 4. 权限与数据隔离

- 顾客、商家、管理员使用不同角色权限
- 商家只能操作自己名下的场馆和场地
- 顾客只能查看和操作自己的订单
- 公开查询、预约下单和库存扣减都会再次校验资源是否已审核并上架
- 会话使用 Redis Token，支持主动失效和账号封禁即时生效

### 5. 时段排期

- 根据营业时间和时段长度自动生成排期
- 每个时段独立维护总量、可用、锁定和已售库存
- 排期生成采用幂等补齐策略，不删除已有订单关联的时段
- 修改营业时间时，仅清理无订单引用的无效时段窗口

## 目录结构

```text
src/main/java/com/example/booking/
├── BookingApplication.java       启动类
├── common/                       统一响应、异常和用户上下文
├── config/                       Redis、CORS、鉴权和基础配置
├── controller/                   REST 接口
├── domain/
│   ├── dto/                      请求对象
│   ├── entity/                   数据库实体
│   ├── enums/                    状态枚举
│   └── vo/                       返回对象
├── job/                          定时排期任务
├── mapper/                       MyBatis-Plus Mapper
└── service/
    └── impl/                     业务服务实现
```

## 本地运行

环境要求：

- JDK 17
- MySQL 8
- Redis 6+
- Maven 3.9+

先创建数据库并初始化基础表：

```bash
mysql -h127.0.0.1 -uroot -p < src/main/resources/db/schema.sql
mysql -h127.0.0.1 -uroot -p < src/main/resources/db/data.sql
mysql -h127.0.0.1 -uroot -p < src/main/resources/db/migration-v5-platform-audit.sql
```

配置数据库密码后启动：

```powershell
$env:BOOKING_DB_PASSWORD = "你的 MySQL 密码"
$env:BOOKING_ADMIN_PASSWORD = "本地管理员密码"

& "D:\apache-maven-3.9.16\bin\mvn.cmd" `
  -Dmaven.repo.local=D:/booking-linked/.m2 clean package

java -jar target/booking-backend-1.0.0.jar
```

后端地址：`http://127.0.0.1:8080/api`

前端开发环境将 `/api` 转发到后端 8080 端口即可。前端项目位于同级目录 `booking-frontend`。

## 本地演示账号

首次启动会自动准备演示账号；生产环境请关闭或替换默认账号初始化逻辑。

| 账号 | 默认密码 | 角色 |
|---|---|---|
| `customer` | `123456` | 顾客 |
| `merchant` | `123456` | 商家 |
| `admin` | 通过 `BOOKING_ADMIN_PASSWORD` 配置 | 管理员 |

## 项目亮点

- 使用数据库条件更新解决高并发库存竞争
- Redis 缓存故障时具备数据库降级能力
- 通过订单状态 CAS 和持久化任务保证超时释放可靠性
- 通过角色权限、资源归属和公开状态三层校验防止越权
- 通过幂等补齐策略维护未来排期，避免破坏已有订单数据
- 前后端分离，业务模块边界清晰，便于扩展不同类型的预约资源
