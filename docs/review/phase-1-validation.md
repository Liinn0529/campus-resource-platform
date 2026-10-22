# Phase 1 总体验收记录

记录时间：2026-10-21–22（Asia/Shanghai）。本阶段包含工程骨架及本地运行验证，业务与后续阶段尚未实现。

## 已执行的验证

| 命令/操作 | 实际结果 |
|---|---|
| `./scripts/test-backend.sh` | Maven 3.9.11 / Java 21.0.9：verify 退出 0，16 项真实 HTTP 测试全部通过，四个可执行 JAR 与 JaCoCo 报告生成 |
| `pnpm install --frozen-lockfile` | 退出 0，精确依赖与锁文件可复现安装 |
| `pnpm lint` | 退出 0，无 warning/error |
| `pnpm typecheck` | 退出 0 |
| `pnpm test` | 6 项通过，覆盖正常、部分失败、超时、格式错误、旧数据清除和刷新去重 |
| `pnpm build` | 退出 0，真实 Vue 生产资源生成 |
| `./scripts/build.sh web`、`./scripts/build.sh platform-service reservation-service classroom-service maintenance-service` | 五个应用镜像均从源码实际构建成功 |
| `nginx -t`（实际 Nginx 容器，非 root） | 配置语法成功 |
| `docker compose up -d` | 实际启动七个容器，全部 healthy |
| `python3 scripts/smoke-phase-1.py --expected-commit dfe1d09d6c2e057f9fd1ba2aece4ec8d9e57ab50-dirty` | 退出 0，全部 PASS；该 SHA 是首次联调时的真实未提交源码基线 |
| 真实 Chrome 访问本地网关 | 四服务显示 UP、0.1.0-SNAPSHOT、真实 SHA 和构建时间 |
| 浏览器失败状态与刷新 | 后端未启动时显示 HTTP 请求失败，刷新更新时间；未显示虚构成功 |
| 390×844 窄屏 | 单列四卡片，document scrollWidth=390，与视口相同，无横向溢出 |
| Shell 语法、`git diff --check` | 通过 |

另已使用仓库外临时脚本核对原始归档 SHA-256 未变、186 个需求 ID 唯一且两表一致、12 DONE / 169 TODO / 5 PARTIAL、15 个未决问题保留、相对文档链接存在、UTF-8/LF/尾随空白、POM XML、脚本执行权限和阶段范围；结果通过。本地 .env 实际权限为 600，Git 忽略有效。

首次联调返回 `gitCommit=dfe1d09d6c2e057f9fd1ba2aece4ec8d9e57ab50-dirty`，`buildTime=2026-10-21T14:21:34Z`。本记录以提交前实际运行结果为证据；交付时按最终 GitHub 提交重建，并在仓库外的交付报告记录最终完整 SHA 与 smoke 结果，避免预填尚未执行的结果。

## 容器拓扑与访问边界

| 服务 | State | Health | 发布的宿主机端口 |
|---|---|---|---|
| postgres | running | healthy | 无 |
| platform-service | running | healthy | 无 |
| reservation-service | running | healthy | 无 |
| classroom-service | running | healthy | 无 |
| maintenance-service | running | healthy | 无 |
| web | running | healthy | 无 |
| nginx | running | healthy | 127.0.0.1:18080→8080 |

HTTP smoke 独立核对四组 health/info 的字段、真实 SHA、时间和模块身份；POST health/info 为 405；内部路径、actuator 根/env、未实现业务与非白名单 status 路径为 404。静态 JS/CSS 返回 200，具有 immutable 缓存；客户端路由回退到 index，缺失资源返回 404。

后端 HTTP 测试还核对直接访问时未实现/敏感路径与 POST health/info 为 403。网关和后端的不同状态码符合各自边界。

## 实际问题与修复

- Docker 自带镜像下载停滞：通过宿主下载官方镜像清单与层，逐项 SHA-256/大小验证后加载 Docker；该机器的临时下载工具与缓存未加入仓库。
- 构建容器没有继承宿主本地代理：仅为本任务临时 Docker 配置代理，未写入项目。Web 使用 Corepack 精确安装 pnpm，避免全版本 npm 元数据下载卡住。
- 容器内回环健康检查曾误经代理返回 503：改为 `wget -Y off`，修复后 Web 和四服务均实际 healthy。
- 静态资源曾有重复 Cache-Control：改为单一包含 max-age 与 immutable 的值，并实际请求确认。
- Playwright CLI 被 macOS 沙箱阻止启动 Chrome：改用已连接的 Chrome 完成真实浏览器检查；未将 CLI 失败计为通过，未生成完整业务 Playwright 测试。

后端测试存在 Map unchecked 编译提示和测试依赖动态 agent 提示，详见 [后端记录](phase-1-backend.md)，不影响本阶段实际测试结果。

## 范围与未完成项

REQ-D-008、REQ-ENG-007、REQ-OPS-001、REQ-PH-001 按本阶段适用验收完成。完整技术栈依赖与目标目录仍有后续内容，追踪表保持 PARTIAL。

当前没有数据库业务 schema/独立服务账号、migration、seed、业务 API、登录/JWT/RBAC、生产 mock、CI/CD 或云端部署。PostgreSQL 是空实例；应用 health 不宣称数据库或业务可用。GitHub 草稿 PR 用于人工审阅，尚未自动合并或建立 required checks。
