# Phase 1：工程骨架

用户在 Phase 0 交付后确认继续，本轮仅执行 Phase 1；完成后停止，等待 Phase 2 确认。

## 范围与执行计划

1. 以 Phase 0 提交为基线创建 feature/phase-1-scaffold，保留已有文档。
2. 建立 Java 21 / Spring Boot 3.x / Maven 四模块，各自提供真实 health/info；仅允许这两个运行端点，其余默认拒绝，不添加登录或业务。
3. 建立 Vue 3 / TypeScript / Vite / Router / Pinia / Element Plus / Axios 应用，只展示真实服务运行状态。
4. 建立 Dockerfile、根 Compose 与 Nginx；单独 PostgreSQL 空实例，无业务 schema、账号、迁移或 seed。仅网关绑定本机端口，内部 API 不代理出网。
5. 执行后端与前端相关验证、容器构建、Compose healthy、HTTP smoke 和浏览器检查，记录真实结果。
6. 更新需求追踪、README、运行手册、阶段报告；上传独立分支及草稿 PR，停止。

## 验收与边界

关联 REQ-PH-001、REQ-D-008、REQ-ENG-007、REQ-OPS-001，以及工程栈、安全边界相关需求。最终以 docker compose up -d 后七个容器全部 healthy、四服务 health/info 实际可请求、Web 通过 Nginx 访问为准。版本字段包含 service/version/gitCommit/buildTime。

Phase 2–15 未授权。数据库隔离和迁移、业务 API、RBAC/JWT、完整业务 E2E、CI/CD 均不在本轮实现。新分支基于 Phase 0 分支；若其 PR 尚未合并，Phase 1 使用堆叠草稿 PR，不擅自合并 Phase 0。

## 执行结果

四个 Spring Boot 服务、Vue 状态页和七容器 Compose 已完成本阶段适用验收。详细证据见 [phase-1-validation.md](../review/phase-1-validation.md)。

### 文件与设计

- 新建父 Maven POM、四服务 POM、Application、RuntimeConfiguration、application.yml 和 RuntimeEndpointsTest；四服务不接入数据库。
- 新建根 pnpm 工作区、锁文件和 apps/web 的 Vue/TypeScript 应用、状态聚合、真实失败提示与六项测试。
- 新建 services/Dockerfile、Web Dockerfile、compose.yaml、两份 Nginx 配置、.dockerignore、.env.example；应用使用非 root 用户，仅网关发布 loopback 端口。
- 新建 scripts/init-env.sh、build.sh、dev-up.sh、test-backend.sh、smoke-phase-1.py；构建注入真实完整 SHA、dirty 标记和 UTC 时间，密码仅保存在本地私有 .env。
- 新建运行端点契约、三份运行手册与分项验证记录；修改 AGENTS、README、CONTRIBUTING、需求基线、追踪表和伦理记录。

四个独立服务的运行端点保留模块边界，默认拒绝业务与敏感路径。前端只有真实 health/info 聚合；不生成业务数据。PostgreSQL 仅为空实例，未提前创建四 schema/账号、迁移或 seed。构建时源码状态与最终提交必须可以核对。

### 实际验证

`./scripts/test-backend.sh`：Maven verify 成功，四服务各 4 项真实 HTTP 测试，共 16 项通过，JAR repackage 和 JaCoCo 报告生成。

`pnpm install --frozen-lockfile`、`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build` 均成功，Vitest 六项通过。

`./scripts/build.sh` 对 Web 及四服务实际构建成功；`docker compose up -d` 后 postgres、四服务、web、nginx 七容器全部 healthy。HTTP smoke 成功核对四组 health/info、只读网关、隐藏路径、静态资源、客户端路由及 loopback 端口。真实 Chrome 检查已观察四服务正常与构建信息，另验证失败状态、刷新和 390px 无横向溢出。

### GitHub 分项提交

分支 `feature/phase-1-scaffold`，草稿 [PR #2](https://github.com/Liinn0529/campus-resource-platform/pull/2)，基于 Phase 0 分支。按用户要求，完成可运行分项后逐次提交并同步：前端 `0f99e2d`、后端 `7cc2f7c`、Web 容器 `06162cf`，之后追加完整容器联调与交付记录。最终完整提交与运行版本的核对保存在交付报告。

### 剩余内容与阶段停止

Phase 2–15 未执行，四 schema/账号、Flyway、seed、JWT/RBAC、业务流程、完整 Testcontainers/Playwright、CI/CD、生产 HTTPS 和华为云部署仍保留后续需求。15 个原有未决问题继续保留。当前本地验证不等于 GitHub CI 通过；分支保护和 required checks 尚未配置验证。

Phase 0/1 草稿 PR 保持人工审阅，未自动合并。完成本阶段交付即停止，等待 Phase 2 确认。

建议阶段汇总 commit：`feat: scaffold platform services and web application`。实际采用多个分项提交，保留验证与上传记录。
