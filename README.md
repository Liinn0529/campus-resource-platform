# 校园资源服务平台

校园自习座位预约（M1）、整教室预约审批（M2）、物业报修（M3）和公共层（D）的 Monorepo。

**当前交付：Phase 3 公共层，功能与本地运行验收完成，等待 Phase 4 确认。** 已实现登录/JWT/RBAC、用户和角色权限、空间主数据、系统参数、每日房间签到码及审计。后端 49 项、前端 6 项与 lint/类型检查/构建通过；保留原数据库升级、七容器健康及真实 API/权限验收通过，见 [Phase 3 记录](docs/plans/phase-3.md)和[平台 API 契约](docs/api/platform.md)。Phase 2 的独立 schema、账号、Flyway 和最小 seed 验收保留在[历史记录](docs/review/phase-2-validation.md)。预约、实际签到、审批和报修业务尚未实现。

## 启动与验证

Phase 3 代码位于 `feature/phase-3-platform`，基于 Phase 2 分支提交；已通过验证的分项分别上传 GitHub，保留[堆叠草稿 PR #4](https://github.com/Liinn0529/campus-resource-platform/pull/4)人工审阅，未自动合并。

```sh
git clone --branch feature/phase-3-platform https://github.com/Liinn0529/campus-resource-platform.git
cd campus-resource-platform
./scripts/dev-up.sh
```

需要 Docker（含 Compose）、Git 和 OpenSSL。脚本补齐私有 `.env` 中缺少的随机密码、JWT 签名秘密和三个独立内部调用方凭据，保留已有非空值，再构建镜像并等待七个容器健康。数据库初始化脚本只在空 volume 上创建账号和 schema；已有数据库先阅读[升级说明](docs/runbooks/local-development.md#已有-volume-与空库重放)。打开 <http://127.0.0.1:18080> 查看实际运行状态与构建版本；平台公开 API 通过同一网关的 `/api/v1/` 访问，内部 API 不经网关公开。数据库和后端不发布宿主机端口。

```sh
./scripts/test-backend.sh
python3 scripts/check-database.py --phase3
python3 scripts/smoke-phase-1.py --platform-api --expected-commit "$(git rev-parse HEAD)"
python3 scripts/smoke-phase-3.py --expected-commit "$(git rev-parse HEAD)"
```

后端已实际通过 49 项真实 HTTP/PostgreSQL 测试（0 失败/错误/跳过）；`check-database.py` 验证 Compose 数据库的账号隔离、迁移版本和演示库初始数据。运行端点检查继续使用 `smoke-phase-1.py`，公共层业务检查使用 `smoke-phase-3.py`，后者从私有 `.env` 读取本地演示凭据而不输出 token。这些命令已在本轮实际执行通过；源码更新后仍应重新执行，不能把历史记录当作新版本结果。登录示例见[后端手册](docs/runbooks/backend.md#登录与平台-api)。

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
7. [Phase 3 计划](docs/plans/phase-3.md)、[平台 API](docs/api/platform.md)、[运行端点契约](docs/api/runtime.md)，以及历史 [Phase 2](docs/plans/phase-2.md) / [Phase 1](docs/plans/phase-1.md) / [Phase 0](docs/plans/phase-0.md) 记录。

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

后端使用 Java 21、Spring Boot 3.5.16、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、JUnit 5、Testcontainers、JaCoCo；前端使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest；本地容器使用 Docker、Compose、Nginx。四个后端提供真实 health/info；公共层额外提供明确授权的 D API，M1/M2/M3 仍保持工程与迁移基线。

四服务共用一个 PostgreSQL 实例，各自使用 `<schema>_app` 运行应用，以 `<schema>_migrator` 执行本服务的 Flyway 迁移。应用账号无 DDL 和迁移历史表权限，不能修改或删除审计记录；迁移账号不能访问其他服务 schema。公共层迁移至 V3，其余服务仍为 V1；已发布迁移不可改写。跨服务只通过 HTTP。

七种角色、14 项权限及初始 35 条角色权限映射属于 V3 的应用参考目录，所有环境均加载；其中包含后续模块所需权限名称，不代表相应业务已经实现。`local` profile 另幂等写入原有 5 个演示用户、2 栋楼、4 间房间和每间 10 个座位，不扩充 seed，也不默认授予房间管理范围。演示密码由私有 `.env` 生成并保存为 BCrypt；重启不会重设已有密码。非本地 profile 不加载演示用户和空间，不要求演示密码，但必须提供数据源、迁移、JWT、内部调用方秘密及业务时区配置。

用户管理以停用替代删除；空间支持新增、修改和启用状态，本阶段不提供硬删除。全局管理要求系统管理员及对应权限；房间管理员只管理显式授权的房间。参数与签到码能力不创建预约或实际签到记录。GitHub Actions、云部署、M1 占用账本/房间锁及 M2/M3 幂等业务 API 仍由后续阶段交付；首版不用 Redis。

## 贡献与后续工作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。每个已验证、可运行的分项单独提交并同步 GitHub，阶段结束保留草稿 PR 等待人工审阅。Phase 3 完成后停止，等待明确确认再进入 Phase 4；完整后续范围保留在需求基线。

尚未选择开源许可证；上传仓库不自动授予额外使用许可。
