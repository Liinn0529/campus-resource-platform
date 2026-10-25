# 运行端点契约（Phase 1–3）

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

Phase 3 起 `/api/v1/` 由网关转发至 D，登录以外的公共 API 必须提供有效用户 JWT，缺失或无效返回 401，权限不足返回 403；已认证但路径不存在返回 404。D 的内部服务调用使用独立凭据，用户 JWT 不能替代，网关继续屏蔽 `/internal`。M1/M2/M3 尚无业务 API。具体公共层契约见 [platform.md](platform.md) 和 [OpenAPI](../../contracts/openapi/platform.v1.json)。

边界由四个 RuntimeEndpointsTest、前端状态聚合测试和 scripts/smoke-phase-1.py 分别验证，Phase 3 运行该脚本时须加 `--platform-api`。公共层权限和运行维护另由真实 PostgreSQL/HTTP 测试与 `scripts/smoke-phase-3.py` 检查，数据库脚本 Phase 3 须加 `--phase3`；记录见各阶段验证报告。运行检查页不是完整业务 UI，主要角色页面留 Phase 9。
