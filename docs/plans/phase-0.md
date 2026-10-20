# Phase 0：项目治理和需求基线

日期：2026-10-20（Asia/Shanghai）。用户授权：只完成 Phase 0，并上传至 `Liinn0529/campus-resource-platform` 公开仓库；完成后停止，等待确认。

## 范围与执行计划

| 步骤 | 交付物/动作 | 验收 |
|---|---|---|
| 1 | 扫描当前目录和上级规则 | 初始目录只有空 work/、outputs/；不是 Git 仓库；未发现适用的上级 AGENTS.md，无已有代码需要修改 |
| 2 | 建立仓库目录、AGENTS、README | 根目录和所有预定子目录可追踪；规则写明 Phase 0 边界 |
| 3 | 建立 REQUIREMENTS、TRACEABILITY | 原文需求有稳定 ID、模块、来源、计划阶段和验证方式；后续实现不标 DONE |
| 4 | 建立 ARCHITECTURE、DATA_OWNERSHIP、ADR-001 | 四服务边界、HTTP 依赖、技术栈、数据所有权、禁止事项有文字说明 |
| 5 | 文档审查、结构和链接验证 | 检查结果与实际输出一致，不能将文档验证当作业务测试 |
| 6 | Git 提交、上传、草稿 PR | 独立 feature 分支，范围明确，无自动合并；发布结果记录在交付报告 |
| 7 | 阶段交付后停止 | 未经用户明确确认不执行 Phase 1 |

## 交付清单

必需文件：

- `AGENTS.md`
- `README.md`
- `docs/requirements/REQUIREMENTS.md`
- `docs/requirements/TRACEABILITY.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/DATA_OWNERSHIP.md`
- `docs/adr/ADR-001-service-boundary.md`
- `docs/plans/phase-0.md`

辅助治理文件：`CONTRIBUTING.md`、`.editorconfig`、`.gitattributes`、`.gitignore`、`.github/CODEOWNERS`、`.github/pull_request_template.md`、`docs/ethics/ETHICS_LOG.md`、`docs/review/phase-0-validation.md`，以及原文归档 `docs/requirements/CODEX_EXECUTION_PROMPT.md`。其余未实现目录仅放 `.gitkeep`。完整文件列表通过 `git ls-files` 查看。

当前本地交付为新项目；GitHub 创建仓库时自动初始化了 README.md，Phase 0 将其替换为完整说明。相对 GitHub main，README.md 为修改，其余交付文件为新增；没有修改或删除用户已有代码。

## 关键设计

遵循源文档既定的 Monorepo、四服务、REST HTTP 和 PostgreSQL 四 schema 方案。D 管公共主数据，M1 管占用真值，M2/M3 管各自业务状态。跨模块数据库访问被禁止，房间维度锁、幂等占用、显式同步中间状态作为未来实现约束。

总体目录是目标结构；Phase 0 只保留路径。`pom.xml`、`package.json`、`pnpm-workspace.yaml`、环境变量样例、Dockerfile、Compose、Nginx 配置和服务健康接口均留给 Phase 1。Flyway 和最小 seed 留给 Phase 2，完整 CI 留给 Phase 11。这一分界避免空文件被误认为可运行工程。

ADR 状态保持待人工审阅。需求中的未明确策略登记为开放问题，不以生成的示例值代替业务决策。CI/业务测试/Docker 尚未存在，不可声称通过。

## Git 策略

GitHub 创建仓库时以自动生成的 README 初始化 `main`，作为 PR 比较基点；Phase 0 的实质变更均在 `feature/phase-0-governance`。本地最初的空初始化提交仅用于准备分支，发布前已对齐远端 main。初始化提交不宣称可构建应用。本地提交使用 GitHub noreply 邮箱，避免公开私人邮箱。

建议阶段提交：

```text
chore: bootstrap project governance and requirements
```

GitHub 上传范围是本仓库全部 Phase 0 文档和治理配置。原始源文档保留用于需求溯源，公开前与其他文件一起检查凭据和个人信息。草稿 PR 仅用于审阅；不会自动 merge。

## 验证与未完成内容

实际命令和结果见 [验证记录](../review/phase-0-validation.md)，而非把计划命令写成执行结果。需求完成状态以 [TRACEABILITY](../requirements/TRACEABILITY.md) 为准。

Phase 1–15 均未实现；没有启动应用、构建镜像、运行 Maven/pnpm、创建数据库、执行业务测试或部署华为云。GitHub 分支保护、required checks 和 CI 不宣称已配置。

待人工审阅：ADR-001、跨模块 API 中释放键与唯一键的差异、重试计数口径、既有预约遇到维修/整教室占用的处理、角色数据范围、签到/违约保留和通知渠道。详见需求和架构文档开放问题。

停止条件：完成上述 Phase 0 交付与上传后停止，等待用户明确确认。任何后续动作只允许修订 Phase 0，不能以“顺手补齐”为由创建 Phase 1 工程骨架。
