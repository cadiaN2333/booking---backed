# 排期公开可见性与商家生成权限设计

## 目标

修复排期服务绕过场地和场馆审核、上架状态校验的问题，确保公开查询不泄露下架或待审资源的时段，并阻止商家为未公开资源生成排期。

## 范围

只修改 `SlotServiceImpl` 及其 Mockito 单元测试。明确不修改前端、商家 CRUD 或 admin API。

## 设计

公开入口 `listByCourtAndDate` 在任何 Redis 读取前，依次确认 court 存在且 `auditStatus=1,status=1`，以及所属 venue 存在且 `auditStatus=1,status=1`。校验失败统一返回空列表，因此不存在状态信息或时段信息泄露；校验通过后才进入现有缓存/数据库读取和 Redis 故障回源逻辑。

商家入口 `generateForMerchant` 先确认 court 存在，再确认 venue 存在且属于当前商家；归属不符继续返回 `4030`。归属通过后，要求 court 和 venue 均审核通过且上架，否则抛业务异常并在调用生成逻辑前结束。

## 测试策略

沿用现有五参数构造器和 Mockito 测试方式，覆盖：公开查询对 court 下架、court 待审、venue 下架、venue 待审均返回空且不读取缓存；商家对未公开 court 或 venue 的生成被拒且不访问排期写入流程。现有 Redis 故障回源测试改为提供已公开的 court/venue 前置数据。

## 验证

执行 `mvn -Dtest=SlotServiceImplTest test`、`git diff --check`，并检查提交仅包含本次允许范围内的文件。
