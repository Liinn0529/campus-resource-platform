# Phase 4 基础预约验收记录

执行日期：2026-10-25（Asia/Shanghai）。用户在 Phase 3 交付后确认继续，本轮仅交付 M1 基础预约，完成后等待 Phase 5。Schema、公共契约和未定业务政策通过草稿 [PR #5](https://github.com/Liinn0529/campus-resource-platform/pull/5)交人工 review，未自动合并。

## 修改文件与设计

| 文件组 | 变化与用途 |
|---|---|
| M1 `ReservationController/Service/Mapper`、`TimeRules` | 8 个房间/座位/可用性、创建/取消、本人列表/详情/再预约操作；带 offset 整点时间、默认最多 4 小时、半开区间及本人权限 |
| M1 `PlatformClient`、`RemoteAuthenticationFilter`、`AccessPolicy` | 每次通过真实 D HTTP 读取当前用户及权限；目录与参数使用独立服务凭据；拒绝无效响应、重定向和显式空插座标记，D 故障返回 503 |
| M1 API envelope、异常、请求 ID/日志与运行配置 | 安全错误码、超时、明确 CORS、注入 Clock/业务时区；日志不记请求正文、凭据或个人目录 |
| M1 `V2__reservation_workflow.sql` | room_gate、seat_reservation、完整 11 字段 seat_occupancy 和 reservation_event；来源三元组唯一，保留释放及取消历史 |
| D 内部目录 Controller/Service/Mapper、安全配置 | 内部启用房间目录；座位过滤先于分页，支持启用、插座和具体座位；保留已有调用兼容性 |
| `compose.yaml`、Nginx 配置 | M1 等待真实 D 健康，持有自己的 opaque 凭据；两个 M1 公共路径转发至 M1，内部路径不公开 |
| M1 工作流/时间/客户端边界、D 目录测试及真实 D 测试支持 | PostgreSQL、HTTP、首次并发锁、参数、所有权、回滚、当前权限与错误响应行为 |
| `scripts/check-database.py`、`smoke-phase-4.py`、状态页 | 实际卷迁移和账本检查、真实网关全流程及取消清理；页面显示 Phase 04，完整业务 UI 留 Phase 9 |
| 两份 OpenAPI、API 文档、治理/架构/需求/伦理/运行手册 | D 33 操作、M1 8 操作的可审阅契约、可复现步骤、实际完成范围与后续边界 |

M1 不持有 D JWT 签名密钥，不跨 schema SQL。用户 JWT 只用于 D `/api/v1/me`，主数据及参数走 M1 专属 opaque 凭据。当前 D 身份、停用状态、角色和权限每次请求重新读取；后端权限和本人所有权不依赖页面按钮，系统管理员也不能借此替他人操作预约。

创建在取回 D 快照后进入 M1 事务：先 `INSERT ... ON CONFLICT DO NOTHING` 初始化 room_gate，再 `SELECT FOR UPDATE`，锁后重新验证当前时间，检查所有 ACTIVE 占用，再同事务写预约、账本和事件。首次并发创建同样受锁保护。冲突条件为半开区间重叠，并覆盖同座位、seat_id 为空的整房间来源及 end_at 为空的开放结束。D 校验与 M1 提交没有跨服务原子性，主数据调整与既有预约的协调仍待后续审阅。

取消仅允许本人 CREATED 预约且严格在开始前；重复取消返回原结果，不添加重复释放/事件。仅释放对应 M1/RESERVATION/source_ref_id；账本异常则事务整体回滚。历史再预约产生新 ID，重新取得当前主数据和参数，并重新争抢锁；旧历史不变。已结束预约可查询为历史，本阶段不自动写 COMPLETED、CHECKED_IN 或 NO_SHOW。

## 实际命令与结果

| 命令或操作 | 实际结果 |
|---|---|
| `./scripts/test-backend.sh` | 完整 reactor 69 项通过，0 失败/错误/跳过，真实 PostgreSQL 与真实 D JAR 子进程 |
| 新增客户端边界后 `./scripts/test-backend.sh -pl services/reservation-service` | 最终 M1 28 项通过；当前四模块 Surefire 合计 75 个不同测试，0 失败/错误/跳过。不是宣称最后一次完整 reactor 运行了 75 项 |
| `pnpm --dir apps/web lint`、`test`、`build` | 全部退出 0；6 项测试通过，生产构建包含 vue-tsc 类型检查 |
| 两份 JSON 经 `openapi-spec-validator` 验证 | D 与 M1 均通过；操作数分别 33/8，并与实际 Controller 对照 |
| `docker compose config --quiet`、实际容器 `nginx -t` | 均通过，不输出私有配置值 |
| 升级前 `python3 scripts/check-database.py --phase3 --snapshot <临时路径>` | 原 D V3、其他 V1，八账号/schema/审计/精确最小集合通过，保存非敏感指纹 |
| `./scripts/build.sh` | 五个应用镜像构建成功；首轮版本 `6d98a8752f9f5babb4e1f28b178d3dacaf798bf9-dirty`，构建 UTC `2026-10-25T14:30:16Z` |
| `docker compose up -d --force-recreate --wait --wait-timeout 180` | 保留已有 volume，M1 自动从 V1 升至 V2；D V3、M2/M3 V1，七容器 healthy，未执行 down -v |
| `python3 scripts/smoke-phase-4.py --expected-commit <构建版本> --evidence <临时路径>` | 所有 PASS：先执行 Phase 3 实际 API 回归，再验证真实目录/插座、4 小时限制、创建/冲突、所有权、取消/历史/再预约和内部路径隐藏 |
| `python3 scripts/check-database.py --phase4 --snapshot <临时路径>` | 八账号、schema 隔离、应用 DDL/迁移历史限制、追加审计、迁移版本及预约/账本/事件一致性通过 |
| 升级前后数量与密码指纹独立比较 | 原有用户、角色/权限/关系、楼宇、房间和座位数量及五个用户密码保持不变；只有 M1 版本前进 |
| 实际 M1 跨源 OPTIONS 请求 | 明确本地来源返回 200 和匹配 ACAO；未知来源返回 403 且无 ACAO |
| 实际 D/M1 容器日志检查 | 保留请求 ID/路由模板；不包含当前私有密码、JWT/内部秘密、数据库中的签到码值或 BCrypt 哈希 |

75 项包含 D 37、M1 28、M2/M3 各 5。M1 中有 13 项真实 HTTP/数据库工作流、4 项时间规则、6 项客户端边界和原有 5 项运行/数据库基线。工作流覆盖同一房间首次 6 请求争抢同座位仅 1 个成功、不同座位均成功、相邻区间、当前 D 最大时长变化、两个房间类型与插座过滤、M2 整房间/M3 开放结束数据形状、所有权、实时撤权/停用、伪造 userId 拒绝、取消边界与异常账本回滚。M2/M3 形状仅存在于隔离数据库测试，不冒充其 HTTP 工作流已经交付。

客户端边界夹具只产生有意损坏的外部 HTTP 响应，用于验证凭据分离、错误/重定向/缺字段/空值/错误筛选失败；业务测试仍启动真实 D JAR 和 PostgreSQL。测试 Clock 仅控制时间边界，不替代被测业务。真实运行 smoke 不扩充用户或空间，最终取消本次两条预约，保留 2 条 CANCELLED 预约、2 条 RELEASED 占用及 4 条实际事件；后续重跑会继续增加真实取消历史。

## 发现并修复的问题

- 真实 D 测试夹具最初只等待 health，local seed 还未提交即登录，出现 3 个 401。改为健康后再等待真实 demo_admin 登录成功，未以跳过集成或 mock 登录掩盖问题。
- 首次 availability 测试把已编码 query 交给字符串 URL 再编码，出现 1 个 400。改为使用 URI 发送真实请求，随后完整 69 项通过。
- 独立复核发现 primitive boolean 的 required 标记仍允许显式 null 被转换为 false。增加字段级 Nulls.FAIL 及第 6 个客户端测试，验证 false 筛选、不筛选和按 ID 查询均返回 503，M1 28 项通过。

## 交付与未完成范围

第一分项为 `feat(reservation): implement room-locked seat reservation workflow`，GitHub 提交 `6d98a8752f9f5babb4e1f28b178d3dacaf798bf9`。第二分项为 `test(reservation): verify compose workflow and phase 4 handoff`，包含边界修复、验收脚本、页面标识及最终治理文档。提交后按最终 GitHub SHA 再构建、重启保留卷并重复验收；最终 SHA/时间、镜像和再次结果记于仓库外报告，避免预填本提交自己的 SHA。

186 个稳定需求 ID、15 个 OQ、原始归档和历史 Phase 0–3 验收均保留；已发布 D V1–V3、M1/M2/M3 V1 未改写。追踪表最终 41 DONE /111 TODO /34 PARTIAL。DONE 表示当前阶段适用功能及运行验收完成，不表示人工审阅、后续业务或 GitHub CI 完成。

实际签到、提醒、违约及自动完成留 Phase 5；M2/M3 业务和跨模块占用写接口、完整角色前端、通知投递、生产秘密轮换、CI/CD 和云部署尚未交付。当前 `.github/workflows` 无实际工作流，未声称 GitHub CI 已通过。目录房间类型、取消时间、最大时长快照及主数据生命周期方案仍待草稿 PR 人工 review。Phase 4 完成后停止，等待明确确认再进入 Phase 5。
