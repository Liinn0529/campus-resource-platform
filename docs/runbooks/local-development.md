# Phase 2 本地容器与数据库运行

需要 Git、Docker Engine/Desktop、Docker Compose 插件和 OpenSSL；完整验收另需 Python 3.9+。推荐至少 4 GB Docker 可用内存。前端独立开发另见[前端手册](frontend.md)，无需在宿主机安装 Java/Maven。

在仓库根目录执行：

```sh
./scripts/dev-up.sh
```

脚本将随机密码写入权限 600 的私有 `.env`，保留已有非空值并补齐缺失或空的凭据；之后以当前真实 Git SHA 和 UTC 时间构建五个应用镜像，再启动 Compose 并等待健康检查。已有 Phase 1 volume 不会自动运行新的初始化脚本，升级前须处理下文的旧 volume。

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
python3 scripts/check-database.py
```

本地入口：<http://127.0.0.1:18080>。端口冲突时修改 `.env` 的 `WEB_PORT` 后重新 `docker compose up -d`。只绑定 loopback，数据库与四服务没有宿主机端口。

`build.sh` 对未提交修改在 SHA 后追加 `-dirty`；新源码修改后必须重建，单独 `up -d` 只启动已有镜像。Maven Enforcer 拒绝缺失或无效 SHA，避免生成未知版本。`test-backend.sh` 使用 Maven/Java 21 容器运行完整 `verify`，依赖缓存在项目专属 Docker volume，真实 PostgreSQL 测试通过挂载 Docker socket 启动隔离容器。默认 socket 为 `/var/run/docker.sock`；自定义安装可设置 `TESTCONTAINERS_SOCKET_PATH`。后端测试要求 Docker 可访问，不会以跳过测试代替数据库验证。直接使用宿主 Maven 时：

```sh
mvn verify -DgitCommit="$(git rev-parse HEAD)" -DbuildTime="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
```

该直接命令只适用于工作区无未提交修改时。Java 必须为 21、Maven 为 3.9.x。

## 凭据、schema 与迁移

`.env.example` 仅列出变量名，不能当作可用密码配置。`init-env.sh` 为 PostgreSQL 管理员和八个服务账号分别生成随机密码，另生成一个供五个演示用户使用的 `DEV_SEED_PASSWORD`；任何命令输出和提交内容均不应包含这些值。

| Schema | 应用账号 / 密码变量 | 迁移账号 / 密码变量 |
|---|---|---|
| platform | platform_app / PLATFORM_DB_PASSWORD | platform_migrator / PLATFORM_FLYWAY_PASSWORD |
| reservation | reservation_app / RESERVATION_DB_PASSWORD | reservation_migrator / RESERVATION_FLYWAY_PASSWORD |
| classroom | classroom_app / CLASSROOM_DB_PASSWORD | classroom_migrator / CLASSROOM_FLYWAY_PASSWORD |
| maintenance | maintenance_app / MAINTENANCE_DB_PASSWORD | maintenance_migrator / MAINTENANCE_FLYWAY_PASSWORD |

`POSTGRES_PASSWORD` 只供数据库 bootstrap 管理员使用；服务不使用该账号。官方 PostgreSQL entrypoint 在空 volume 上执行 `deploy/postgres/010-service-databases.sh`，建立四个 schema 及八个无角色成员关系的普通登录账号。每个迁移账号拥有自己的 schema，应用账号只有自身 schema 的 USAGE、表的 SELECT/INSERT/UPDATE/DELETE 与序列的 USAGE/SELECT；应用账号没有 DDL、TRUNCATE 或 Flyway 历史表访问权限。PUBLIC 的数据库 CONNECT/TEMP 和 public schema 权限已撤销，账号不能访问其他服务 schema。

每个服务以应用凭据建立数据源，另以迁移凭据运行 `src/main/resources/db/migration/` 中的 Flyway migration。配置固定自身 schema，禁止自动创建 schema，关闭 Flyway clean；已发布迁移保持不可修改。当前四服务各有 V1，公共层建立最小身份/角色/空间表，其余服务仅建立迁移基线。完整业务表随后续阶段实现。

## 仅限本地的 seed

Compose 仅为 `platform-service` 启用 `local` profile，将 `.env` 的 `DEV_SEED_PASSWORD` 传为 `CAMPUS_SEED_PASSWORD`。该密码须为 24–72 个 UTF-8 字节，生成器使用 48 位十六进制随机字符串。应用生成 BCrypt cost 12 哈希，不保存或输出密码明文。其他 profile 不加载 seed，也不要求演示密码。

最小集合是 5 个演示用户、7 个角色、5 条用户角色映射、2 栋楼、4 间房间、40 个座位；每间房 10 个座位。`permission` 和 `role_permission` 为空。数据源位于 `services/platform-service/src/main/resources/db/seed/local/`，与 schema migration 分开。重启使用唯一约束和 `ON CONFLICT DO NOTHING` 保持幂等，也不重置现有用户的密码。修改 `DEV_SEED_PASSWORD` 不会改变已有账号密码。当前尚无登录 API，这些数据不表示登录/RBAC 业务已完成。

## 数据库验证

后端套件包含 23 项真实 HTTP/PostgreSQL 测试，覆盖 Flyway 自动迁移、应用与迁移账号隔离、应用 DDL/迁移历史访问拒绝、最小 seed、幂等且不重置旧密码，以及非 `local` profile 的无 seed 启动：

```sh
./scripts/test-backend.sh
python3 scripts/check-database.py
```

第二条命令针对当前 Compose 数据库，使用容器内凭据经 TCP 验证八个账号；它检查 schema 所有者、角色属性、跨 schema 拒绝、应用 DDL 和迁移历史权限、四个 V1 与精确数据量。失败时退出非零，不打印凭据或用户哈希。它检查最小 seed 的精确数量，只适用于当前阶段的本地演示库。

保留 volume 的重启验证可保存非敏感计数及密码哈希指纹，再比较：

```sh
mkdir -p .local
python3 scripts/check-database.py --snapshot .local/phase-2-database.json
docker compose down
docker compose up -d --wait --wait-timeout 180
python3 scripts/check-database.py --compare .local/phase-2-database.json
```

快照只包含计数、版本和指纹，放在 Git 忽略的 `.local/`；不提交原始用户密码哈希。实际验收结果见 [Phase 2 记录](../plans/phase-2.md)，本手册描述可复现步骤。

## 已有 volume 与空库重放

官方初始化脚本只在 PostgreSQL 数据目录为空时执行。仅更新 `.env` 不会创建旧 volume 中缺失的服务账号，也不会轮换已有数据库角色密码。需要保留数据时先备份并制定显式升级方案，不能通过重跑初始化脚本手改旧库权限。

当前阶段的空库验收仅可用于确认没有需保留业务数据的本项目开发库。核实当前目录和 `docker compose ps` 属于 `campus-resource-platform`，需要的本地数据已有备份后，才执行以下删除 volume 的命令；它会清空本项目数据库：

```sh
docker compose down -v
./scripts/init-env.sh
docker compose up -d --wait --wait-timeout 180
python3 scripts/check-database.py
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

网关只为 GET `/status/{platform,reservation,classroom,maintenance}/{health,info}` 代理后端运行信息。`/internal`、`/actuator`、`/api` 及未列出的 `/status` 路径均返回 404。后端自身只允许 GET health/info，其余请求由 Spring Security 拒绝。

```sh
python3 scripts/smoke-phase-1.py --expected-commit "$(git rev-parse HEAD)"
```

如构建时工作区有修改，传入构建日志中的 `<SHA>-dirty`。自定义端口另传 `--url http://127.0.0.1:<port>`。脚本要求七个容器全部 healthy，核对真实 SHA/版本/时间、只读路由、隐藏路径、Web 构建资源与端口暴露；失败退出非零。

## 查看与停止

```sh
docker compose logs --tail=100 platform-service
docker compose stop
docker compose start
docker compose down
```

普通 `down` 保留 PostgreSQL volume。删除 volume 只按上文空库重放步骤执行。这些命令均按本项目 Compose 名称操作。

当前 health 包含真实数据源连接检查，详情不对外暴露；它不代表尚未实现的业务流程可用。此环境用于本地工程验证；生产 HTTPS、RBAC/JWT、CI/CD 和云部署按后续阶段实施。
