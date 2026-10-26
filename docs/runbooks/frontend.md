# 前端本地开发

需要 Node.js 24（至少 24.15）和 pnpm 11.25.0。依赖精确版本和传递依赖锁定在 package.json、pnpm-lock.yaml。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

打开终端显示的本地地址。Vite 将 `/status` 请求代理至 `http://127.0.0.1:18080`，该地址由 Compose 网关提供。网关尚未启动时，页面会显示接口连接失败；不会生成模拟健康或构建数据。完整运行与当前数据库准备见[容器运行手册](local-development.md)。

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Vitest 只替换 Axios 网络边界，验证聚合状态、部分故障、超时、错误响应、旧构建信息清除和重复刷新。生产请求直接读取四个服务的 health/info。

当前页面只提供服务状态、版本、提交 SHA、构建时间和手动刷新。D、M1与M2提供后端API，Phase5签到/违约/通知已验证，本轮M2申请审批及真实M1拒绝处理已验证；页面登录与完整角色业务流程仍留 Phase 9，不能以状态页或 API smoke 声称完整前端已完成。

站内提醒的后端 ACCEPTED 表示持久入箱，不代表当前状态页可读取通知、用户已读或邮件送达。签到输入、个人违约/通知列表等完整交互仍在 Phase 9/10 实现和 E2E 验证，不通过临时管理入口或前端调时模拟完成业务。

## Phase6当前接口与验证

M2六操作见[教室API](../api/classroom.md)，D新增本人审批范围。M2使用D和M1两种独立opaque凭据，Compose配置PLATFORM_BASE_URL/PLATFORM_CLASSROOM_TOKEN及M1_BASE_URL/M1_CLASSROOM_TOKEN；init-env保留旧凭据并补齐新值，不输出秘密。现有D4/M1 V3保留，M2从V1升V2、M3仍V1，保留卷升级，无需删除数据。

运行 `python3 scripts/smoke-phase-6.py --expected-commit <实际构建SHA>` 先回归Phase5，再验证真实D范围、提交/两级审批/拒绝/撤销/撤权与实际M1 403→SYNC_FAILED；临时为现有第二学生赋部门角色/范围后恢复，不扩seed。不会声称真实封房成功。定时超时/成功回执/重试边界另由真实HTTP/PostgreSQL+Clock或明确外部响应夹具验证。`check-database.py --phase6`核对M2四表一致性和没有假APPROVED/M2占用的当前阶段基线。

本地后端测试使用容器内部Linux工作区，公共Git源码归档不含.env；导出JAR、Surefire和JaCoCo XML，避免宿主同步class文件错误。完整130项通过，运行升级和最终SHA实际证据见[Phase6验收](../review/phase-6-validation.md)。完整角色Web/E2E留Phase9/10，M1占用/释放/恢复留Phase8；完成Phase6后停止等待Phase7。
