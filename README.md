# 校园资源服务平台

校园自习座位预约（M1）、整教室预约审批（M2）、物业报修（M3）和公共层（D）的 Monorepo。

**当前交付：Phase 2 数据库，本阶段验收完成，等待 Phase 3 确认。** 四服务已接入独立 schema、应用/迁移账号和 Flyway；公共层提供仅限本地的最小 seed。23 项后端测试、空卷自动迁移及保留卷重启验证通过，见 [Phase 2 验证记录](docs/review/phase-2-validation.md)。当前尚无登录、预约、审批或报修业务。

## 启动与验证

Phase 2 代码位于 `feature/phase-2-database` 分支，使用草稿 PR 审阅，未自动合并到 main。

```sh
git clone --branch feature/phase-2-database https://github.com/Liinn0529/campus-resource-platform.git
cd campus-resource-platform
./scripts/dev-up.sh
```

需要 Docker（含 Compose）、Git 和 OpenSSL。脚本补齐私有 `.env` 中缺少的随机密码，保留已有非空凭据，再构建镜像并等待七个容器健康。数据库初始化脚本只在空 volume 上创建账号和 schema；从 Phase 1 的旧 volume 升级前先阅读[本地手册](docs/runbooks/local-development.md#已有-volume-与空库重放)。打开 <http://127.0.0.1:18080> 查看实际运行状态与构建版本。数据库和后端不发布宿主机端口。

```sh
./scripts/test-backend.sh
python3 scripts/check-database.py
python3 scripts/smoke-phase-1.py --expected-commit "$(git rev-parse HEAD)"
```

后端套件包含 23 项真实 HTTP/PostgreSQL 测试；`check-database.py` 验证当前 Compose 数据库的账号隔离、迁移版本和精确 seed 数量。`smoke-phase-1.py` 继续检查运行端点、网关和七容器健康。命令成功退出才表示对应检查通过，当前阶段验收不由 README 预先宣称。

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
7. [Phase 2 记录](docs/plans/phase-2.md)、[运行端点契约](docs/api/runtime.md)，以及历史 [Phase 1](docs/plans/phase-1.md) / [Phase 0](docs/plans/phase-0.md) 记录。

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
│   ├── api/             # 当前 health/info 契约
│   └── runbooks/        # 本地启动与验证
├── contracts/openapi/   # 后续契约占位
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

后端使用 Java 21、Spring Boot 3.5.16、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、JUnit 5、Testcontainers、JaCoCo；前端使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest；本地容器使用 Docker、Compose、Nginx。每个后端提供真实 health/info；只读运行端点以外的请求默认拒绝。

四服务共用一个 PostgreSQL 实例，各自使用 `<schema>_app` 运行应用，以 `<schema>_migrator` 执行本服务的 Flyway 迁移。应用账号无 DDL 和迁移历史表权限；迁移账号不能访问其他服务 schema。跨服务只通过 HTTP。M1/M2/M3 当前仅建立迁移基线，业务表随对应阶段交付。

公共层的 `local` profile 幂等写入 5 个演示用户、7 个角色、2 栋楼、4 间房间和每间 10 个座位，权限和角色权限映射保持为空。演示密码由私有 `.env` 生成，保存为 BCrypt；重启不会重设现有密码。其他 profile 不加载 seed，也不要求演示密码。完整 OpenAPI、业务 Playwright 测试、GitHub Actions 和华为云部署按后续阶段实现。M1 占用账本和房间锁、M2/M3 幂等 API 仍属于后续业务阶段；首版不用 Redis。

## 贡献与后续工作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。每个已验证、可运行的分项单独提交并同步 GitHub，阶段结束保留草稿 PR 等待人工审阅。Phase 2 完成后停止，完整 Phase 3–15 范围保留在需求基线，只有明确确认才进入下一阶段。

尚未选择开源许可证；上传仓库不自动授予额外使用许可。
