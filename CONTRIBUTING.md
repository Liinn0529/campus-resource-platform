# 贡献流程

先阅读 [AGENTS.md](AGENTS.md)、[需求基线](docs/requirements/REQUIREMENTS.md) 和当前 [阶段记录](docs/plans/phase-0.md)。本项目目前只执行 Phase 0。

1. 为需求创建 `feature/<description>`，缺陷使用 `fix/<description>`；不直接在 main 做大规模修改。
2. 在 PR 中填写需求 ID、变更原因、实际验证命令和结果、未完成内容。提交使用 `type: summary`，例如 `chore: bootstrap project governance and requirements`。这是本项目约定，不是 GitHub 强制的提交格式。
3. 只暂存本任务文件。使用 `git diff --check` 和 `git diff --cached` 检查差异；不得提交凭据、环境文件、依赖目录、构建产物和敏感日志。
4. 按改动运行真实验证；未执行的检查明确标注。Phase 0 仅验证文档、目录、链接和需求追踪；后续业务阶段执行相应 unit、integration、API、E2E 和 Docker 检查。
5. 发起草稿 PR 供审阅。schema、公共契约和跨模块变更必须人工 review；Git 语义冲突交由人工处理。
6. 更新 TRACEABILITY 和阶段报告。业务需求仅在全部适用 DoD 满足后标记 DONE；不得删除未完成需求。

使用 UTF-8、LF、文件末尾换行、无尾随空白。Java 使用 4 空格；Web、JSON、YAML 使用 2 空格。Phase 1 再配置可执行的格式化及 lint 工具，不提前声称 lint 已通过。

`.github/CODEOWNERS` 将当前仓库所有者列为审阅负责人，但 CODEOWNERS 本身不强制阻止合并。required checks 在真实 CI 建立后配置，不能用空成功任务替代质量门禁；分支保护配置尚未验证。

本阶段按 GitHub 官方建议维护共享 [.gitignore](https://docs.github.com/en/get-started/git-basics/ignoring-files)、防止[提交敏感信息](https://docs.github.com/en/repositories/working-with-files/managing-files/creating-new-files)，并通过[草稿 PR](https://docs.github.com/en/pull-requests/reference/pull-requests)提供审阅入口。
