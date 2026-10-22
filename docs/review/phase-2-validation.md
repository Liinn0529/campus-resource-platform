# Phase 2 数据库验收记录

记录时间：2026-10-22（Asia/Shanghai）。用户在 Phase 1 交付后确认继续，本阶段仅完成数据库基础，结束后停止等待 Phase 3。schema 方案通过草稿 PR 交人工审阅，测试通过不表示已获人工批准或合并。

## 修改与关键设计

| 文件/目录 | 本阶段交付 |
|---|---|
| `deploy/postgres/010-service-databases.sh`、`compose.yaml` | 空库创建四 schema、八个独立 LOGIN 角色；每服务 app/migrator 分离；只用私有环境凭据 |
| `pom.xml`、`services/Dockerfile`、四份 `application.yml` | 接入 PostgreSQL、MyBatis、Flyway、Testcontainers；迁移以独立账号运行；健康包含真实数据源检查 |
| 四服务 `src/main/resources/db/migration/V1__*.sql` | 各自版本化；D 八张基础表，其他服务只建迁移/权限基线；app 无迁移历史权限 |
| D 的 `LocalDemoSeed.java`、`application-local.yml`、六份 `db/seed/local/*.sql` | 仅 local 执行；参数化 SQL、BCrypt(12)、事务含资源读取失败回滚、自然唯一键幂等；不重置已有数据/密码 |
| `tests/java/.../DatabaseTestSupport.java`、四份 `RuntimeEndpointsTest.java`、`NonLocalDatabaseTest.java` | 复用生产引导脚本，真实 PostgreSQL 验证迁移、隔离、seed 及非 local 行为 |
| `scripts/init-env.sh`、`.env.example`、`scripts/test-backend.sh`、`scripts/check-database.py` | 补齐缺失秘密、保留非空值；Docker 测试；对实际 Compose 库检查身份/权限/迁移/精确数据和重启指纹 |
| Web 阶段标识、README、贡献文档、运行手册、架构/需求/伦理记录 | 标识实际 Phase 2 范围、复现步骤、证据与后续边界 |

四个 schema owner 是各自 migrator；app 只有本 schema USAGE、表 SELECT/INSERT/UPDATE/DELETE 和序列 USAGE/SELECT，无 DDL、角色成员资格或跨 schema 权限。PUBLIC 数据库 CONNECT/TEMP 与 public schema 权限撤销。每份 V1 撤销 app 对 Flyway 历史的全部权限。服务进程持有自己的迁移凭据，账号限制不等同于进程无法取得这些凭据。

D 仅创建 `user/role/permission/user_role/role_permission/building/room/seat`。精确 seed 为 5 用户、7 角色、5 用户角色关系、2 楼、4 房、40 座；permission/role_permission 为空。非 local 模式八表为空且不要求演示密码。没有为审批扩增用户或追加角色；其他业务表按 [DATA_OWNERSHIP](../architecture/DATA_OWNERSHIP.md) 留对应阶段。

## 已执行命令与结果

以下在仓库根目录执行。宿主未安装 Java/Maven，后端使用 Docker 的 Maven 3.9.11 / Java 21.0.9。机器专属代理与工具路径只存在仓库外临时环境，未写入项目或 GitHub。

