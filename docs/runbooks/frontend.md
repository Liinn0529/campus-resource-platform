# 前端本地开发

需要 Node.js 24（至少 24.15）和 pnpm 11.25.0。依赖精确版本和传递依赖锁定在 package.json、pnpm-lock.yaml。

```sh
pnpm install --frozen-lockfile
pnpm dev
```

打开终端显示的本地地址。Vite 将 `/status` 请求代理至 `http://127.0.0.1:18080`，该地址由 Compose 网关提供。网关尚未启动时，页面会显示接口连接失败；不会生成模拟健康或构建数据。完整运行与 Phase 2 数据库准备见[容器运行手册](local-development.md)。

```sh
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Vitest 只替换 Axios 网络边界，验证聚合状态、部分故障、超时、错误响应、旧构建信息清除和重复刷新。生产请求直接读取四个服务的 health/info。

当前页面只提供服务状态、版本、提交 SHA、构建时间和手动刷新。业务流程与登录仍属于后续阶段。
