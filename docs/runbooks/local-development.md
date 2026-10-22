# Phase 1 本地容器运行

需要 Git、Docker Engine/Desktop、Docker Compose 插件和 OpenSSL；完整验收另需 Python 3.9+。推荐至少 4 GB Docker 可用内存。前端独立开发另见[前端手册](frontend.md)，无需在宿主机安装 Java/Maven。

在仓库根目录执行：

```sh
./scripts/dev-up.sh
```

脚本生成仅本机使用的随机数据库密码，保存在权限 600 的 `.env`，已有文件保持不变；之后以当前真实 Git SHA 和 UTC 时间构建五个应用镜像，再启动 Compose 并等待健康检查。

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
```

本地入口：<http://127.0.0.1:18080>。端口冲突时修改 `.env` 的 `WEB_PORT` 后重新 `docker compose up -d`。只绑定 loopback，数据库与四服务没有宿主机端口。

`build.sh` 对未提交修改在 SHA 后追加 `-dirty`；新源码修改后必须重建，单独 `up -d` 只启动已有镜像。Maven Enforcer 拒绝缺失或无效 SHA，避免生成未知版本。`test-backend.sh` 使用 Maven/Java 21 容器运行完整 `verify`，依赖缓存在项目专属 Docker volume。直接使用宿主 Maven 时：

```sh
mvn verify -DgitCommit="$(git rev-parse HEAD)" -DbuildTime="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
```

该直接命令只适用于工作区无未提交修改时。Java 必须为 21、Maven 为 3.9.x。

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

查看与停止：

```sh
docker compose logs --tail=100 platform-service
docker compose stop
docker compose start
docker compose down
```

普通 `down` 保留 PostgreSQL volume。只在明确需要丢弃本项目本地数据库时才使用 `docker compose down -v`。这些命令均按本项目 Compose 名称操作。

当前 PostgreSQL 是空实例。四服务没有 JDBC、MyBatis、Flyway 运行依赖或数据库凭据；四 schema、独立账号、迁移和最小 seed 在 Phase 2 审查后实现。当前 health 表示应用进程健康，不表示数据库或业务可用。此环境用于本地工程验证；生产 HTTPS、RBAC/JWT、CI/CD 和云部署按后续阶段实施。
