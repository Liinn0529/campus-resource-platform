# 运行端点契约（Phase 1–6）

四服务均在容器内提供下列 GET 接口，无需业务身份。Phase 2 起服务启动必须通过各自 Flyway 迁移，健康检查使用应用账号检查 PostgreSQL 连接。完整业务 OpenAPI 留待相应业务阶段；这里不生成未实现的路径。

`GET /actuator/health`：应用及数据库连接健康时返回 200 和 `{"status":"UP"}`，不暴露连接信息或检查细节。数据库不可用时按 Spring Boot 健康状态返回 503；此端点不等同于验证完整业务权限，账号隔离另由数据库测试与检查脚本验证。

`GET /actuator/info`：返回 200，JSON 顶层恰好四个字段：

| 字段 | 来源 |
|---|---|
| service | Maven artifactId：platform-service、reservation-service、classroom-service 或 maintenance-service |
| version | Maven 项目版本，目前 0.1.0-SNAPSHOT |
| gitCommit | 构建时实际完整 Git SHA；有未提交源码时追加 -dirty |
| buildTime | 构建时 UTC ISO-8601 时间 |

Spring Boot build-info 插件生成 BuildProperties，服务读取打包数据。不是启动时临时生成的 commit/time，也不是前端静态数据。缺失或格式错误的 SHA 导致 Maven 构建失败。

四服务均不开放 actuator 根目录、env 及非 GET 运行端点。本地网关映射为 GET `/status/<模块名>/health` 和 `/status/<模块名>/info`；模块名仅 platform/reservation/classroom/maintenance。该路径的非 GET 请求返回 405；未经白名单的管理和所有内部路径返回 404。连接失败时网关返回 502/504 等实际错误，前端显示失败原因。

Phase6网关将 `/api/v1/reservation/...` 与 `/api/v1/reservations`（含子路径）及 `/api/v1/violations` 转发 M1，`/api/v1/classroom-requests`及子路径转发M2，其余 `/api/v1/` 转发D。登录以外的公开业务 API 要求有效用户 JWT，缺失/无效为401、权限不足为403；本人预约对其他用户统一404。M1 每次通过真实 D `/api/v1/me` 校验当前身份，另用独立服务凭据读取目录/参数、校验房间当日码并请求持久站内接收；D 故障/超时/无效响应返回503。网关继续屏蔽 `/internal`，M2已开放六操作，M3尚无业务API。具体契约见 [platform.md](platform.md)、[reservation.md](reservation.md)和对应 [D](../../contracts/openapi/platform.v1.json)/[M1](../../contracts/openapi/reservation.v1.json) OpenAPI。

边界由四个 RuntimeEndpointsTest、前端状态聚合测试和 scripts/smoke-phase-1.py 分别验证，Phase 3 运行该脚本时须加 `--platform-api`。Phase 4 的 `scripts/smoke-phase-4.py` 先执行公共层回归再完成实际预约流程；Phase6另执行 `scripts/smoke-phase-6.py`；数据库检查使用 `scripts/check-database.py --phase6`，验证D V4/M1 V3/M2 V2/M3 V1和签到/违约/提醒账本。真实D JAR/HTTP与PostgreSQL测试另验证权限、首次并发锁和失败回滚；记录见各阶段验证报告。运行检查页不是完整业务 UI，主要角色页面留 Phase 9。

Phase5已完成真实验证，历史结果见[Phase5验收](../review/phase-5-validation.md)。M1 默认独立30秒扫描/发送、调度池2，持久claim/lease和有界恢复；D站内ACCEPTED不代表邮件送达或用户已读。健康与版本端点只能证明进程/数据源与构建元数据，不能据此宣称调度、全部业务或CI通过；当前M2实际结果见[Phase6验证](../review/phase-6-validation.md)。

## Phase6当前接口与验证

M2六操作见[教室API](../api/classroom.md)，D新增本人审批范围。M2使用D和M1两种独立opaque凭据，Compose配置PLATFORM_BASE_URL/PLATFORM_CLASSROOM_TOKEN及M1_BASE_URL/M1_CLASSROOM_TOKEN；init-env保留旧凭据并补齐新值，不输出秘密。现有D4/M1 V3保留，M2从V1升V2、M3仍V1，保留卷升级，无需删除数据。

运行 `python3 scripts/smoke-phase-6.py --expected-commit <实际构建SHA>` 先回归Phase5，再验证真实D范围、提交/两级审批/拒绝/撤销/撤权与实际M1 403→SYNC_FAILED；临时为现有第二学生赋部门角色/范围后恢复，不扩seed。不会声称真实封房成功。定时超时/成功回执/重试边界另由真实HTTP/PostgreSQL+Clock或明确外部响应夹具验证。`check-database.py --phase6`核对M2四表一致性和没有假APPROVED/M2占用的当前阶段基线。

本地后端测试使用容器内部Linux工作区，公共Git源码归档不含.env；导出JAR、Surefire和JaCoCo XML，避免宿主同步class文件错误。完整130项通过，运行升级和最终SHA实际证据见[Phase6验收](../review/phase-6-validation.md)。完整角色Web/E2E留Phase9/10，M1占用/释放/恢复留Phase8；完成Phase6后停止等待Phase7。