| 命令/操作 | 实际结果 |
|---|---|
| `./scripts/test-backend.sh` | Maven verify 退出 0；23 项 JUnit 通过，0 失败/错误/跳过；四个 JAR 与 JaCoCo 报告生成 |
| `pnpm --filter @campus/web lint` | 退出 0 |
| `pnpm --filter @campus/web test` | 原有 6 项前端测试全部通过 |
| `pnpm --filter @campus/web build` | vue-tsc 类型检查与 Vite 生产构建成功 |
| `./scripts/build.sh platform-service reservation-service classroom-service maintenance-service`、`./scripts/build.sh web` | 五个应用镜像从源码实际构建成功 |
| `docker compose config --quiet` | 配置验证成功，不输出私有环境值 |
| 空卷前只读查询 `information_schema.tables` | 本项目旧开发库用户表数为 0，无需保留的业务数据 |
| `docker compose down -v` | 只删除 campus-resource-platform 的容器、网络和 postgres-data 卷，未操作其他项目 |
| `docker compose up -d --wait --wait-timeout 180` | 新卷自动执行角色/schema 引导及四个 V1；七容器全部 healthy |
| `python3 scripts/smoke-phase-1.py --expected-commit ecf9af24591a369501df3aa464ce6b370aae24ef-dirty` | 退出 0；四组真实 health/info、七容器、网关只读/隐藏路径、Web 资源与路由、loopback 端口边界均通过 |
| `python3 scripts/check-database.py --snapshot <仓库外临时快照>` | 退出 0；八账号经 TCP 验证、四 schema owner/V1、跨 schema 和 app DDL/history 拒绝、精确 seed 通过 |
| `docker compose down` 后 `docker compose up -d --wait --wait-timeout 180` | 保留卷重建所有本项目容器，七容器再次健康；Flyway 校验及 local seed 再次执行 |
| `python3 scripts/check-database.py --compare <同一快照>` | 退出 0；全部数据计数、迁移版本及既有密码哈希指纹完全一致 |
| 停止本项目 PostgreSQL 后分别请求四服务健康接口 | 直连四服务均返回 HTTP 503；网关因 5 秒 upstream 时限返回 504，没有显示虚构 UP |
| 故障检查后 `docker compose up -d --wait --wait-timeout 180` | PostgreSQL 恢复后七容器重新全部 healthy；其他项目 PostgreSQL 仍运行 |
| 临时副本执行 `init-env.sh` 两次 | 原有密码/端口保留，缺失与空密码补齐，第二次文件字节不变，权限 600 |
| 归档、需求、链接、格式与私有环境检查 | 原始归档 SHA-256 未变；186 个稳定 ID 两表顺序/集合一致；15 个 OQ 保留；文件 UTF-8/LF、Shell 语法、POM XML、相对链接及 `git diff --check` 通过；真实私有密码未出现在拟提交文件 |

首次联调镜像对应上述真实 dirty SHA，构建时间 `2026-10-22T05:07:31Z`。最终交付按 GitHub 提交重新构建，并在仓库外的交付报告记录最终完整 SHA、时间及再次验证结果，避免在提交前伪造其自身 SHA。

## 后端测试明细

| 测试 | 数量 | 核心行为 |
|---|---|---|
| D RuntimeEndpointsTest | 7 | 4 项运行接口、真实 Flyway/权限隔离、精确 seed/BCrypt、重复 seed 保留密码 |
| D NonLocalDatabaseTest | 1 | 无演示密码、无 seed bean，迁移后八表为空 |
| M1/M2/M3 RuntimeEndpointsTest | 每服务 5，共 15 | 各 4 项运行接口及 1 项数据库迁移/隔离 |

隔离测试在临时真实库内由各 owner 建立实际探针表，验证本 app 能 DML、app 与 migrator 对其他 schema 的 SELECT/INSERT/UPDATE/DELETE 均被拒绝；并验证 app 建表、切换 migrator、读取/更改/删除迁移历史被拒绝。不是只检测配置字符串或假表错误。Testcontainers 使用生产引导脚本，23 项均实际运行，不依赖跳过测试获得成功。

## 实际问题与边界

Docker 29 需要兼容的 Testcontainers 客户端；本阶段使用 1.21.4 并实际成功运行 PostgreSQL/Ryuk。构建初次需下载新增 Maven 依赖，共用缓存后后续服务构建成功；未通过删除测试或绕过校验加速。

故障探针首次假设网关总返回 JSON 503，实际收到 504 HTML，故该次探针失败。核对网关 5 秒超时与连接池等待后，按既有契约分别检查直连 503 和网关失败状态，实际四服务均符合；没有将首次失败记为通过。健康详情仍不对外暴露，网关可在后端等待数据库时先返回超时错误。

MyBatis 当前尚无业务 Mapper，因此扫描器有无 Mapper 提示；测试依赖仍有动态 agent 提示。这些提示未计作业务能力或 CI 通过。当前只有本地执行，GitHub Actions、required checks、生产部署和完整业务 E2E 留后续阶段。

需求当前为 18 DONE / 158 TODO / 10 PARTIAL / 0 BLOCKED，共 186。REQ-PH-002 本阶段适用验收完成；持续安全/迁移约束及完整模型保持 TODO/PARTIAL。OQ-04/07/11 的业务问题未擅自决定，OQ-13 只解决当前账号和 D 基础表方案。登录、JWT、RBAC、主数据管理、签到码、预约/占用/房间锁、审批、报修均尚未交付。

代码基础分项提交：`1098586053b4bfc17d29d5b86251f32b9692199e`。最终阶段分支为 `feature/phase-2-database`，对应 [草稿 PR #3](https://github.com/Liinn0529/campus-resource-platform/pull/3)，基于未合并的 Phase 1 分支，不自动合并到 main。最终文档/页面分项建议并使用 `docs(database): record fresh-start verification and phase 2 handoff`。
