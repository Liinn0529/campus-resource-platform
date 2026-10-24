# 后端运行与公共层验证

需要 Docker、Git 和 OpenSSL；API 检查另需 Python 3.9+。在仓库根目录执行：

```sh
./scripts/test-backend.sh
```

脚本使用 Maven 3.9.11 / Java 21 容器，注入当前真实 Git SHA、dirty 标记和 UTC 时间，执行四模块 `mvn verify`。测试通过 Testcontainers 启动真实 PostgreSQL 16，运行同一份数据库 bootstrap 和 Flyway，并经随机端口 Tomcat 发送 HTTP；不 mock 被测业务。Docker 不可用时检查失败，不以跳过测试获取成功。每模块生成可执行 JAR、Surefire 和 JaCoCo 报告，均被 Git 忽略。当前 Phase 3 正在验证，测试数量和结果以当次报告为准。

Docker socket 默认挂载 `/var/run/docker.sock`；自定义安装可设置 `TESTCONTAINERS_SOCKET_PATH`。测试覆盖数据库账号隔离、迁移历史与审计保护、精确 local seed、非 local 无演示数据，以及登录/JWT、实时权限、房间范围、最后系统管理员保护、主数据修改和每日签到码。已发布迁移不改写：公共层使用 V1–V3，其余服务为 V1。

## 启动服务

推荐通过 Compose 提供私有凭据和内部数据库网络。只重建并启动公共层时：

```sh
./scripts/init-env.sh
./scripts/build.sh platform-service
docker compose up -d --wait --wait-timeout 180 platform-service
```

已有 Phase 2 数据库在公共层启动时自动执行新的 V2/V3，不需要删除数据卷。Phase 1 旧卷缺少服务账号，应先阅读[本地手册](local-development.md#已有-volume-与空库重放)。其他服务使用对应 Compose 服务名；没有宿主机后端或数据库端口，公开 API 通过网关访问。

宿主机直接运行 JAR 需要 Java 21，并自行提供可达的私网 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`FLYWAY_USERNAME` 和 `FLYWAY_PASSWORD`；JDBC URL 指定对应 `currentSchema`。不能把管理员或迁移身份用于业务数据源，不能将 Compose 内部 `postgres` 主机名直接当作宿主机地址。

公共层在所有 profile 中还要求 `PLATFORM_JWT_SECRET`、`PLATFORM_RESERVATION_TOKEN`、`PLATFORM_CLASSROOM_TOKEN`、`PLATFORM_MAINTENANCE_TOKEN` 和 `CAMPUS_BUSINESS_ZONE`。JWT 秘密至少 32 个 UTF-8 字节，三个内部秘密各至少 32 字节且彼此不同；本地生成器分别生成随机值。秘密仅放在私有运行环境，不写入命令行、源码或日志。非 local 启动没有默认管理员；应通过审阅过的初始化/运维流程配置真实管理员，不能为方便登录开启演示 seed。

## 登录与平台 API

公开入口为 `http://127.0.0.1:18080/api/v1`。`POST /auth/login` 接收用户名和密码，返回短期 access JWT；`GET /me` 返回当前身份。默认 issuer 为 `https://campus-resource.local/platform`，audience 为 `campus-web`，固定 HS256、15 分钟有效期，无 refresh token。issuer 是校验标识，不要求访问该域名发现密钥。每次请求重新读取数据库中的启用状态、auth_version 和权限；停用、改密和撤权不会等待旧 JWT 自然过期。

本地五个演示用户名为 `demo_admin`、`demo_student_01`、`demo_student_02`、`demo_room_admin`、`demo_property_staff`，初始密码来自私有 `.env` 的 `DEV_SEED_PASSWORD`。修改这个环境值不会重设已有用户密码。使用下列脚本完成登录和 API 验证，不把 token、密码或签到码输出到终端：

```sh
python3 scripts/smoke-phase-3.py --expected-commit "$(git rev-parse HEAD)"
```

如构建时有未提交修改，应传构建日志中的 `<SHA>-dirty`。端口修改后另加 `--url http://127.0.0.1:<port>`。脚本读取私有 `.env`；不要用带明文密码的 shell 历史、`curl -v` 或整段响应日志代替这一步。

管理和读取的具体方法、请求字段、错误码见[平台 API](../api/platform.md)与 [OpenAPI JSON](../../contracts/openapi/platform.v1.json)。系统管理员管理用户/角色、全局空间、参数和审计；房间管理员只能维护明确授权房间及其座位、读取对应签到码，无范围默认拒绝。学生可以读取启用空间和本人身份，不能通过直接调用 API 绕过管理限制。当前没有业务管理页面，不以隐藏前端按钮作为权限控制。

不提供用户或空间硬删除。用户可停用，空间可修改启用状态；涉及未来预约和历史引用的删除/停用协调属于后续阶段。通知投递、M1 预约及实际签到、M2 审批和 M3 工单尚未实现。

## 数据库与运行端点检查

```sh
python3 scripts/check-database.py --phase3
python3 scripts/smoke-phase-1.py --platform-api --expected-commit "$(git rev-parse HEAD)"
```

数据库脚本针对当前 Compose 库检查八个 TCP 身份、schema owner、迁移版本、DDL/历史限制和演示基线；具体参数见脚本 `--help`。运行脚本核对七容器健康、真实 SHA/时间、只读运行接口、隐藏路径及 Web 资源，不等同于全部业务验收。结果另记于阶段验证报告，不由运行手册预先宣称。
