# 贡献流程

先阅读 [AGENTS.md](AGENTS.md)、[需求基线](docs/requirements/REQUIREMENTS.md) 和当前 [阶段记录](docs/plans/phase-2.md)。本项目目前只执行 Phase 2 数据库，完成后等待 Phase 3 确认。

1. 为需求创建 `feature/<description>`，缺陷使用 `fix/<description>`；不直接在 main 做大规模修改。
2. 在 PR 中填写需求 ID、变更原因、实际验证命令和结果、未完成内容。提交使用 `type: summary`，例如 `chore: bootstrap project governance and requirements`。这是本项目约定，不是 GitHub 强制的提交格式。
3. 只暂存本任务文件。使用 `git diff --check` 和 `git diff --cached` 检查差异；不得提交凭据、环境文件、依赖目录、构建产物和敏感日志。
4. 按改动运行真实验证；未执行的检查明确标注。后端运行 `./scripts/test-backend.sh`，其 23 项测试使用真实 PostgreSQL 和 HTTP；数据库变更另按[本地手册](docs/runbooks/local-development.md)执行 `python3 scripts/check-database.py`、持久化重启与空库重放。Web 变更运行 `pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`。整体运行验证继续使用 `scripts/smoke-phase-1.py`；后续业务阶段补相应 API、E2E。历史 Phase 0 验证只适用于其文档提交。
5. 发起草稿 PR 供审阅。schema、公共契约和跨模块变更必须人工 review；Git 语义冲突交由人工处理。
6. 更新 TRACEABILITY 和阶段报告。业务需求仅在全部适用 DoD 满足后标记 DONE；不得删除未完成需求。

使用 UTF-8、LF、文件末尾换行、无尾随空白。Java 使用 4 空格；Web、JSON、YAML 使用 2 空格。前端 ESLint 使用 typescript-eslint 与 eslint-plugin-vue；可用 `pnpm --filter @campus/web exec eslint src tests vite.config.ts --fix` 修复格式。后端当前以 Maven 编译、真实 HTTP/PostgreSQL 测试及 `git diff --check` 验证；完整 backend lint/CI 门禁在 Phase 11 建立前不声明已完成。

迁移只向前：已发布的 `V1__*.sql` 不再修改，修复使用新版本。每个服务只迁移自己的 schema，不以管理员账号运行应用或迁移。应用账号只获得自身表的 SELECT/INSERT/UPDATE/DELETE 和序列使用权限，不能修改 Flyway 历史；不要把迁移账号用于业务查询。数据库初始化脚本只在空 volume 执行，不用它偷偷修补旧数据库权限。Schema 变更与 seed 分离；演示 seed 仅限 `local` profile，不追加超出需求的演示数据。非本地启动必须保持 seed 关闭。

运行 `./scripts/init-env.sh` 可保留 `.env` 中已有非空值并补齐缺少的凭据；不得提交该文件或输出密码/哈希。已有 volume 的数据库密码不会随 `.env` 自动修改。需要轮换或保留旧数据时先制定迁移方案，不能直接删除 volume。测试专用凭据仅存在于隔离 Testcontainers 容器，不可用于部署。

每个完成且可运行的分项独立提交并上传 GitHub，不将未完成的阶段写为已验收。当前 Phase 2 PR 基于 Phase 1 分支，在前置 PR 合并前保留堆叠草稿。

`.github/CODEOWNERS` 将当前仓库所有者列为审阅负责人，但 CODEOWNERS 本身不强制阻止合并。required checks 在真实 CI 建立后配置，不能用空成功任务替代质量门禁；分支保护配置尚未验证。

本阶段按 GitHub 官方建议维护共享 [.gitignore](https://docs.github.com/en/get-started/git-basics/ignoring-files)、防止[提交敏感信息](https://docs.github.com/en/repositories/working-with-files/managing-files/creating-new-files)，并通过[草稿 PR](https://docs.github.com/en/pull-requests/reference/pull-requests)提供审阅入口。
