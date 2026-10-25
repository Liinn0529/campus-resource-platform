# 运行端点契约（Phase 1–4）

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

Phase 4 网关将 `/api/v1/reservation/...` 与 `/api/v1/reservations`（含子路径）转发 M1，其余 `/api/v1/` 转发 D。登录以外的公开业务 API 要求有效用户 JWT，缺失/无效为401、权限不足为403；本人预约对其他用户统一404。M1 每次通过真实 D `/api/v1/me` 校验当前身份，另用独立服务凭据读取目录/参数；D 故障/超时/无效响应返回503。网关继续屏蔽 `/internal`，M2/M3 尚无业务 API。具体契约见 [platform.md](platform.md)、[reservation.md](reservation.md)和对应 [D](../../contracts/openapi/platform.v1.json)/[M1](../../contracts/openapi/reservation.v1.json) OpenAPI。

边界由四个 RuntimeEndpointsTest、前端状态聚合测试和 scripts/smoke-phase-1.py 分别验证，Phase 3 运行该脚本时须加 `--platform-api`。Phase 4 的 `scripts/smoke-phase-4.py` 先执行公共层回归再完成实际预约流程；数据库检查使用 `scripts/check-database.py --phase4`，验证D V3/M1 V2/其他V1与账本一致性。真实D JAR/HTTP与PostgreSQL测试另验证权限、首次并发锁和失败回滚；记录见各阶段验证报告。运行检查页不是完整业务 UI，主要角色页面留 Phase 9。
