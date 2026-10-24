# Phase 3 本地容器与公共层运行

需要 Git、Docker Engine/Desktop、Docker Compose 插件和 OpenSSL；完整验收另需 Python 3.9+。推荐至少 4 GB Docker 可用内存。前端独立开发另见[前端手册](frontend.md)，无需在宿主机安装 Java/Maven。

在仓库根目录执行：

```sh
./scripts/dev-up.sh
```

脚本将随机密码、JWT 签名秘密及三个独立内部调用方凭据写入权限 600 的私有 `.env`，保留已有非空值并补齐缺失或空的值；默认业务时区为 Asia/Shanghai。之后以当前真实 Git SHA 和 UTC 时间构建五个应用镜像，再启动 Compose 并等待健康检查。已有 Phase 2 volume 自动执行公共层 V2/V3，无需删除；Phase 1 旧卷不会重新 bootstrap，升级前阅读下文。

也可逐步执行：

```sh
./scripts/init-env.sh
./scripts/test-backend.sh
pnpm install --frozen-lockfile
pnpm lint
pnpm typecheck
pnpm test
pnpm build
./scripts/build.sh
docker compose up -d
docker compose ps
python3 scripts/check-database.py --phase3
```

本地入口：<http://127.0.0.1:18080>。端口冲突时修改 `.env` 的 `WEB_PORT` 后重新 `docker compose up -d`。只绑定 loopback，数据库与四服务没有宿主机端口。

`build.sh` 对未提交修改在 SHA 后追加 `-dirty`；新源码修改后必须重建，单独 `up -d` 只启动已有镜像。Maven Enforcer 拒绝缺失或无效 SHA，避免生成未知版本。`test-backend.sh` 使用 Maven/Java 21 容器运行完整 `verify`，依赖缓存在项目专属 Docker volume，真实 PostgreSQL 测试通过挂载 Docker socket 启动隔离容器。默认 socket 为 `/var/run/docker.sock`；自定义安装可设置 `TESTCONTAINERS_SOCKET_PATH`。后端测试要求 Docker 可访问，不会以跳过测试代替数据库验证。直接使用宿主 Maven 时：

```sh
mvn verify -DgitCommit="$(git rev-parse HEAD)" -DbuildTime="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
```

该直接命令只适用于工作区无未提交修改时。Java 必须为 21、Maven 为 3.9.x。

## 凭据、schema 与迁移

`.env.example` 仅列出变量名，不能当作可用密码配置。`init-env.sh` 为 PostgreSQL 管理员和八个服务账号分别生成随机密码，另生成五个演示用户的初始 `DEV_SEED_PASSWORD`、公共层的 `PLATFORM_JWT_SECRET` 及三个内部调用方秘密。任何命令输出和提交内容均不应包含这些值。它保留已有非空值，不执行数据库密码轮换，也不重设已有用户密码。

| Schema | 应用账号 / 密码变量 | 迁移账号 / 密码变量 |
|---|---|---|
| platform | platform_app / PLATFORM_DB_PASSWORD | platform_migrator / PLATFORM_FLYWAY_PASSWORD |
| reservation | reservation_app / RESERVATION_DB_PASSWORD | reservation_migrator / RESERVATION_FLYWAY_PASSWORD |
| classroom | classroom_app / CLASSROOM_DB_PASSWORD | classroom_migrator / CLASSROOM_FLYWAY_PASSWORD |
| maintenance | maintenance_app / MAINTENANCE_DB_PASSWORD | maintenance_migrator / MAINTENANCE_FLYWAY_PASSWORD |

`POSTGRES_PASSWORD` 只供数据库 bootstrap 管理员使用；服务不使用该账号。官方 PostgreSQL entrypoint 在空 volume 上执行 `deploy/postgres/010-service-databases.sh`，建立四个 schema 及八个无角色成员关系的普通登录账号。每个迁移账号拥有自己的 schema，应用账号只有自身 schema 的 USAGE、表的 SELECT/INSERT/UPDATE/DELETE 与序列的 USAGE/SELECT；应用账号没有 DDL、TRUNCATE 或 Flyway 历史表访问权限，公共层 app 也不能更新/删除 audit_log。PUBLIC 的数据库 CONNECT/TEMP 和 public schema 权限已撤销，账号不能访问其他服务 schema。

