# 校园资源服务平台

校园自习座位预约（M1）、整教室预约审批（M2）、物业报修（M3）和公共层（D）的 Monorepo。

**当前交付：Phase 0 项目治理和需求基线，等待人工确认。** 当前没有可运行应用、数据库或部署环境；不能执行 Maven、pnpm 或 Docker 启动。Phase 1 尚未开始。

## 阅读顺序

1. [AGENTS.md](AGENTS.md)：阶段门禁、开发约束、Git 和完成规则。
2. [REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md)：编号需求、来源、阶段和验收条件。
3. [TRACEABILITY.md](docs/requirements/TRACEABILITY.md)：每条需求的实现和验证状态。
4. [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)：服务边界、技术栈和调用关系。
5. [DATA_OWNERSHIP.md](docs/architecture/DATA_OWNERSHIP.md)：schema、账号和数据所有权。
6. [ADR-001](docs/adr/ADR-001-service-boundary.md)：服务划分决策，待人工审阅。
7. [Phase 0 交付记录](docs/plans/phase-0.md)与[验证记录](docs/review/phase-0-validation.md)。

原始依据保存在 [CODEX_EXECUTION_PROMPT.md](docs/requirements/CODEX_EXECUTION_PROMPT.md)，不将文档中的后续阶段任务视为提前执行授权。

## 仓库布局

```text
campus-resource-platform/
├── AGENTS.md
├── README.md
├── CONTRIBUTING.md
├── docs/
│   ├── requirements/    # 原始依据、编号需求、追踪表
│   ├── architecture/    # 架构和数据所有权
│   ├── adr/             # 架构决策记录
│   ├── plans/           # 阶段计划及交付记录
│   ├── review/          # 验证证据
│   ├── ethics/          # 真实伦理问题记录
│   ├── api/             # 后续 API 文档占位
│   └── runbooks/        # 后续运行手册占位
├── contracts/openapi/   # 后续契约占位
├── services/
│   ├── platform-service/
│   ├── reservation-service/
│   ├── classroom-service/
│   └── maintenance-service/
├── apps/web/
├── deploy/{compose,nginx,huawei}/
├── scripts/
└── .github/             # PR 模板、CODEOWNERS、工作流目录占位
```

空目录使用 `.gitkeep` 保留。原文总体目录中的 `pom.xml`、`package.json`、`pnpm-workspace.yaml`、`.env.example` 在 Phase 1 根据真实工程配置创建，Phase 0 不添加空壳构建文件。`.github/workflows/` 当前没有工作流，CI 在 Phase 11 交付。

## 基线

四个后端服务：Java 21、Spring Boot 3.x、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、OpenAPI 3、JUnit 5、Testcontainers、JaCoCo。前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest、Playwright。部署：Docker、Compose、Nginx、GitHub Actions，后续华为云 SWR/ECS/RDS PostgreSQL。

一个 PostgreSQL 实例，四个独立 schema/账号；跨服务只通过 HTTP。M1 统一维护占用账本并按房间加锁，M2/M3 通过幂等 API 创建和释放占用。首版不用 Redis。

## 贡献与后续工作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。Phase 0 分支提交后以草稿 PR 审阅；只有明确收到人工确认才进入下一阶段。完整 Phase 1–15 范围保留在需求基线，状态均不代表实现完成。

尚未选择开源许可证；上传仓库不自动授予额外使用许可。
