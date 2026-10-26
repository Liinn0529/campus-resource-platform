# 校园资源服务平台

校园自习座位预约（M1）、整教室预约审批（M2）、物业报修（M3）和公共层（D）的 Monorepo。

**当前执行：Phase 6 整教室申请与两级审批，130项后端测试及首轮真实运行验收通过。** D新增本人审批房间范围，M2提供提交/待办/详情、两级批准/拒绝、前置撤销、审批超时和持久同步。见[Phase 6方案](docs/plans/phase-6.md)、[教室API](docs/api/classroom.md)和[验收记录](docs/review/phase-6-validation.md)。真实M1整房间占用接口属于Phase8，当前真实调用403会明确进入SYNC_FAILED，不显示假APPROVED；完整业务Web仍在Phase9。完成本阶段后停止等待Phase7。

## 启动与验证

Phase 6代码位于 `feature/phase-6-classroom`，基于Phase5最终c6948c95，已验证分项分别上传[草稿PR #7](https://github.com/Liinn0529/campus-resource-platform/pull/7)。D分项06a13a7d、M2分项b56f96ce已上传；保留堆叠草稿人工review，不自动合并。

```sh
git clone --branch feature/phase-6-classroom https://github.com/Liinn0529/campus-resource-platform.git
cd campus-resource-platform
./scripts/dev-up.sh
```

需要 Docker（含 Compose）、Git 和 OpenSSL。脚本补齐私有 `.env` 中缺少的随机密码、JWT 签名秘密和D的三个独立调用方凭据及M2到M1的独立凭据，保留已有非空值，再构建镜像并等待七个容器健康。数据库初始化脚本只在空 volume 上创建账号和 schema；已有数据库先阅读[升级说明](docs/runbooks/local-development.md#已有-volume-与空库重放)。打开 <http://127.0.0.1:18080> 查看实际运行状态与构建版本；D、M1与M2公开API 通过同一网关的 `/api/v1/` 访问，内部 API 不经网关公开。数据库和后端不发布宿主机端口。

```sh
./scripts/test-backend.sh
python3 scripts/check-database.py --phase6
python3 scripts/smoke-phase-1.py --platform-api --expected-commit "$(git rev-parse HEAD)"
python3 scripts/smoke-phase-6.py --expected-commit "$(git rev-parse HEAD)"
```

完整clean reactor130项（D48/M1 49/M2 28/M3 5）、前端6项及lint/类型检查/构建通过。测试使用真实D/M1 JAR、HTTP与PostgreSQL；显式外部响应夹具只验证客户端和同步恢复边界。`check-database.py --phase6`检查D4/M1 V3/M2 V2/M3 V1、账号隔离、最小集合及账本/审批一致性；`smoke-phase-6.py`先回归Phase5，再验证实际网关两级审批、撤权和M1拒绝后的SYNC_FAILED。固定时间边界用注入Clock测试，无生产调时端点。脚本从私有.env读取凭据且不输出token；实际结果见[Phase6验收](docs/review/phase-6-validation.md)。登录示例见[后端手册](docs/runbooks/backend.md#登录与平台-api)。

前端独立开发需要 Node.js 24、pnpm 11.25.0：

```sh
pnpm install --frozen-lockfile
pnpm dev
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

完整命令、端口调整、构建元数据、dirty 标记和停止方法见[容器手册](docs/runbooks/local-development.md)及[前端手册](docs/runbooks/frontend.md)。

## 阅读顺序

1. [AGENTS.md](AGENTS.md)：阶段门禁、开发约束、Git 和完成规则。
2. [REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md)：编号需求、来源、阶段和验收条件。
3. [TRACEABILITY.md](docs/requirements/TRACEABILITY.md)：每条需求的实现和验证状态。
4. [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)：服务边界、技术栈和调用关系。
5. [DATA_OWNERSHIP.md](docs/architecture/DATA_OWNERSHIP.md)：schema、账号和数据所有权。
6. [ADR-001](docs/adr/ADR-001-service-boundary.md)：服务划分决策，待人工审阅。
7. [Phase 6计划](docs/plans/phase-6.md)与[本轮验收](docs/review/phase-6-validation.md)、[教室API](docs/api/classroom.md)、历史[Phase5](docs/review/phase-5-validation.md) / [Phase 4](docs/plans/phase-4.md)、[预约 API](docs/api/reservation.md)、[平台 API](docs/api/platform.md)、[运行端点契约](docs/api/runtime.md)，以及历史 [Phase 3](docs/plans/phase-3.md) / [Phase 2](docs/plans/phase-2.md) / [Phase 1](docs/plans/phase-1.md) / [Phase 0](docs/plans/phase-0.md) 记录。

原始依据保存在 [CODEX_EXECUTION_PROMPT.md](docs/requirements/CODEX_EXECUTION_PROMPT.md)，不将文档中的后续阶段任务视为提前执行授权。

## 仓库布局

```text
campus-resource-platform/
├── AGENTS.md
├── README.md
├── CONTRIBUTING.md
├── pom.xml              # Java 21 / Spring Boot 3.5.16 Maven reactor
├── package.json         # pnpm workspace commands
├── pnpm-lock.yaml
├── compose.yaml         # 七容器本地开发环境
├── docs/
│   ├── requirements/    # 原始依据、编号需求、追踪表
│   ├── architecture/    # 架构和数据所有权
│   ├── adr/             # 架构决策记录
│   ├── plans/           # 阶段计划及交付记录
│   ├── review/          # 验证证据
│   ├── ethics/          # 真实伦理问题记录
│   ├── api/             # 平台公开/内部 API 与 health/info 契约
│   └── runbooks/        # 本地启动与验证
├── contracts/openapi/   # 当前阶段 OpenAPI 契约
├── services/
│   ├── platform-service/
│   ├── reservation-service/
│   ├── classroom-service/
│   └── maintenance-service/
├── apps/web/            # Vue 状态页及容器镜像
├── deploy/{postgres,compose,nginx,huawei}/
├── tests/java/          # 真实 PostgreSQL 隔离验证支持
├── scripts/             # 环境生成、构建、数据库验证、HTTP smoke
└── .github/             # PR 模板、CODEOWNERS、工作流目录占位
```

尚未实现的目录使用 `.gitkeep` 保留。`.github/workflows/` 当前没有工作流，CI 在 Phase 11 交付；本地验证不能称为 GitHub CI 已通过。

## 基线

后端使用 Java 21、Spring Boot 3.5.16、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、JUnit 5、Testcontainers、JaCoCo；前端使用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest；本地容器使用 Docker、Compose、Nginx。四个后端提供真实 health/info；D 提供身份与公共层 API，M1 提供基础预约及签到/违约 API，D持久站内通知与M1生命周期调度已通过Phase5验证；M2提供申请审批与真实同步状态，M3仍保持工程与迁移基线。

四服务共用一个 PostgreSQL 实例，各自使用 `<schema>_app` 运行应用，以 `<schema>_migrator` 执行本服务的 Flyway 迁移。应用账号无 DDL 和迁移历史表权限，不能修改或删除审计记录；迁移账号不能访问其他服务 schema。公共层保持V4、预约服务保持V3，本轮M2追加V2，M2为V2，M3仍为V1；已发布迁移不可改写。跨服务只通过 HTTP。

七种角色、14 项权限及初始 35 条角色权限映射属于 V3 的应用参考目录，所有环境均加载；其中包含后续模块所需权限名称，不代表相应业务已经实现。`local` profile 另幂等写入原有 5 个演示用户、2 栋楼、4 间房间和每间 10 个座位，不扩充 seed，也不默认授予房间管理范围。演示密码由私有 `.env` 生成并保存为 BCrypt；重启不会重设已有密码。非本地 profile 不加载演示用户和空间，不要求演示密码，但必须提供数据源、迁移、JWT、内部调用方秘密及业务时区配置。

用户管理以停用替代删除；空间支持新增、修改和启用状态，本阶段不提供硬删除。M1 在本人预约的 `[T−15min,T+15min)` 窗口经真实 D 验码，在房间锁后重新校验时间，唯一签到和事件同事务写入；T+15 仍 CREATED 时写 NO_SHOW、唯一违约/系统事件并精确释放占用。D 站内通知按 M1 来源键去重，M1 持久提醒保存接收凭据；ACCEPTED 只表示 D 已入箱，不表示邮件送达或用户已读。默认独立扫描/发送任务各每30秒，调度池2，最多3次总尝试、网络/5xx退避30/60秒，4xx/坏契约终止；未过期提醒恢复可重试，过期跳过。签到/取消抑制待发提醒，已发出的跨服务请求不能原子撤回。违约仅本人查询，不引入封号/积分；保留期限和正常 COMPLETED 触发仍待审。完整Web、M3、真实M1占用集成和CI/CD留后续阶段，首版不用 Redis。

## 贡献与后续工作

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)。每个已验证、可运行的分项单独提交并同步 GitHub，阶段结束保留草稿 PR 等待人工审阅。Phase 6 完成后停止，等待明确确认再进入 Phase 7；完整后续范围保留在需求基线。

尚未选择开源许可证；上传仓库不自动授予额外使用许可。

Phase6已完成一次完整130项clean reactor（D48/M1 49/M2 28/M3 5），前端6项及lint/类型检查/构建通过。M2两级审批必须对应角色、当前权限和显式房间范围，禁止本人或同一人完成两级，无系统管理员越级覆盖。每级默认24小时并封顶开始时间；撤销仅未同步审批阶段。持久30秒租约、最多3次总尝试，网络/5xx退避30/60秒，4xx/坏回执终止。真实成功封房、已批准取消/到期释放和未知回执恢复仍属Phase8；审批状态与占用真值分开，不用边界夹具冒充跨模块成功。
