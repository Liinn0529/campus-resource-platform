# 运行端点契约（Phase 1）

四服务均在容器内提供下列 GET 接口，无业务身份或数据库依赖。完整业务 OpenAPI 留待相应业务阶段；这里不生成未实现的路径。

`GET /actuator/health`：应用健康返回 200 和 `{"status":"UP"}`，不暴露细节。此时尚无数据库健康指示器。

`GET /actuator/info`：返回 200，JSON 顶层恰好四个字段：

| 字段 | 来源 |
|---|---|
| service | Maven artifactId：platform-service、reservation-service、classroom-service 或 maintenance-service |
| version | Maven 项目版本，目前 0.1.0-SNAPSHOT |
| gitCommit | 构建时实际完整 Git SHA；有未提交源码时追加 -dirty |
| buildTime | 构建时 UTC ISO-8601 时间 |

Spring Boot build-info 插件生成 BuildProperties，服务读取打包数据。不是启动时临时生成的 commit/time，也不是前端静态数据。缺失或格式错误的 SHA 导致 Maven 构建失败。

后端 POST/PUT/DELETE 以及其他路径均拒绝（403），不开放 actuator 根目录、env、登录或业务 API。本地网关映射为 GET `/status/<模块名>/health` 和 `/status/<模块名>/info`；模块名仅 platform/reservation/classroom/maintenance。该路径的非 GET 请求返回 405；未经白名单的管理、内部和业务路径返回 404。连接失败时网关返回 502/504 等实际错误，前端显示失败原因。

边界由四个 RuntimeEndpointsTest、前端状态聚合测试和 scripts/smoke-phase-1.py 分别验证；记录见 Phase 1 验证报告。
