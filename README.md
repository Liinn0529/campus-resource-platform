# 校园资源服务平台

校园自习座位预约（M1）、整教室预约审批（M2）、物业报修（M3）和公共层（D）的 Monorepo。

**当前交付：Phase 1 工程骨架，已完成本阶段验收，等待 Phase 2 确认。** 四个后端服务、Vue 状态页及本地 Compose 的范围和结果见[阶段记录](docs/plans/phase-1.md)。当前尚无登录、预约、审批或报修业务。

## 启动与验证

Phase 1 代码位于 `feature/phase-1-scaffold` 分支，使用草稿 PR 审阅，未自动合并到 main。

```sh
git clone --branch feature/phase-1-scaffold https://github.com/Liinn0529/campus-resource-platform.git
cd campus-resource-platform
./scripts/dev-up.sh
```

需要 Docker（含 Compose）、Git 和 OpenSSL。脚本生成私有 `.env`、构建镜像并等待七个容器健康。打开 <http://127.0.0.1:18080> 查看实际运行状态与构建版本。数据库和后端不发布宿主机端口。

```sh
./scripts/test-backend.sh
python3 scripts/smoke-phase-1.py --expected-commit "$(git rev-parse HEAD)"
```

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
7. [Phase 1 记录](docs/plans/phase-1.md)、[运行端点契约](docs/api/runtime.md)，以及历史 [Phase 0 记录](docs/plans/phase-0.md)。

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
├── deploy/{compose,nginx,huawei}/
├── scripts/             # 环境生成、构建、后端验证、HTTP smoke
└── .github/             # PR 模板、CODEOWNERS、工作流目录占位
```

尚未实现的目录使用 `.gitkeep` 保留。`.github/workflows/` 当前没有工作流，CI 在 Phase 11 交付；本地验证不能称为 GitHub CI 已通过。

## 基线

Phase 1 已配置 Java 21、Spring Boot 3.5.16、Spring Security、Maven、Bean Validation、JUnit 5、JaCoCo；前端使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest；本地容器使用 Docker、Compose、Nginx。每个后端提供真实 health/info；只读运行端点以外的请求默认拒绝。

MyBatis/Flyway/PostgreSQL 驱动只在 Maven dependencyManagement 中预留版本，尚未接入运行时。数据库迁移、完整 OpenAPI、Testcontainers、业务 Playwright 测试、GitHub Actions 和华为云部署在各自后续阶段实现。

目标架构是一个 PostgreSQL 实例、四个独立 schema/账号，跨服务只通过 HTTP；M1 统一维护占用账本并按房间加锁，M2/M3 通过幂等 API 创建和释放占用。Phase 1 数据库仅为空实例，后端不接入数据库；Phase 2 再创建隔离 schema/账号。首版不用 Redis。

## 贡献与后续工作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。每个已验证、可运行的分项单独提交并同步 GitHub，阶段结束保留草稿 PR 等待人工审阅。完整 Phase 2–15 范围保留在需求基线，只有明确确认才进入下一阶段。

尚未选择开源许可证；上传仓库不自动授予额外使用许可。
