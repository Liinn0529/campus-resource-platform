# Phase 1 前端分项验证

2026-10-21：Vue 3 / TypeScript / Vite / Vue Router / Pinia / Element Plus / Axios 状态页可独立启动。依赖以 pnpm-lock.yaml 锁定。

已执行：

| 命令 | 结果 |
|---|---|
| `pnpm install --frozen-lockfile` | 退出 0；esbuild 构建脚本通过 allowBuilds 明确允许 |
| `pnpm lint` | 退出 0，无 warning/error |
| `pnpm typecheck` | 退出 0 |
| `pnpm test` | 6 项通过，覆盖部分故障、超时、格式错误、旧状态清除与刷新去重 |
| `pnpm build` | 退出 0，生成实际 dist；不提交构建产物 |
| `pnpm dev` 与 `GET http://127.0.0.1:5173/` | Vite 启动，实际 HTML 返回 200 |

生产请求直接访问网关 `/status/{service}/{health,info}`。本分项尚未完成真实后端联调、容器 Web 和浏览器验收；这些验证在 Phase 1 总体验收中继续。REQ-PH-001 此时仍未完成。

## Web 容器分项追加验证

后续已执行 `./scripts/build.sh web`，Corepack 安装精确 pnpm 11.25.0，容器内 frozen install、vue-tsc 和 Vite build 成功。`docker compose up -d --no-deps web` 后 Web 容器实际 healthy；容器内 GET /healthz 与 / 返回 200，页面引用真实打包的 JS/CSS，JS 响应具有单一的 immutable Cache-Control。

健康检查使用 `wget -Y off`，本机回环请求不会误经宿主代理。`.dockerignore` 排除环境文件、依赖与构建目录。

真实 Chrome 检查确认后端未启动时显示 HTTP 失败、刷新会更新检查时间；390px 宽度下四卡片单列，scrollWidth 等于视口宽度，无横向溢出。Playwright CLI 在本机沙箱中启动 Chrome 被系统拒绝，浏览器检查实际由现有 Chrome 连接完成；不声称 CLI 测试通过。七容器与健康服务页面仍在总体验收中继续。