每个服务以应用凭据建立数据源，另以迁移凭据运行 `src/main/resources/db/migration/` 中的 Flyway migration。配置固定自身 schema，禁止自动创建 schema，关闭 Flyway clean；已发布迁移保持不可修改。当前公共层有 V1–V3，其余服务各有 V1。V2 增加身份版本、房间范围、系统参数、签到码及审计，V3 加载七角色、14 权限、初始 35 条角色权限关系和默认最大预约时长 4。这些是所有环境的应用参考目录，不是演示用户 seed。预约、审批和工单业务表随后续阶段实现。

## 身份配置与仅限本地的 seed

Compose 仅为 `platform-service` 启用 `local` profile，将 `.env` 的 `DEV_SEED_PASSWORD` 传为 `CAMPUS_SEED_PASSWORD`。该密码须为 24–72 个 UTF-8 字节，生成器使用 48 位十六进制随机字符串。应用生成 BCrypt cost 12 哈希，不保存或输出密码明文。其他 profile 不加载 seed，也不要求演示密码。

local 演示集合保持 5 个用户、5 条用户角色映射、2 栋楼、4 间房间、40 个座位；每间房 10 个座位。七角色、14 权限及初始角色权限关系由 V3 参考目录加载，非 local 同样存在；不因为新增参考目录扩增演示用户。数据源位于 `services/platform-service/src/main/resources/db/seed/local/`，与 schema migration 分开。重启使用唯一约束和 `ON CONFLICT DO NOTHING` 保持幂等，也不重置现有用户的密码。修改 `DEV_SEED_PASSWORD` 不会改变已有账号密码。房间管理员没有隐式房间范围，须由系统管理员通过 API 显式授予。公开层已实现登录、JWT 和 RBAC 方案，具体字段与范围见[平台 API](../api/platform.md)；运行验证仍以阶段报告为准。

公共层所有 profile 都必须配置 `PLATFORM_JWT_SECRET`（至少 32 个 UTF-8 字节）、`PLATFORM_RESERVATION_TOKEN`、`PLATFORM_CLASSROOM_TOKEN`、`PLATFORM_MAINTENANCE_TOKEN`（彼此不同且各至少 32 字节），以及显式 `CAMPUS_BUSINESS_ZONE`。Compose 在私有 `.env` 中补齐这些值；非 local 不需要演示密码，但没有默认管理员或默认签名秘密。用户初始化及生产秘密注入/轮换须使用审阅过的运维流程。

## 数据库验证

后端套件使用真实 HTTP/PostgreSQL，覆盖原有迁移和账号隔离，以及 Phase 3 身份、权限、房间范围、最后系统管理员、审计、主数据与每日签到码。当前尚在验证，数量和结果以当次 Surefire/阶段报告为准：

```sh
./scripts/test-backend.sh
python3 scripts/check-database.py --phase3
```

第二条命令针对当前 Compose 数据库，使用容器内凭据经 TCP 验证八个账号；它检查 schema 所有者、角色属性、跨 schema 拒绝、应用 DDL/迁移历史/审计权限、公共层 V3 和其他三个 V1，以及演示基线。失败时退出非零，不打印凭据或用户哈希。它适用于当前阶段的本地演示基线；通过管理 API 创建额外合法数据或调整权限后，应按脚本能力和实际数据判断，不能把变化当作 seed 自动扩充。

保留 volume 的重启验证可保存非敏感计数及密码哈希指纹，再比较：

```sh
mkdir -p .local
python3 scripts/check-database.py --phase3 --snapshot .local/phase-3-database.json
docker compose down
docker compose up -d --wait --wait-timeout 180
python3 scripts/check-database.py --phase3 --compare .local/phase-3-database.json
```

