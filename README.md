# 校园资源服务平台

校园自习座位预约（M1）、整教室预约审批（M2）、物业报修（M3）和公共层（D）的 Monorepo。

**当前交付：Phase 4 基础预约，代码与首次本地运行验收通过，最终干净版本重建待核对。** 已实现房间/座位/可用性、创建/取消、本人历史与再预约、房间行锁和来源占用账本；保留 Phase 3 的身份权限与公共层。当前 75 个后端测试通过（完整 reactor 69 项后新增 6 项边界测试，M1 最终子集 28 项），0 失败/错误/跳过，见 [Phase 4 方案](docs/plans/phase-4.md)、[预约 API](docs/api/reservation.md)和[平台 API](docs/api/platform.md)。首次保留原卷升级、七容器健康、真实网关预约流程及数据库/密码保留检查通过，最终干净版本重建另记于[Phase 4 验证](docs/review/phase-4-validation.md)；Phase 2/3 的历史验收单独保留。实际签到、提醒、审批和报修尚未实现。

## 启动与验证

Phase 4 代码位于 `feature/phase-4-reservations`，基于 Phase 3 最终提交；已通过验证的可运行分项分别上传 GitHub，保留[堆叠草稿 PR #5](https://github.com/Liinn0529/campus-resource-platform/pull/5)人工审阅，未自动合并。前序 [Phase 3 PR #4](https://github.com/Liinn0529/campus-resource-platform/pull/4)保留。

```sh
git clone --branch feature/phase-4-reservations https://github.com/Liinn0529/campus-resource-platform.git
cd campus-resource-platform
./scripts/dev-up.sh
```

需要 Docker（含 Compose）、Git 和 OpenSSL。脚本补齐私有 `.env` 中缺少的随机密码、JWT 签名秘密和三个独立内部调用方凭据，保留已有非空值，再构建镜像并等待七个容器健康。数据库初始化脚本只在空 volume 上创建账号和 schema；已有数据库先阅读[升级说明](docs/runbooks/local-development.md#已有-volume-与空库重放)。打开 <http://127.0.0.1:18080> 查看实际运行状态与构建版本；D 与 M1 公开 API 通过同一网关的 `/api/v1/` 访问，内部 API 不经网关公开。数据库和后端不发布宿主机端口。

```sh
./scripts/test-backend.sh
python3 scripts/check-database.py --phase4
python3 scripts/smoke-phase-1.py --platform-api --expected-commit "$(git rev-parse HEAD)"
python3 scripts/smoke-phase-4.py --expected-commit "$(git rev-parse HEAD)"
```

后端当前 75 项测试通过；M1 集成测试启动真实 D JAR 子进程与 Testcontainers PostgreSQL，覆盖首次同座位并发、时间/参数、所有来源冲突、权限、释放与回滚。6 项客户端边界测试使用错误响应夹具，不能替代真实 D 集成。`check-database.py --phase4` 检查账号隔离、D V3/M1 V2、最小 seed 及预约账本/事件一致性；`smoke-phase-4.py` 先执行公共层回归，再通过实际网关完成创建/冲突/取消/再预约，最后取消本次创建记录并保留历史。脚本从私有 `.env` 读取凭据且不输出 token。上述运行命令的本轮结果见阶段记录，源码变化后应重新执行。登录示例见[后端手册](docs/runbooks/backend.md#登录与平台-api)。

前端独立开发需要 Node.js 24、pnpm 11.25.0：

```sh
pnpm install --frozen-lockfile
pnpm dev
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

完整命令、端口调整、构建元数据、dirty 标记和停止方法见[容器手册](docs/runbooks/local-development.md)及[前端手册](docs/runbooks/frontend.md)。

## 阅读顺序

1. [AGENTS.md](AGENTS.md)：阶段门禁、开发约束、Git 和完成规则。
2. [REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md)：编号需求、来源、阶段和验收条件。
3. [TRACEABILITY.md](docs/requirements/TRACEABILITY.md)：每条需求的实现和验证状态。
4. [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)：服务边界、技术栈和调用关系。
5. [DATA_OWNERSHIP.md](docs/architecture/DATA_OWNERSHIP.md)：schema、账号和数据所有权。
6. [ADR-001](docs/adr/ADR-001-service-boundary.md)：服务划分决策，待人工审阅。
7. [Phase 4 计划](docs/plans/phase-4.md)、[预约 API](docs/api/reservation.md)、[平台 API](docs/api/platform.md)、[运行端点契约](docs/api/runtime.md)，以及历史 [Phase 3](docs/plans/phase-3.md) / [Phase 2](docs/plans/phase-2.md) / [Phase 1](docs/plans/phase-1.md) / [Phase 0](docs/plans/phase-0.md) 记录。

原始依据保存在 [CODEX_EXECUTION_PROMPT.md](docs/requirements/CODEX_EXECUTION_PROMPT.md)，不将文档中的后续阶段任务视为提前执行授权。

## 仓库布局

```text
campus-resource-platform/
├── AGENTS.md
├── README.md
├── CONTRIBUTING.md
├── pom.xml              # Java 21 / Spring Boot 3.5.16 Maven reactor
├── package.json         # pnpm workspace commands
├── pnpm-lock.yaml
├── compose.yaml         # 七容器本地开发环境
├── docs/
│   ├── requirements/    # 原始依据、编号需求、追踪表
│   ├── architecture/    # 架构和数据所有权
│   ├── adr/             # 架构决策记录
│   ├── plans/           # 阶段计划及交付记录
│   ├── review/          # 验证证据
│   ├── ethics/          # 真实伦理问题记录
│   ├── api/             # 平台公开/内部 API 与 health/info 契约
│   └── runbooks/        # 本地启动与验证
├── contracts/openapi/   # 当前阶段 OpenAPI 契约
├── services/
│   ├── platform-service/
│   ├── reservation-service/
│   ├── classroom-service/
│   └── maintenance-service/
├── apps/web/            # Vue 状态页及容器镜像
├── deploy/{postgres,compose,nginx,huawei}/
├── tests/java/          # 真实 PostgreSQL 隔离验证支持
├── scripts/             # 环境生成、构建、数据库验证、HTTP smoke
└── .github/             # PR 模板、CODEOWNERS、工作流目录占位
```

尚未实现的目录使用 `.gitkeep` 保留。`.github/workflows/` 当前没有工作流，CI 在 Phase 11 交付；本地验证不能称为 GitHub CI 已通过。

## 基线

后端使用 Java 21、Spring Boot 3.5.16、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、JUnit 5、Testcontainers、JaCoCo；前端使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest；本地容器使用 Docker、Compose、Nginx。四个后端提供真实 health/info；D 提供身份与公共层 API，M1 提供基础预约 API，M2/M3 仍保持工程与迁移基线。

四服务共用一个 PostgreSQL 实例，各自使用 `<schema>_app` 运行应用，以 `<schema>_migrator` 执行本服务的 Flyway 迁移。应用账号无 DDL 和迁移历史表权限，不能修改或删除审计记录；迁移账号不能访问其他服务 schema。公共层迁移至 V3、预约服务至 V2，M2/M3 仍为 V1；已发布迁移不可改写。跨服务只通过 HTTP。

七种角色、14 项权限及初始 35 条角色权限映射属于 V3 的应用参考目录，所有环境均加载；其中包含后续模块所需权限名称，不代表相应业务已经实现。`local` profile 另幂等写入原有 5 个演示用户、2 栋楼、4 间房间和每间 10 个座位，不扩充 seed，也不默认授予房间管理范围。演示密码由私有 `.env` 生成并保存为 BCrypt；重启不会重设已有密码。非本地 profile 不加载演示用户和空间，不要求演示密码，但必须提供数据源、迁移、JWT、内部调用方秘密及业务时区配置。

用户管理以停用替代删除；空间支持新增、修改和启用状态，本阶段不提供硬删除。全局管理要求系统管理员及对应权限；房间管理员只管理显式授权的房间。参数与签到码能力不创建预约或实际签到记录。M1 经真实 D HTTP 验证身份与启用空间、读取当前最大时长，在本地房间锁事务内维护预约、占用及事件。取消仅本人且开始前，历史再预约产生新 ID；业务时间、生命周期和跨模块释放方案仍待草稿 PR 人工 review。GitHub Actions、云部署和 M2/M3 幂等业务 API 仍由后续阶段交付；首版不用 Redis。

## 贡献与后续工作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。每个已验证、可运行的分项单独提交并同步 GitHub，阶段结束保留草稿 PR 等待人工审阅。Phase 4 完成后停止，等待明确确认再进入 Phase 5；完整后续范围保留在需求基线。

尚未选择开源许可证；上传仓库不自动授予额外使用许可。
