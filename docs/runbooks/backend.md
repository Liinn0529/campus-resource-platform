# 后端数据库验证与运行

需要 Docker 和 Git。在仓库根目录执行：

```sh
./scripts/test-backend.sh
```

脚本使用 Maven 3.9.11 / Java 21 容器，注入当前实际 Git SHA、dirty 标记和 UTC 时间，执行四模块 `mvn verify`。23 个测试通过 Testcontainers 启动真实 PostgreSQL 16，执行同一份 bootstrap 和 Flyway migration，并通过真实随机端口 Tomcat 发送 HTTP，不 mock 被测逻辑。Docker socket 默认挂载 `/var/run/docker.sock`；自定义路径可设置 `TESTCONTAINERS_SOCKET_PATH`。Docker 不可用时检查失败，不跳过数据库验证。每模块生成可执行 JAR、Surefire 和 JaCoCo 报告，均被 .gitignore 排除。

数据库验证覆盖八个账号的 schema 隔离、实际表的跨 schema 读写拒绝、应用无 DDL/迁移历史访问权限，以及自身表的正常 CRUD。公共层还验证精确 seed 数量、BCrypt 密码、重复执行不增加数据或重设旧密码，以及非 `local` profile 仍能迁移且不加载 seed。

推荐通过 Compose 提供服务凭据、内部数据库网络和本地 profile。只重建并启动一个后端时，在仓库根目录执行：

```sh
./scripts/init-env.sh
./scripts/build.sh platform-service
docker compose up -d --wait --wait-timeout 180 platform-service
```

数据库须为空库 bootstrap 已成功或已有本阶段 schema。其他服务使用对应 Compose 服务名。后端没有宿主机端口，整体状态通过网关查看，接口契约见 [runtime.md](../api/runtime.md)。完整启动、旧 volume 升级和空库重放见[本地手册](local-development.md)。

宿主机直接运行 JAR 需要 Java 21，并自行提供可达的私网 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`FLYWAY_USERNAME` 和 `FLYWAY_PASSWORD`。JDBC URL 使用对应 `currentSchema`；不能以管理员或迁移账号替代应用账号。Compose 不发布数据库端口，因此不能直接将其内部 `postgres` 主机名用于宿主机 JAR。密码放在私有运行环境，不写入源码、命令行参数或日志。

服务已接入 MyBatis、PostgreSQL 驱动和 Flyway。每个服务的 V1 migration 位于自己的 `src/main/resources/db/migration/`；已发布 migration 不改写。只有公共层 `local` profile 从 `db/seed/local/` 加载最小演示数据，要求 `CAMPUS_SEED_PASSWORD`；非本地 profile 不加载 seed，也不要求该值。应用/迁移密码分别来自各服务的 `*_DB_PASSWORD` / `*_FLYWAY_PASSWORD`。当前没有登录、预约、审批或工单业务 API。

当前 Compose 数据库的验收命令为 `python3 scripts/check-database.py`。与 HTTP 网关检查 `scripts/smoke-phase-1.py` 分别执行；前者验证数据库，后者核对七容器健康和运行端点，不互相替代。
