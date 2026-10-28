# 成员二个人交付与复现

日期：2026-10-28。个人分支：`feature/member-02-platform-web`。用户已授权提交：后端提交 `e5baa28`，前端提交 `05eda7e`，验收资料另作独立提交。此前 ZIP 是提交前工作树快照，其 manifest 保留当时状态。本文区分作者自测与外部审核，不代替成员三/四签字，也不宣布整个小组阶段完成。

## 本人实现

- D：当前物业主管加派单权限才可读取的维修人员选择列表；独立服务凭据验证、来源动作白名单、原子落库与幂等回执的审计接收接口；V5 向前迁移及 API/OpenAPI 草案。
- 公共 Web：唯一 Axios 客户端、Pinia 会话、登录退出、401 清理、路由守卫及角色导航；令牌只发往同源 `/api/v1`；接入成员三已提供的真实路由片段。
- 学生：空间筛选、完整座位分页、可用性查询、预约、取消、详情、签到、历史及再次预约；历史与违约分页；异步切换保护。
- 管理：楼宇、房间、座位、用户、角色/房间范围、系统参数、审计查询七页。
- 修复：空时间查询卡住、历史只能读取前50条、座位只能读取前100条、手机导航文字挤成竖排、管理失败错误文案误称预约错误、Git Bash/Docker 测试路径不兼容。

## 14条本人路由

| 路由 | 内容 | 权限边界 |
|---|---|---|
| `/login` | 登录 | 公开表单 |
| `/student/home` | 本人首页 | reservation:view:self |
| `/student/rooms` | 空间目录 | space:view |
| `/student/rooms/:id` | 座位查询/预约 | space:view；提交另需 reservation:create |
| `/student/reservations` | 当前预约 | reservation:view:self |
| `/student/reservations/:id` | 本人详情/签到/取消 | reservation:view:self；后端校验本人/时间/状态 |
| `/student/history` | 本人历史/违约 | reservation:view:self |
| `/admin/spaces` | 楼宇 | SYSTEM_ADMIN + space:manage |
| `/admin/rooms` | 房间 | space:manage + 后端房间范围 |
| `/admin/seats` | 座位 | space:manage + 后端房间范围 |
| `/admin/users` | 用户 | SYSTEM_ADMIN + user:manage |
| `/admin/roles` | 角色和范围 | SYSTEM_ADMIN + role:manage |
| `/admin/settings` | 参数 | SYSTEM_ADMIN + settings:manage |
| `/admin/audit` | 审计 | SYSTEM_ADMIN + audit:view |

## 可复现验证

先在本人的私有 `.env` 配好环境；不复制他人的凭据。运行 `bash scripts/test-backend.sh` 生成当前 Java 包。此入口使用 Testcontainers 隔离库，Windows Git Bash 路径已处理。前端执行：

```powershell
pnpm.cmd --dir apps/web lint
pnpm.cmd --dir apps/web typecheck
pnpm.cmd --dir apps/web test
pnpm.cmd --dir apps/web build
```

专项真实验收使用独立项目 `campus-member-02-acceptance`，18081 网关、18082 本机 D、5174 前端。PostgreSQL 使用内存盘；不会连接 `campus-member-02` 开发数据库。大量历史由真实创建/取消接口产生，仅在该临时库中存在，不扩充五人 seed。为了测试创建用户，专项用例在该临时库经真实管理 API 建立额外验收账号。

```powershell
docker compose --project-directory . --env-file .env -f tests/member2/compose.yaml up -d --wait --wait-timeout 180
$env:NO_PROXY='127.0.0.1,localhost'
$env:no_proxy=$env:NO_PROXY
$env:RUN_ISOLATED_E2E='1'
$env:RUN_WINDOW_CHECKIN='1'
pnpm.cmd --dir apps/web exec playwright test --config playwright.acceptance.config.ts
node scripts/test-member2-recovery.mjs
```

签到用例按真实 Asia/Shanghai 时间等待下个整点前15分钟开放，不修改主机或应用时钟；最长约45分钟等待。它取得真实房间当日码，通过浏览器提交并刷新验证 CHECKED_IN。此类预约不能按业务规则取消，验收结束由销毁专用临时库清理。其他普通开发库测试仍使用 `RUN_REAL_E2E=1` 显式授权，会取消自建预约但保留真实历史。

专项覆盖：管理创建/修改/启停、用户重置密码、角色和范围授权、审计筛选、51条以上历史翻页、真实101座分页和筛选、实际409冲突、测试客户端断网及恢复、14路由直达和截图、实际窗口签到。网络失败使用浏览器中止真实请求，不伪造成功响应。D恢复脚本校验容器项目标签和内存数据库后，只重启专用D容器、暂停/恢复专用数据库；验证原回执保留、未知结果重放及错误 actor 不占用事件键。

截图保存在 `work/member2-evidence/`，只含隔离验收数据，截图关闭密码/令牌/签到码记录。结束后：

```powershell
docker compose --project-directory . --env-file .env -f tests/member2/compose.yaml down
```

该命令销毁专用验收容器及其内存数据，不是开发环境清库。不得把命令的项目文件换成开发 compose 后照抄清理操作。

## 证据与责任边界

- 后端当前源代码：157项通过，D55/M1 49/M2 28/M3 25，零失败/错误/跳过。
- 前端 lint/typecheck/build 通过，单元测试11项；普通真实浏览器4项，隔离专项6类用例分别通过；D故障恢复3项。18:45真实窗口签到成功并刷新确认，未改时钟。详细批次与结果见 `phase-9-member-2.md` 最新条目。
- 独立审核、各业务生产者的审计投递/outbox、完整M2/M3业务联调、CI/部署及保留期限等政策不由成员二自行批准。本人可交付接口、代码、复现命令及证据。
- 用户已授权将上述提交和验收资料推送个人分支；推送结果以实际 Git 输出为准。不合并小组主分支，契约/schema仍须人工审核。
