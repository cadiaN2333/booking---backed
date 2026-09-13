# 排期公开可见性与商家生成权限实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为排期公开查询和商家生成补齐 court/venue 审核上架状态权限校验，并用回归测试锁定行为。

**Architecture:** 在 `SlotServiceImpl` 的两个入口复用私有资源状态判断。公开入口校验失败返回空列表且不触碰缓存；商家入口先做归属校验，再拒绝未公开资源，最后调用既有生成逻辑。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、AssertJ、Maven。

---

### Task 1: 补充公开排期可见性回归测试

**Files:**
- Modify: `src/test/java/com/example/booking/service/impl/SlotServiceImplTest.java`

- [ ] **Step 1: 为已公开资源补齐现有缓存故障测试前置数据**

在测试中保留现有五参数构造，并 stub 已公开 court 和 venue；这样缓存故障回源测试继续验证原有可用性，而不是因新权限校验提前返回。

- [ ] **Step 2: 写入失败测试覆盖 court/venue 的下架与待审状态**

为 `listByCourtAndDate` 增加四个独立测试，分别设置 court 或 venue 的 `status=0`、`auditStatus=0`，断言结果为空，并验证 `slotCacheService.getRawDay` 从未调用。

- [ ] **Step 3: 运行定向测试确认新增用例按预期失败**

运行：`mvn -Dtest=SlotServiceImplTest test`

预期：新增公开状态测试失败，失败原因是当前实现仍会读取缓存/数据库或返回排期；缓存故障测试在补齐有效资源后保持可验证状态。

### Task 2: 补充商家生成权限回归测试

**Files:**
- Modify: `src/test/java/com/example/booking/service/impl/SlotServiceImplTest.java`

- [ ] **Step 1: 写入商家上下文和实体构造辅助方法**

使用 `UserContext.set(new UserContext.LoginUser(...))` 建立当前商家，测试结束通过 `UserContext.clear()` 清理线程上下文；使用真实 `Court`、`Venue` 对象 stub mapper。

- [ ] **Step 2: 写入未公开资源生成被拒测试**

覆盖 court 下架、court 待审、venue 下架、venue 待审四种组合，断言抛出 `BizException`，并验证 `slotMapper.selectList`、`slotMapper.insert` 均未因生成流程被调用。

- [ ] **Step 3: 运行定向测试确认商家用例失败**

运行：`mvn -Dtest=SlotServiceImplTest test`

预期：新增商家权限测试失败，因为当前实现只校验归属后会继续进入生成逻辑。

### Task 3: 实现最小权限校验

**Files:**
- Modify: `src/main/java/com/example/booking/service/impl/SlotServiceImpl.java`

- [ ] **Step 1: 增加公开资源状态校验并置于缓存读取之前**

新增私有方法判断 court/venue 是否存在且两级均满足 `auditStatus=1,status=1`；`listByCourtAndDate` 首行执行判断，失败返回 `List.of()`，成功后保持原缓存和 Redis 故障回源逻辑不变。

- [ ] **Step 2: 增加商家生成的两级状态校验**

在现有归属校验之后检查 court/venue 状态，失败抛 `BizException("场地未审核通过或未上架")`，成功后继续调用 `generate`；不改 `generateAll`、CRUD 或 controller。

- [ ] **Step 3: 运行定向测试确认全部通过**

运行：`mvn -Dtest=SlotServiceImplTest test`

预期：`SlotServiceImplTest` 全部通过，且不出现编译错误或新增警告。

### Task 4: 完成验证并提交

**Files:**
- Verify: `src/main/java/com/example/booking/service/impl/SlotServiceImpl.java`
- Verify: `src/test/java/com/example/booking/service/impl/SlotServiceImplTest.java`
- Verify: `docs/superpowers/specs/2026-09-13-slot-visibility-permission-design.md`
- Verify: `docs/superpowers/plans/2026-09-13-slot-visibility-permission.md`

- [ ] **Step 1: 检查差异和格式**

运行：`git diff --check`；确认只包含上述本次文件，且没有前端、商家 CRUD、admin API 或生成目录改动。

- [ ] **Step 2: 提交中文 commit**

运行：`git add src/main/java/com/example/booking/service/impl/SlotServiceImpl.java src/test/java/com/example/booking/service/impl/SlotServiceImplTest.java docs/superpowers/specs/2026-09-13-slot-visibility-permission-design.md docs/superpowers/plans/2026-09-13-slot-visibility-permission.md`，随后执行 `git commit -m "修复排期状态校验与商家生成权限"`。
