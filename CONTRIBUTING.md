# 贡献流程

先阅读 [AGENTS.md](AGENTS.md)、[需求基线](docs/requirements/REQUIREMENTS.md) 和当前 [阶段记录](docs/plans/phase-7.md)。本项目目前只执行Phase7物业工单，完成后等待Phase8确认。

1. 为需求创建 `feature/<description>`，缺陷使用 `fix/<description>`；不直接在 main 做大规模修改。
2. 在 PR 中填写需求 ID、变更原因、实际验证命令和结果、未完成内容。提交使用 `type: summary`，例如 `chore: bootstrap project governance and requirements`。这是本项目约定，不是 GitHub 强制的提交格式。
3. 只暂存本任务文件。使用 `git diff --check` 和 `git diff --cached` 检查差异；不得提交凭据、环境文件、依赖目录、构建产物和敏感日志。
4. 按改动运行真实验证；未执行的检查明确标注。后端运行 `./scripts/test-backend.sh`，使用真实 PostgreSQL 和 HTTP 验证数据库、身份、权限、公共层、基础预约和签到/提醒/违约；测试数量与结果以当次 Surefire 报告为准。数据库变更另按[本地手册](docs/runbooks/local-development.md)执行 `python3 scripts/check-database.py --phase7`、持久化重启与空库重放。Web 变更运行 `pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`。整体运行验证使用 `scripts/smoke-phase-1.py --platform-api`，并执行 `scripts/smoke-phase-7.py --expected-commit <真实构建SHA>` 验证公共层/预约回归及本阶段可复现运行行为；严格 T−15/T+10/T+15 边界由注入 Clock 的真实 HTTP/PostgreSQL 集成测试覆盖。OpenAPI 源为 `contracts/openapi/platform.v1.json` 和 `contracts/openapi/reservation.v1.json`，改接口同步契约及[平台](docs/api/platform.md)/[预约](docs/api/reservation.md)说明；M2/M3契约同时维护，完整Web E2E留Phase9/10；不要将站内 ACCEPTED 写成邮件送达或用户已读。历史 Phase 0 验证只适用于其文档提交。
5. 发起草稿 PR 供审阅。schema、公共契约和跨模块变更必须人工 review；Git 语义冲突交由人工处理。
6. 更新 TRACEABILITY 和阶段报告。业务需求仅在全部适用 DoD 满足后标记 DONE；不得删除未完成需求。

使用 UTF-8、LF、文件末尾换行、无尾随空白。Java 使用 4 空格；Web、JSON、YAML 使用 2 空格。前端 ESLint 使用 typescript-eslint 与 eslint-plugin-vue；可用 `pnpm --filter @campus/web exec eslint src tests vite.config.ts --fix` 修复格式。后端当前以 Maven 编译、真实 HTTP/PostgreSQL 测试及 `git diff --check` 验证；完整 backend lint/CI 门禁在 Phase 11 建立前不声明已完成。

迁移只向前：已发布的所有 migration 不再修改，包括 D V1–V3、M1 V1–V2 和 M2/M3 V1；修复使用新版本。每个服务只迁移自己的 schema，不以管理员账号运行应用或迁移。应用账号只获得自身表的 SELECT/INSERT/UPDATE/DELETE 和序列使用权限，不能访问 Flyway 历史或更新/删除审计记录；不要把迁移账号用于业务查询。数据库初始化脚本只在空 volume 执行，不用它偷偷修补旧数据库权限。Schema 变更与演示 seed 分离；七角色、14 权限和初始角色权限映射属于应用参考目录，非本地也加载。演示 seed 仅限 `local` profile，不追加超出需求的演示数据。非本地启动必须保持 seed 关闭。

运行 `./scripts/init-env.sh` 可保留 `.env` 中已有非空值并补齐缺少的凭据；不得提交该文件或输出密码/哈希。已有 volume 的数据库密码不会随 `.env` 自动修改。需要轮换或保留旧数据时先制定迁移方案，不能直接删除 volume。测试专用凭据仅存在于隔离 Testcontainers 容器，不可用于部署。

每个完成且可运行的分项独立提交并上传 GitHub，不将未完成的阶段写为已验收。当前Phase7 PR基于Phase6分支，在前置 PR 合并前保留堆叠草稿。

`.github/CODEOWNERS` 将当前仓库所有者列为审阅负责人，但 CODEOWNERS 本身不强制阻止合并。required checks 在真实 CI 建立后配置，不能用空成功任务替代质量门禁；分支保护配置尚未验证。

本阶段按 GitHub 官方建议维护共享 [.gitignore](https://docs.github.com/en/get-started/git-basics/ignoring-files)、防止[提交敏感信息](https://docs.github.com/en/repositories/working-with-files/managing-files/creating-new-files)，并通过[草稿 PR](https://docs.github.com/en/pull-requests/reference/pull-requests)提供审阅入口。

后端测试在容器Linux工作区编译：仅归档Git非忽略源码，不复制私有.env，避免宿主同步目录的重复class/I/O错误；导出JAR、Surefire和JaCoCo XML。M2/M3测试启动真实D/M1 JAR，显式M1响应夹具只测客户端/重试边界；不得称为Phase8集成。新增契约见[教室API](docs/api/classroom.md)及contracts/openapi/classroom.v1.json。

M3契约见[工单API](docs/api/maintenance.md)与contracts/openapi/maintenance.v1.json。实际M1封房/释放不属于当前Phase7完成声明；具体政策/释放回执经草稿PR人工review。