快照只包含计数、版本和指纹，放在 Git 忽略的 `.local/`；不提交原始用户密码哈希。实际验收结果见 [Phase 3 记录](../plans/phase-3.md)，本手册描述可复现步骤。

## 已有 volume 与空库重放

官方初始化脚本只在 PostgreSQL 数据目录为空时执行。Phase 2 数据库已经有八账号和四 schema，直接启动新版公共层即可按 Flyway 执行 V2/V3。仅更新 `.env` 不会创建 Phase 1 旧 volume 中缺失的服务账号，也不会轮换已有数据库角色密码。需要保留旧数据时先备份并制定显式升级方案，不能通过重跑初始化脚本手改旧库权限。

当前阶段的空库验收仅可用于确认没有需保留业务数据的本项目开发库。核实当前目录和 `docker compose ps` 属于 `campus-resource-platform`，需要的本地数据已有备份后，才执行以下删除 volume 的命令；它会清空本项目数据库：

```sh
docker compose down -v
./scripts/init-env.sh
docker compose up -d --wait --wait-timeout 180
python3 scripts/check-database.py --phase3
```

这里使用已构建镜像重放；源码有改动时先运行 `./scripts/build.sh`。不对其他项目执行停止或 volume 删除操作。

## 容器与运行端点

容器与端点：

| 服务 | 容器内端口 | 检查方式 |
|---|---|---|
| postgres | 5432 | pg_isready |
| platform-service | 8080 | /actuator/health、/actuator/info |
| reservation-service | 8080 | /actuator/health、/actuator/info |
| classroom-service | 8080 | /actuator/health、/actuator/info |
| maintenance-service | 8080 | /actuator/health、/actuator/info |
| web | 8080 | /healthz |
| nginx | 8080 | /healthz |

网关为 GET `/status/{platform,reservation,classroom,maintenance}/{health,info}` 代理运行信息；另将 `/api/v1/` 代理至公共层，由后端执行登录与权限校验。`/internal`、`/actuator`、未列出的 `/status` 和其他 API 版本仍返回 404。M1/M2/M3 目前仍只允许 GET health/info，不开放后续业务。公共层内部 API 仅走 backend 网络，使用调用方各自的 opaque secret，不能用用户 JWT 替代。

```sh
python3 scripts/smoke-phase-1.py --platform-api --expected-commit "$(git rev-parse HEAD)"
```

如构建时工作区有修改，传入构建日志中的 `<SHA>-dirty`。自定义端口另传 `--url http://127.0.0.1:<port>`。脚本要求七个容器全部 healthy，核对真实 SHA/版本/时间、只读路由、隐藏路径、Web 构建资源与端口暴露；失败退出非零。

## 公共层 API 验证

```sh
python3 scripts/smoke-phase-3.py --expected-commit "$(git rev-parse HEAD)"
```

脚本从私有 `.env` 读取本地演示凭据，不打印密码、JWT 或签到码。源码 dirty 时传构建时的 `<SHA>-dirty`；自定义入口可传 `--url`。具体请求、响应、管理权限、房间范围与错误码见[平台 API](../api/platform.md)及[后端手册](backend.md#登录与平台-api)。API 修改与审计在同一事务内执行；当前无硬删除接口，停用用户或空间并不删除历史审计。

此阶段提供 D API，Web 仍为工程运行状态页；完整业务前端属于后续阶段。通知投递、预约与实际签到、审批、工单没有实现，不能把成功读取签到码或参数当作完成业务流程。

## 查看与停止

```sh
docker compose logs --tail=100 platform-service
docker compose stop
docker compose start
docker compose down
```

普通 `down` 保留 PostgreSQL volume。删除 volume 只按上文空库重放步骤执行。这些命令均按本项目 Compose 名称操作。

当前 health 包含真实数据源连接检查，详情不对外暴露；它不代表尚未实现的业务流程可用。此环境用于本地开发和验证；JWT/RBAC 方案已在本阶段实现，生产 HTTPS、密钥运维、CI/CD 和云部署仍按后续阶段实施。
