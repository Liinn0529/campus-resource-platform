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

待本阶段完成后填写实际文件、命令、验证结果和未完成项，不预填成功。
