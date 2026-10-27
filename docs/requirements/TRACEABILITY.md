# 需求追踪矩阵（当前 Phase 7）

来源与需求正文见 [REQUIREMENTS.md](REQUIREMENTS.md)，原文见 [CODEX_EXECUTION_PROMPT.md](CODEX_EXECUTION_PROMPT.md)。每个需求 ID 对应一行；保留原文要求的 `ID | Requirement | Module | API/Page | Test | Status` 六列，并增加 Source、Phase。

**Phase7完整153项后端、前端6项、四份OpenAPI及首轮真实运行验收通过。** D37/M1 10/M2 6/M3 10操作；D资格与M3功能分项已上传[PR #8](https://github.com/Liinn0529/campus-resource-platform/pull/8)。实际M1未开放封房/释放，M3真实403如实SYNC_FAILED，无假维修下架或完成。运行证据见[Phase7验收](../review/phase-7-validation.md)。完成后等待Phase8，不提前实现。

状态语义：TODO=尚未实现/验证；PARTIAL=部分交付且仍需验收；BLOCKED=无法继续且已记录原因；DONE=阶段适用验收完成且有真实证据。DONE 不表示人工已批准schema、合并PR或下一阶段。本轮已获真实证据的阶段项逐项晋升；正常COMPLETED、完整Web与违约保留政策仍保持PARTIAL，不把本地验证称为GitHub CI。

OQ-01..15全部保留。Phase 5对签到窗口、NO_SHOW、站内入箱/恢复与最小违约可见范围给出具体草案，正常COMPLETED、保存期限、补签到、M2/M3释放和组织政策仍未定；不删除后续需求。

验收证据：历史[Phase 0](../review/phase-0-validation.md)、[Phase 1](../review/phase-1-validation.md)、[Phase 2](../review/phase-2-validation.md)、[Phase 3](../review/phase-3-validation.md)、[Phase 4](../review/phase-4-validation.md)保留；历史[Phase5](../review/phase-5-validation.md)、历史[Phase6](../review/phase-6-validation.md)、当前[Phase7计划](../plans/phase-7.md)与[本轮验收](../review/phase-7-validation.md)、[平台API](../api/platform.md)、[预约API](../api/reservation.md)及对应OpenAPI同步。本轮分支feature/phase-7-maintenance基于Phase6最终fa960e6，验证后按可运行分项上传，保持堆叠草稿人工review。

## Phase 0 文档交付

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-P0-001 | 创建 AGENTS.md，固化阶段门禁、开发原则、技术限制、人工审查与真实验证要求。 | 治理 | AGENTS.md | 已执行：文档存在性与源要求对照 | DONE | Phase 0; §四十一 | 0 |
| REQ-P0-002 | 创建 README.md，说明项目目标、当前仅 Phase 0、模块边界、文档入口和后续阶段。 | 治理 | README.md | 已执行：文档链接与范围检查 | DONE | Phase 0; §四十一 | 0 |
| REQ-P0-003 | 创建 REQUIREMENTS.md，为全部功能、工程要求、禁止事项与阶段要求分配稳定 ID，明确来源及计划阶段。 | 治理 | docs/requirements/REQUIREMENTS.md | 已执行：源文档逐节覆盖、ID 唯一性检查 | DONE | Phase 0 | 0 |
| REQ-P0-004 | 创建 TRACEABILITY.md，逐条映射需求、模块、API/页面、验证计划及真实状态，不删除未完成需求。 | 治理 | docs/requirements/TRACEABILITY.md | 已执行：需求与追踪矩阵双向集合一致性检查 | DONE | §二十三; Phase 0 | 0 |
| REQ-P0-005 | 创建 ARCHITECTURE.md，以文字说明四服务、前后端分离、HTTP 依赖、固定技术栈、并发和跨服务一致性。 | 架构 | docs/architecture/ARCHITECTURE.md | 已执行：架构与源要求对照 | DONE | Phase 0; §四、五 | 0 |
| REQ-P0-006 | 创建 DATA_OWNERSHIP.md，明确 D/M1/M2/M3、schema、表和主数据所有权及跨模块访问边界。 | 架构 | docs/architecture/DATA_OWNERSHIP.md | 已执行：服务—schema—数据所有权核对 | DONE | Phase 0; §六、七 | 0 |
| REQ-P0-007 | 创建 ADR-001-service-boundary.md，记录服务边界及禁止跨库访问的决策、理由和影响。 | 架构 | docs/adr/ADR-001-service-boundary.md | 已执行：ADR 决策与架构一致性检查 | DONE | Phase 0 | 0 |
| REQ-P0-008 | 创建基础目录及 phase-0.md；记录文件、设计、实际命令、真实验证结果、未完成项和建议提交信息；仅交付治理文档，不提前生成工程或业务代码。 | 治理 | docs/plans/phase-0.md; 基础目录 | 已执行：文件树、阶段范围与报告核对 | DONE | Phase 0; §四、四十一 | 0 |

## 执行治理、追踪与质量门禁

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-GOV-001 | 目标为可交付、可测试、可部署、可追踪的真实系统；遵守业务确定性、简单可维护、真实运行、可验证和明确边界的优先级。 | 全局 | 全项目 | 计划，未执行：设计评审与实际验收证据 | TODO | 角色; §一、二 | 0–15 |
| REQ-GOV-002 | 严格按 Phase 0→15 顺序，一次仅完成一个 Phase；禁止一次性生成整个系统及自动跳阶段。 | 全局 | 阶段执行记录 | 计划，未执行：阶段变更范围审查 | TODO | 角色; §二、四十 | 0–15 |
| REQ-GOV-003 | 每阶段完成列出修改文件、关键设计、实际命令、测试结果、未完成内容和建议 commit；等待人工确认后进入下一阶段。 | 全局 | docs/plans/phase-N.md（后续规划） | 计划，未执行：阶段报告及人工确认记录 | TODO | §四十、四十一 | 0–15 |
| REQ-GOV-004 | 执行前扫描仓库，保留有效代码，空仓库建基础目录，已有结构尽量复用；本次不开始 Phase 1、不建 Spring Boot 服务、不生成业务代码。 | 治理 | Phase 0 文件树 | 计划，未执行：初始扫描与最终 diff 核对 | TODO | §四十一 | 0 |
| REQ-GOV-005 | 分支使用 main、feature/*、fix/*；每个需求一个分支，main 保持可构建，禁止直接大规模修改 main。 | 全局 | Git 工作流 | 计划，未执行：分支及合并记录审查 | TODO | §二十二 | 0–15 |
| REQ-GOV-006 | PR 必须测试通过才合并；schema、公共契约及跨模块变更必须人工 review。 | 全局 | PR / review 记录 | 计划，未执行：合并门禁与审批记录 | TODO | §二十二; Phase 11 | 1–15 |
| REQ-GOV-007 | Git 语义冲突由人工处理，代理不得自动解决。 | 全局 | Git 冲突流程 | 计划，未执行：冲突处置记录审查 | TODO | §二、二十二 | 0–15 |
| REQ-GOV-008 | 追踪状态仅 TODO、PARTIAL、BLOCKED、DONE；未完成需求始终保留，示例 DONE 不代表真实完成。 | 全局 | TRACEABILITY.md | 计划，未执行：状态枚举及历史 diff 检查 | TODO | §二十三 | 0–15 |
| REQ-GOV-009 | 不得以 mock、假接口、假测试、假成功结果掩盖未完成；禁止 mock 掉真实业务后宣称集成完成。 | 全局 | 实现、测试及验收 | 计划，未执行：生产调用链与测试有效性核验 | TODO | 角色; §二、三十九 | 1–15 |
| REQ-GOV-010 | DONE 必须同时有需求 ID、真实业务逻辑、无生产 mock、必要 migration、通过的测试、更新的 API 文档、验证的 RBAC。 | 全局 | 各任务验收记录 | 计划，未执行：DoD 前六类工程证据 | TODO | §三十九 | 1–15 |
| REQ-GOV-011 | DONE 还必须无敏感日志、Docker 可运行、CI 通过、追踪表更新、人工验收可复现、Git 历史可追踪。 | 全局 | 各任务验收记录 | 计划，未执行：DoD 其余工程证据 | TODO | §三十九 | 1–15 |
| REQ-GOV-012 | 从 Git commit 可追踪到构建镜像、部署版本与发布记录，不宣称没有执行过的验证成功。 | 全局 | 版本及发布记录 | 计划，未执行：commit—image—release 核对 | TODO | §一、三十九 | 1–15 |

## 架构与技术栈

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-ARCH-001 | 使用 Monorepo、前后端分离、四个后端服务、REST API 和 PostgreSQL；清晰区分 D、M1、M2、M3。 | D/M1/M2/M3 | 四服务及 apps/web; D/M1 公开/内部 API | D 33/M1 8 操作及真实 HTTP/PostgreSQL 已实现并测试；M2/M3 业务仍留对应阶段 | PARTIAL | §一、四、五 | 0 设计; 1 实现 |
| REQ-ARCH-002 | 后端采用 Java 21、Spring Boot 3.x、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、OpenAPI 3、JUnit 5、Testcontainers、JaCoCo。 | 全局 | 后端依赖/构建; D/M1 OpenAPI JSON | Java 21/Maven/Boot/Security/MyBatis/PostgreSQL/Flyway/JUnit/Testcontainers/JaCoCo 构建与75项测试通过；D33/M1 8操作契约正式校验通过 | DONE | §三 | 1 起 |
| REQ-ARCH-003 | 前端采用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest、Playwright。 | Web | apps/web | 已执行：Vue 栈、6 tests/lint/build；业务 Playwright 待 Phase 10 | PARTIAL | §三 | 1 起 |
| REQ-ARCH-004 | 运维采用 Docker、Docker Compose、Nginx、GitHub Actions、华为云 SWR/ECS/RDS PostgreSQL。 | 运维 | deploy; .github/workflows | 已执行：Docker/Compose/Nginx 真实运行；Actions/华为云待后续 | PARTIAL | §三 | 1/11–14 |
| REQ-ARCH-005 | 遵循附录 A 的目标目录；Phase 0 只建文档与空目录，pom.xml、package.json、pnpm-workspace.yaml 等工程文件留后续。 | 全局 | 仓库目录 | 已执行：本阶段构建/应用/脚本文件；后续目录仍保留占位 | PARTIAL | §四 | 0 目录; 1 起文件 |
| REQ-ARCH-006 | 跨模块只能通过 HTTP API；业务服务通过 D 获取其拥有的身份、权限和空间主数据；M2/M3 通过 M1 创建及释放房间占用。 | D/M1/M2/M3 | M1/M2/M3 PlatformClient; D /me/room-scopes/内部资格读取 | 三服务真实HTTP读取D身份/资源，M2范围/M3资格与真实M1拒绝通过；M1实际占用/释放仍待Phase8，无跨schema | PARTIAL | §一、五、六 | 0 设计; 3–8 实现 |
| REQ-ARCH-007 | 一个 PostgreSQL 实例包含 platform、reservation、classroom、maintenance 四个独立 schema；各服务仅拥有对应 schema。 | D/M1/M2/M3 | deploy/postgres/010-service-databases.sh; 四服务 datasource/Flyway | 已执行：真实 PostgreSQL 空库创建四 schema，各服务 V1 执行与自身 current_schema 核对 | DONE | §六 | 2 |
| REQ-ARCH-008 | 每服务独立数据库账号且只可访问自身 schema；禁止跨 schema SQL、跨服务直接读表或写表。 | D/M1/M2/M3 | 四对 app/migrator 账号; DatabaseTestSupport | 已执行：各 app 本 schema DML；app/migrator 跨 schema 实际表读写均拒绝；app DDL/切换 owner/history 操作拒绝 | DONE | §六 | 2 |
| REQ-ARCH-009 | 禁止无必要的 Kafka、Nacos、Consul、Service Mesh、Event Sourcing、CQRS；M2 第一版不实现 BPMN 引擎。 | 全局 | 架构与依赖 | 计划，未执行：依赖及设计审查 | TODO | §二、十三 | 0–15 |
| REQ-ARCH-010 | 第一版不使用 Redis，也不使用 Redis lock；并发依赖 PostgreSQL room_gate 房间锁。 | 全局 | ReservationService; room_gate | M1 本地事务房间锁、首次六请求同座位竞争仅一成功；无 Redis，后续模块接入仍持续审查 | PARTIAL | §三、八 | 1–14 |
| REQ-ARCH-011 | 不使用分布式事务；本地持久化业务状态，通过 HTTP 和有限重试显式表达同步结果。 | M2/M3/M1 | M2/M3 sync state/client | 两服务显式持久同步状态、独立凭据、HTTP出事务、有限重试已验证；跨模块未知回执补偿待Phase8 | PARTIAL | §十 | 6–8 |
| REQ-ARCH-012 | ELB、CCE、DCS Redis 均为后续可选；CCE 仅 ECS+Compose 稳定后允许，不能借可选技术违反第一版不用 Redis 的约束。 | 运维 | 可选演进 ADR | 计划，未执行：演进门禁与人工范围确认 | TODO | §三、三十六 | 14 后可选 |

## 数据结构、迁移与演示数据

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-DATA-001 | 建立 room_gate、seat_reservation、seat_occupancy、checkin、violation、reservation_event；不能只靠 seat.status 表示全部业务状态。 | M1 | M1 V2/V3七表 | D4/M1 V3实际迁移和102项测试通过；M1七表、唯一签到/违约、来源账本和事件已验证 | DONE | §七 | 2/4/5 |
| REQ-DATA-002 | seat_occupancy 完整保留附录 B 的 11 个字段、可空性及来源枚举，能够追溯不可用来源。 | M1 | reservation V2 seat_occupancy | 真实 PostgreSQL 验证完整11字段、nullable seat/end、三来源、ACTIVE/RELEASED与释放信息；事件/账本一致性测试通过 | DONE | §七 | 2/4 |
| REQ-DATA-003 | 建立 room_gate(room_id PRIMARY KEY)，房间级互斥所需行必须可获取。 | M1 | reservation V2 room_gate; ReservationMapper | room_id 主键；事务INSERT ON CONFLICT后FOR UPDATE，首次同房间同座位并发仅一成功，真实锁竞争测试通过 | DONE | §八 | 2/4 |
| REQ-DATA-004 | 占用来源具有 (source_module, source_ref_id, source_type) 唯一约束。 | M1 | seat_occupancy occupancy_source_unique | V2 保留来源三元组唯一且取消不删记录、再预约新来源；M2/M3 幂等同/异载荷HTTP语义仍留Phase8 | PARTIAL | §九 | 2/8 |
| REQ-DATA-005 | 建立 room_checkin_code，保留 room_id、date、code、created_at；每日每 room 一个码并由数据库唯一约束保证。 | D | platform V2 room_checkin_code; CommonService | 已执行：真实 PostgreSQL V2、room/date 主键、Clock 上海日界、8 位码、并发 ensure 单记录/单审计和内部验码测试通过 | DONE | §十二 | 2/3 |
| REQ-DATA-006 | 建立 classroom_request、approval_step、approval_action、classroom_event。 | M2 | M2 V2四表 | V2真实迁移、约束及申请/步骤/动作/事件测试通过 | DONE | §十三 | 2/6 |
| REQ-DATA-007 | 建立 user、role、permission、user_role、role_permission，保留附录 C 的七类角色和权限示例。 | D | platform V1–V3; ManagementService; AccessPolicy | 已执行：五表/七角色/原九权限示例及 14 项目录、初始 35 关系；角色/范围管理和后端授权测试通过，方案仍待人工 review | DONE | §十五 | 2/3 |
| REQ-DATA-008 | 各服务 migration 放在 src/main/resources/db/migration/，使用 Flyway 版本命名，schema 版本化。 | D/M1/M2/M3 | 四服务 db/migration; D V1–V3 / M1 V1–V2 / M2/M3 V1 | 真实空库 Flyway 自动发现/校验 D V1–V3 与 M1 V1–V2；已发布旧迁移保持不变 | DONE | §二十四 | 2 起 |
| REQ-DATA-009 | migration 只向前；不修改已经执行/发布的 migration，修复新增 migration；禁止手工修改生产 schema。 | D/M1/M2/M3 | Flyway; 新 M1 V2; 生产流程 | Phase4只追加M1 V2，D V1–V3/其他V1保持不变；未来迁移与生产流程持续审查 | PARTIAL | §二、二十四 | 2 起 |
| REQ-DATA-010 | 全新空库可完整重放 migration；seed 与 schema migration 分离。 | D/M1/M2/M3 | 各服务 migration; D local seed 与 V3 参考目录 | 真实 PostgreSQL 空库 V1–V3/local seed/non-local 参考目录测试通过；Compose 保留 Phase 2 原卷升级 V1→V3，原演示实体和五用户密码指纹不变 | DONE | §二十四; Phase 2 | 2 |
| REQ-DATA-011 | 只提供最小演示 seed：1 system admin、2 students、1 room admin、1 property staff、2 buildings、4 rooms、40 seats。 | D | LocalDemoSeed; 六份独立 SQL | 已执行：精确 5 用户/5 角色关系/2 楼/4 房/40 座；每房 10 座；BCrypt 匹配且重复运行保留已有密码 | DONE | §十九 | 2 |
| REQ-DATA-012 | 禁止 AI 虚构业务数据或自动填满 seed；演示数据必须明确、最小且不冒充真实运营数据。 | 全局 | db/seed/local; V3 固定参考目录 | 保留原五演示用户/空间，无预约/审批/工单 seed；新增权限参考定义不增加演示用户，后续数据/页面持续审查 | PARTIAL | §十九 | 2 起 |

## D 公共层

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-D-001 | platform-service 提供用户、角色、权限和 RBAC 管理；具体权限分配必须后端生效。 | D | ManagementController/Service; AccessPolicy | 已执行：ManagementApiTest 的用户/角色/权限/房间范围管理、学生拒绝、最后启用管理员并发保护等真实 HTTP/DB 测试通过 | DONE | §五.1; Phase 3 | 3 |
| REQ-D-002 | 提供用户名/学号加密码登录并签发 JWT，第一版暂不接学校统一身份认证。 | D | POST /api/v1/auth/login; GET /me; 自助/管理改密 | 已执行：AuthenticationApiTest 12 项真实 HTTP/DB 通过；BCrypt 登录/改密、有效/错误/停用、签名/issuer/audience/严格到期/authVersion 及即时撤权 | DONE | §五.1、十六; Phase 3 | 3 |
| REQ-D-003 | 管理楼宇、教室、自习室与座位主数据，管理员可维护空间。 | D | SpaceController/Service; buildings/rooms/seats | 已执行：CommonResourcesTest 管理员新建/修改/启停楼宇房间座位、关联/唯一键、学生/跨房间拒绝通过；本阶段无硬删除 | DONE | §五.1; Phase 3 | 3 |
| REQ-D-004 | 管理系统参数，含最大连续预约时长；M1 通过约定 API 使用参数。 | D/M1 | D settings API; M1 PlatformClient.settings/TimeRules | 真实 D 默认4、HTTP修改当前参数为2后M1新建按新快照拒绝超长，已有预约不改写；测试通过，具体政策待审 | DONE | §五.1、十一; Phase 3 | 3/4 |
| REQ-D-005 | 每房间每天生成 6–8 位随机字符签到码；提供校验所需能力，可生成 QR Code。 | D | GET/POST /api/v1/rooms/{id}/checkin-code; internal verify | 已执行：每日 8 位随机码、room/date 唯一、Clock 上海日界、并发同条/单审计及 M1-only 校验测试通过；不含实际签到，QR 可选未实现 | DONE | §五.1、十二; Phase 3 | 3 |
| REQ-D-006 | 提供通知接口，供 M1 提醒调用；实际渠道、投递状态和去重策略待确认，不用假通知宣称送达。 | D/M1 | D POST/internal/v1/notifications; GET/api/v1/notifications; M1 reminder | 真实D站内接收/本人读取、来源幂等冲突、未知回执重放/有限尝试测试通过；仅持久入箱，不称邮件/已读 | DONE | §五.1、十一 | 3 契约; 5 实现 |
| REQ-D-007 | 提供操作审计，支持授权查询；业务事件与公共审计之间通过 HTTP 契约关联。 | D | AuditService/Mapper; GET /api/v1/audit-events | D 修改/审计同事务、授权查询、requestId 关联和 app UPDATE/DELETE 拒绝测试已通过；跨服务事件与公共审计的 HTTP 关联仍留后续 | PARTIAL | §五.1; Phase 3 | 3 |
| REQ-D-008 | 每个服务都提供 health 与 version/info；D 的职责列表不替代其他服务自身端点。 | D/M1/M2/M3 | GET /actuator/health; GET /actuator/info | 已执行：四服务真实 HTTP 测试，见 phase-1-backend.md | DONE | §五.1、二十八 | 1 |

## M1 自习座位预约

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-M1-001 | 查询自习室、座位和可用时段，结果综合 M1/M2/M3 占用。 | M1 | GET /api/v1/reservation/rooms/{id}/availability; rooms/seats | 真实D目录、所有ACTIVE来源、整房间null seat、开放结束与相邻区间测试通过；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §五.2、十一; Phase 4 | 4 |
| REQ-M1-002 | 支持按插座条件过滤座位，筛选结果与真实座位主数据一致。 | M1 | GET /api/v1/reservation/rooms/{id}/seats; availability | 真实D有/无插座过滤且SQL分页前筛选，HTTP测试通过；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §十一; Phase 4 | 4 |
| REQ-M1-003 | 预约按整点小时，默认最多连续 4 小时，最大时长读取系统参数。 | M1 | POST /api/v1/reservations; TimeRules; D settings | 注入Clock、offset转Instant、业务时区整点、默认4/超长/当前D参数变化与锁后复核测试通过；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §十一; Phase 4 | 4 |
| REQ-M1-004 | 创建预约前检查所有 M1/M2/M3 占用，已占用返回 HTTP 409，不允许重复预约。 | M1 | POST /api/v1/reservations; ReservationMapper | 真实PG六并发首次同座位仅一201其余409；M2/M3形状夹具、null end、相邻区间已验证；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §十一; Phase 4 | 4 |
| REQ-M1-005 | M1 创建、取消预约均在事务内 SELECT room_gate FOR UPDATE，随后检查并创建/释放占用，最后提交，锁粒度为 room。 | M1 | ReservationService create/cancel; room_gate | 真实事务锁竞争、不同座位、取消释放、账本不一致回滚测试通过；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §八; Phase 4 | 4 |
| REQ-M1-006 | 提供预约取消并释放对应占用；允许取消的状态及时间边界须先明确，不能臆造。 | M1 | POST /api/v1/reservations/{id}/cancel | 本人CREATED开始前取消/释放、重复幂等、开始时拒绝与单事件真实测试通过；具体方案待review；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §五.2、十一; Phase 4 | 4 |
| REQ-M1-007 | 提供当前预约、预约详情和历史预约查询；个人范围由后端权限限制。 | M1 | GET /api/v1/reservations?view=current 或 history; /{id} | 真实HTTP当前/取消或结束历史、本人详情及其他学生/管理员404测试通过；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §五.2、十一; Phase 4 | 4 |
| REQ-M1-008 | 可再次预约历史座位，创建新预约时仍执行时段、权限、参数和冲突检查。 | M1 | POST /api/v1/reservations/{id}/rebook | 真实HTTP历史新ID/rebookOf、新来源及当前元数据/参数/时段/冲突检查通过；首次Compose真实网关/数据库验收通过，最终干净版本重建另记 | DONE | §五.2、十一; Phase 4 | 4 |
| REQ-M1-009 | 预约状态限定为 CREATED、CANCELLED、CHECKED_IN、NO_SHOW、COMPLETED；完整转换守卫及完成触发规则待确认。 | M1 | M1状态守卫; checkin/NO_SHOW | 本轮实现CHECKED_IN/NO_SHOW及合法守卫，正常COMPLETED触发未定，保持PARTIAL | PARTIAL | §十一 | 4/5 |
| REQ-M1-010 | 以预约开始时间 T 为基准：T−15min 第一次提醒、T+10min 第二次提醒、T+15min 未签到判 NO_SHOW。 | M1/D | LifecycleService/Scheduler; reminder; D notification | T−15/T+10提醒与T+15 NO_SHOW真实HTTP/PostgreSQL+注入Clock验证，独立30秒任务和持久去重恢复通过 | DONE | §十一; Phase 5 | 5 |
| REQ-M1-011 | NO_SHOW 处置需结束预约有效性、释放 occupancy、新增 violation、新增 audit/event；最终持久状态与“取消预约”表述歧义见 OQ-01。 | M1 | LifecycleService; violation; 系统事件 | NO_SHOW/精确来源释放/唯一违约/actor=null系统事件同事务，重复/并发与异常回滚验证通过 | DONE | §十一; Phase 5 | 5 |
| REQ-M1-012 | 所有相关时间逻辑使用可注入 Clock，测试不得依赖真实等待或系统当前时间。 | M1 | TimeRules/ReservationService/LifecycleService; Clock | 基础预约及签到/提醒/截止/锁等待均使用注入Clock，真实HTTP测试无需等待15分钟 | DONE | §十一; Phase 5 | 5 |
| REQ-M1-013 | Web 输入签到码后校验 room、reservation 再完成签到；签到结果由后端确定。 | M1/D | POST/api/v1/reservations/{id}/checkin; D verify | 本人窗口/房间码/预约验证及幂等后端已实现，真实D/HTTP与并发/窗口验证通过；完整Web输入及E2E留Phase9/10 | PARTIAL | §十二; Phase 5 | 5 |
| REQ-M1-014 | 第一版签到不使用 GPS、人脸识别、蓝牙、NFC。 | M1/Web | CheckinInput; D verify; 依赖/采集范围 | 仅房间码和本人预约输入；实现/依赖与真实HTTP验证无GPS、人脸、蓝牙或NFC采集 | DONE | §十二 | 5 |
| REQ-M1-015 | M1 作为座位占用账本唯一所有者，提供 M2 教室占用、M3 维修占用及释放能力，并保留来源。 | M1 | seat_occupancy; ReservationMapper conflict query | M1拥有账本，三来源/null seat冲突读模型真实PG测试通过；M2/M3创建/释放HTTP留Phase8 | PARTIAL | §七、八、九; Phase 8 | 4 基础; 8 集成 |
| REQ-M1-016 | M2 创建/释放教室占用、M3 创建/释放维修占用均由 M1 在 room_gate 同房间锁事务内执行。 | M1/M2/M3 | room-blocks API | 计划，未执行：与 M1 预约并发的互斥验证 | TODO | §八 | 8 |
| REQ-M1-017 | 维修占用支持 endAt=null 的开放结束时间；释放时更新账本释放信息，区间冲突边界待确认。 | M1/M3 | seat_occupancy nullable end; conflict query | 真实PG中M3开放结束形状持续阻止冲突区间，半开边界已测；真实维修创建/释放HTTP留Phase8 | PARTIAL | §九 | 8 |

## M2 整教室申请与审批

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-M2-001 | 提供整教室申请、状态查询和详情，维护申请、审批步骤、审批动作与事件记录。 | M2 | /api/v1/classroom-requests | 真实D/HTTP提交、本人/范围待办、详情一致快照及事件测试通过 | DONE | §五.3、十三; Phase 6 | 6 |
| REQ-M2-002 | 第一版固定审批链：普通学生提交 → ROOM_ADMIN → DEPARTMENT_ADMIN，逐级审批。 | M2 | ClassroomService; D own scopes | 逐级角色/当前权限/范围、禁止自批/同人两级与即时撤权测试通过 | DONE | §十三; Phase 6 | 6 |
| REQ-M2-003 | 支持批准及驳回，必须按当前状态和审批步骤执行后端权限校验。 | M2 | POST approve/reject | 记录锁内状态/步骤守卫、并发单决定及损坏步骤原子回滚通过 | DONE | §五.3、十三; Phase 6 | 6 |
| REQ-M2-004 | 支持申请撤销及审批超时自动撤销；超时阈值、可撤销状态和与占用释放的协调待确认。 | M2 | POST cancel; timeout job | 仅前置审批撤销/每级24h封顶开始、锁后Clock/到期自动取消测试通过；占用后释放另属M2-008 | DONE | §五.3、十三; Phase 6 | 6 |
| REQ-M2-005 | 状态完整保留 SUBMITTED、APPROVING、APPROVED_PENDING_SYNC、APPROVED、REJECTED、CANCELLED、AUTO_CANCELLED、SYNC_FAILED。 | M2 | classroom_request状态约束 | 保留全部8状态；当前生产同步403失败，APPROVED分支仅明确回执夹具，真实成功仍见M2-006/010 | DONE | §十三 | 6 |
| REQ-M2-006 | 最终批准流程为 APPROVING → APPROVED_PENDING_SYNC → 调 M1 → APPROVED，只有 M1 真实占用成功后才能显示 APPROVED。 | M2/M1 | OccupancyClient; sync job | 真实HTTP/持久pending/失败及回执校验已实现；M1真实成功封房仍待Phase8，不能以夹具称完成 | PARTIAL | §十 | 6/8 |
| REQ-M2-007 | 占用同步失败保持明确中间态并按有限重试策略处理，最终失败标记 SYNC_FAILED，不默认审批成功。 | M2/M1 | ClassroomService.syncDue | 3总尝试、30/60退避、4xx终止、lease恢复/耗尽/迟到令牌；真实M1拒绝不假成功 | DONE | §十 | 6/8 |
| REQ-M2-008 | 需要释放教室时调用 M1 释放契约，禁止直接删除 M1 记录；取消与正常到期释放规则待确认。 | M2/M1 | post-sync cancel guard; Phase8 release | 当前禁止pending/syncfailed无确认取消；真实DELETE释放/到期补偿尚未实现 | PARTIAL | §五.3、九 | 6/8 |
| REQ-M2-009 | 单元测试可以 mock M1，生产代码必须使用真实 HTTP client；单测 mock 不能当作跨模块集成证据。 | M2 | OccupancyClient; SynchronizationBoundaryTest | 生产真实HTTP独立凭据/禁重定向，明确夹具只测回执边界，不充当Phase8成功 | DONE | Phase 6 | 6 |
| REQ-M2-010 | 真实验收：M2 审批成功 → HTTP M1 → 教室全部座位不可预约。 | M2/M1/Web | 审批页面 / availability | 计划，未执行：跨服务集成与 Playwright E2E | TODO | Phase 8 | 8 |

## M3 物业报修工单

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-M3-001 | 支持 report 故障上报、工单查询与详情。 | M3 | M3 report/list/detail | 真实D/HTTP/PostgreSQL与实际网关提交、SQL可见过滤、一致详情通过 | DONE | §五.4、十四; Phase 7 | 7 |
| REQ-M3-002 | 支持 assign 派单，派单动作由后端权限与状态守卫控制。 | M3 | assign; D staff eligibility | 当前PROPERTY_MANAGER+maintenance:assign，D实时enabled/PROPERTY_STAFF资格及非法状态/越权测试通过 | DONE | §五.4、十四; Phase 7 | 7 |
| REQ-M3-003 | start 开始维修按 ASSIGNED → BLOCKING_ROOM → 调 M1 → IN_PROGRESS 执行，未成功占用不能假报维修下架完成。 | M3/M1 | start; OccupancyClient BLOCK | 真实M1当前403→SYNC_FAILED；匹配ACTIVE回执后IN_PROGRESS仅明确边界夹具通过，真实封房待Phase8 | PARTIAL | §十、十四; Phase 7 | 7/8 |
| REQ-M3-004 | 支持 finish 完工及 WAITING_ACCEPTANCE，完工到待验收的守卫需明确。 | M3 | finish | 当前被派PROPERTY_STAFF且IN_PROGRESS才WAITING_ACCEPTANCE；真实D/M3库加明确M1回执夹具验证 | DONE | §十四; Phase 7 | 7 |
| REQ-M3-005 | 支持 accept 验收并完成工单；验收完成后通过 M1 恢复座位，释放失败恢复策略待确认。 | M3/M1 | accept; OccupancyClient RELEASE | 本人验收排RELEASE，匹配原ID/来源RELEASED才COMPLETED，失败保留占用；真实M1恢复仍待Phase8 | PARTIAL | §五.4、十四; Phase 7/8 | 7/8 |
| REQ-M3-006 | 支持 rejectAcceptance 拒绝验收；返回状态、后续返修及占用延续规则待确认。 | M3 | reject-acceptance | 本人拒绝验收回IN_PROGRESS、保留占用、增加周期并重开期限；排释放后拒绝409 | DONE | §十四 | 7 |
| REQ-M3-007 | 支持 cancel 取消，需定义可取消状态及现有维修占用的释放规则。 | M3 | cancel | 本人仅REPORTED/ASSIGNED可取消；并发start/cancel一胜，开始同步后无确认释放不假取消 | DONE | §五.4、十四; Phase 7 | 7 |
| REQ-M3-008 | 支持 review 评价，评价主体、次数与可评价阶段待确认。 | M3 | review | 仅报修人COMPLETED后一次1–5分/≤500字；越权/未完成/重复拒绝，真实库唯一评价 | DONE | §五.4、十四; Phase 7 | 7 |
| REQ-M3-009 | 使用 due_at 定时扫描超时，写 escalation event 并在 UI 标记，不能仅改变展示而无事件。 | M3 | scanEscalations; escalated | due_at锁后Clock、达到即升级、每周期一次系统事件、返修重开均通过；完整Web标记待Phase9 | PARTIAL | §十四; Phase 7 | 7 |
| REQ-M3-010 | 状态完整保留 REPORTED、ASSIGNED、BLOCKING_ROOM、IN_PROGRESS、WAITING_ACCEPTANCE、COMPLETED、CANCELLED、SYNC_FAILED。 | M3 | M3 V2状态约束 | 完整八状态、显式BLOCK/RELEASE、状态守卫和事件测试通过；实际集成另见M3-003/005/012 | DONE | §十四 | 7 |
| REQ-M3-011 | 网络错误和 5xx 允许有限重试，4xx 不重试；超出次数进入 SYNC_FAILED。 | M3/M1 | syncDue; OccupancyClient | 三总尝试/30、60退避、4xx/坏回执终止、lease恢复及耗尽无第四次通过；真实403不重试 | DONE | §十 | 7/8 |
| REQ-M3-012 | 真实验收：报修 → 开始维修 → HTTP M1 → 房间座位下架 → 验收完成 → M1 恢复。 | M3/M1/Web | 工单/座位页面 | 计划，未执行：跨服务集成与 Playwright E2E | TODO | Phase 8 | 8 |

## HTTP 契约与同步语义

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-API-001 | 公开 API 使用 /api/v1，内部 API 使用 /internal/v1，OpenAPI 3 文档随真实契约维护。 | D/M1/M2/M3 | D /api/v1与/internal/v1; M1/M2/M3 /api/v1; 四份OpenAPI | D37/M1 10/M2 6/M3 10操作与Controller同步且正式OpenAPI通过；M1占用写契约留Phase8 | PARTIAL | §十七 | 1 契约; 3–8 实现 |
| REQ-API-002 | 成功响应统一含 data、requestId；失败响应统一含 code、message、requestId，保留业务错误码。 | D/M1/M2/M3 | D/M1/M2/M3 ApiEnvelope/ApiErrors; X-Request-ID | D/M1/M2/M3真实成功/失败响应与关联ID测试通过；网关自身错误及后续模块持续维护 | PARTIAL | §十七 | 3–8 |
| REQ-API-003 | 按语义使用 200、201、204、400、401、403、404、409、422、500、503；禁止所有接口都返回 200。 | D/M1/M2/M3 | D/M1 Controller/异常/权限边界 | D/M1真实200/201/400/401/403/404/409/422/500/503语义测试通过；暂无204/硬删除，后续模块持续验证 | PARTIAL | §十七 | 3–8 |
| REQ-API-004 | 提供 POST /internal/v1/room-blocks，请求字段 roomId、startAt、endAt、sourceModule、sourceType、sourceRefId 与附录 D 一致。 | M1/M2/M3 | POST /internal/v1/room-blocks | 计划，未执行：M2/M3 示例及非法组合契约测试 | TODO | §九 | 8 |
| REQ-API-005 | 提供 DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId} 释放来源占用；不得擅自更改路径解决三元唯一键歧义。 | M1/M2/M3 | DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId} | 计划，未执行：释放目标、重复释放及歧义确认后的契约测试 | TODO | §九 | 8 |
| REQ-API-006 | 同一来源重复请求内容一致返回已有结果，内容冲突返回 409 Conflict；不能产生重复占用。 | M1/M2/M3 | room-blocks API | 计划，未执行：同键同载荷、同键异载荷及并发重复测试 | TODO | §九 | 8 |
| REQ-API-007 | 只重试网络错误和 5xx，不重试 4xx，最多 3 次，超出进入 SYNC_FAILED；“3 次”计数口径待确认。 | M2/M3/M1 | M2/M3 bounded sync | M2/M3均实现三总尝试、网络/5xx退避、4xx终止与持久恢复；实际M1封房/释放失败恢复联调待Phase8 | PARTIAL | §十 | 6–8 |
| REQ-API-008 | API、HTTP client 和前端数据必须来自真实业务实现，公共契约更新后经人工审查并同步文档。 | D/M1/M2/M3 | 四服务API/OpenAPI; PlatformClient; 草稿PR | 四服务真实MyBatis/HTTP，153项及四份契约通过；草稿PR仍待人工review，实际M1集成/业务Web留Phase8/9 | PARTIAL | §一、十七、三十九 | 3–15 |

## 认证、授权和安全边界

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-SEC-001 | 密码使用 BCrypt 存储和校验，不保存明文或可逆密码。 | D | AuthenticationService; ManagementService; LocalDemoSeed | 已执行：BCrypt(12) seed/登录/自助改密/管理员重置、UTF-8 边界和错误密码/旧凭据失效测试通过；响应无明文或哈希 | DONE | §十六、二十九 | 3 |
| REQ-SEC-002 | JWT 必须验证且有 expiration；失效、过期或篡改凭证不能认证。 | D/全服务 | PlatformSecurity; JwtEncoder/Decoder; authVersion | 已执行：D 固定 HS256/issuer/audience、必需时间及严格 exp、签名/authVersion/DB 启用校验；正常/篡改/过期/边界失效测试通过 | DONE | §十六、二十九 | 3 |
| REQ-SEC-003 | 权限必须后端校验，前端隐藏按钮不能作为权限控制；角色与个人数据范围隔离。 | 全服务/Web | D/M1 AccessPolicy; M1 RemoteAuthenticationFilter | D即时权限/房间范围及M1真实D即时撤权/停用、本人范围、spoof userId拒绝通过；未来模块/Web持续补齐 | PARTIAL | §十五、二十九 | 3 起 |
| REQ-SEC-004 | RBAC 保留附录 C 的角色和权限示例；角色到权限及组织/房间范围映射须在实现前确认。 | D | V3 七角色/14 权限参考目录; role/room-scope API | 七角色/14 权限目录、SYSTEM_ADMIN 全局与 ROOM_ADMIN 显式范围测试通过；具体映射/组织范围仍待人工 review | PARTIAL | §十五 | 3 |
| REQ-SEC-005 | 生产只通过 HTTPS 暴露业务流量。 | 运维 | Nginx / 公网入口 | 计划，未执行：HTTP 暴露面与 HTTPS 访问检查 | TODO | §十六、二十九 | 13/14 |
| REQ-SEC-006 | SQL 参数化，禁止把不可信输入拼接为 SQL。 | 全服务 | D/M1 MyBatis Mapper; seed参数绑定 | D/M1 Mapper与seed使用绑定参数，真实HTTP/数据库边界测试通过；后续模块继续审查 | PARTIAL | §二十九 | 3 起 |
| REQ-SEC-007 | CORS 使用明确白名单，避免任意来源访问。 | 全服务 | D/M1 明确CORS origins | D/M1明确白名单；M1实际本地预检200且匹配ACAO，未知来源403且无ACAO；其他模块/生产持续维护 | PARTIAL | §二十九 | 1 配置; 3 起验证 |
| REQ-SEC-008 | Git 不包含 token、密码或密钥；部署脚本不得硬编码凭据。 | 全局 | 私有 .env; init-env; Compose 运行注入 | JWT/数据库/内部调用秘密从私有环境提供，测试仅隔离凭据；本次提交敏感值检查与后续部署持续验证 | PARTIAL | §二、二十九、三十五 | 0–15 |
| REQ-SEC-009 | 生产不得保留默认密码，初始管理员凭据必须外部提供并明确运维流程。 | 运维/D | 生产用户初始化 | 计划，未执行：无默认凭据验证 | TODO | §二十九 | 13/14 |
| REQ-SEC-010 | 数据库只在私网可达，不向公网开放。 | 数据库/运维 | RDS / 网络规则 | 计划，未执行：公网拒绝及应用私网连接验证 | TODO | §二十九 | 13/14 |
| REQ-SEC-011 | 内部 API 不得公开，生产 Swagger 必须限制访问；内部服务认证机制与凭据轮换待确认。 | 全服务/运维 | D internal 安全链; Nginx 隐藏路由; 静态 OpenAPI | D 三调用方 opaque 身份与 M1-only 验码测试通过；实际网关 internal 返回 404，用户 JWT 不能替代服务身份；生产秘密轮换/文档策略仍留后续 | PARTIAL | §二十九 | 1 设计; 8/14 验证 |
| REQ-SEC-012 | 日志不得记录 password、JWT、refresh token、签到码明文或敏感个人信息。 | 全服务 | RequestLoggingFilter; ApiExceptionHandler; 审计字段 | 实际 D Compose 成功/失败请求日志抽查未发现本地私有凭据或生成签到码；模板化路径/最小审计字段已实现，未来全服务与生产日志持续验证 | PARTIAL | §二十七 | 1 起 |

## 代码约束与可观测性

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-ENG-001 | 不创建无意义抽象；简单 CRUD 不堆 Manager/Helper/Factory/Adapter；内部代码保持简洁。 | 全局 | 代码结构 | 计划，未执行：实现评审 | TODO | §二、十八 | 1–15 |
| REQ-ENG-002 | 严格校验集中在 HTTP request、JWT、权限、DB constraint、外部 HTTP response、状态机转换和环境变量边界。 | 全服务 | D边界; M1请求/权限/DB/外部HTTP响应/转换/env | M1新增严格外部响应与来源、未知字段、当前身份/权限、时间/状态和环境校验；真实业务与6项HTTP边界测试通过 | PARTIAL | §十八 | 1–15 |
| REQ-ENG-003 | 禁止每层重复 null check/重复校验、内部对象反复 Bean Validation、无意义 Optional。 | 全服务 | 内部调用链 | 计划，未执行：代码审查 | TODO | §二、十八 | 1–15 |
| REQ-ENG-004 | 禁止所有方法 try/catch、catch Exception 后返回默认值、无意义 fallback、默认成功和无限重试。 | 全服务 | 异常处理 / HTTP client | 计划，未执行：故障注入与失败透明性验证 | TODO | §二、十八 | 1–15 |
| REQ-ENG-005 | 避免大量无意义日志；最低记录 timestamp、level、service、requestId、userId、method、path、status、latencyMs。 | 全服务 | 四服务RequestLoggingFilter; 异常类型/关联ID | 四服务模板路径和最小关联字段已实现；D/M1/M2历史及M3本轮实际日志按交付证据检查，后续渠道/生产持续验证 | PARTIAL | §十八、二十七 | 1–15 |
| REQ-ENG-006 | 注释仅解释业务规则、架构原因、并发逻辑和跨模块契约，不生成大段教学式注释。 | 全局 | 代码注释 | 计划，未执行：代码审查 | TODO | §十九 | 1–15 |
| REQ-ENG-007 | GET /actuator/info 返回 service、version、gitCommit、buildTime，值来自真实构建。 | 全服务 | GET /actuator/info | 已执行：build-info.properties 与四服务真实 info HTTP 核对 | DONE | §二十八 | 1 |
| REQ-ENG-008 | AI 仅用于开发及最后可选 Agent，不成为核心业务真值来源，不决定权限、审批、预约、座位或工单状态。 | 全局 | AI 边界 | 计划，未执行：架构及调用路径审查 | TODO | §一、十九、三十七 | 0–15 |
| REQ-ENG-009 | 禁止 AI 推荐座位、AI 智能维修、AI 自动审批、AI 自动生成公告、AI 生成运营结论和 AI 虚构内容。 | Web/全局 | UI / 业务服务 | 计划，未执行：页面、数据及功能范围审查 | TODO | §十九 | 1–15 |

## 前端页面与真实业务流程

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-UI-001 | 提供 /login 与 /student/home，以真实身份和业务数据呈现入口。 | Web/D | /login; /student/home | 计划，未执行：登录与学生首页 E2E | TODO | §二十 | 9 |
| REQ-UI-002 | 提供自习室列表、详情、预约列表、详情及历史五类页面，完整保留附录 E 路由。 | Web/M1 | /student/rooms; /student/rooms/:id; /student/reservations; /student/reservations/:id; /student/history | 计划，未执行：搜索、预约、取消、签到、历史与再预约 E2E | TODO | §二十 | 9 |
| REQ-UI-003 | 提供学生整教室申请及报修页面。 | Web/M2/M3 | /student/classroom-applications; /student/repairs | 计划，未执行：提交、查询及可用操作 E2E | TODO | §二十 | 9 |
| REQ-UI-004 | 提供审批列表和审批详情。 | Web/M2 | /approvals; /approvals/:id | 计划，未执行：各级审批、驳回及权限 E2E | TODO | §二十 | 9 |
| REQ-UI-005 | 提供维修工单列表和详情。 | Web/M3 | /maintenance; /maintenance/:id | 计划，未执行：派单、维修、验收、评价及超时标记 E2E | TODO | §二十 | 9 |
| REQ-UI-006 | 提供空间、房间、座位、用户、角色、设置、审计七个管理页面。 | Web/D | /admin/spaces; /admin/rooms; /admin/seats; /admin/users; /admin/roles; /admin/settings; /admin/audit | 计划，未执行：管理流程及学生访问拒绝 E2E | TODO | §二十 | 9 |
| REQ-UI-007 | UI 清晰、克制、业务化、响应式；学生端 Mobile friendly，管理端 Desktop first。 | Web | 全部页面 | 计划，未执行：手机及桌面尺寸人工验收 | TODO | §二十一 | 9 |
| REQ-UI-008 | 避免大量渐变、无意义动画、AI 风格装饰、Fake Dashboard、无真实来源图表。 | Web | 全部页面 | 计划，未执行：视觉与数据来源审查 | TODO | §二十一 | 9 |
| REQ-UI-009 | 学生、审批、维修、管理主要业务通过 Web 完成，无需 Postman。 | Web/全服务 | 全角色业务页面 | 计划，未执行：真实账号端到端人工验收 | TODO | Phase 9 | 9 |

## 测试层次与有效性

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-TEST-001 | 单元测试验证状态机、时间规则、权限、最大预约时间、取消规则和超时逻辑。 | 全局 | D真实测试; M1 TimeRulesTest/ReservationWorkflowTest | 当前身份权限、整点/最大时长、开始前取消/历史再预约与Clock边界通过；提醒/违约/审批维修超时留后续 | PARTIAL | §二、二十五 | 3–10 |
| REQ-TEST-002 | 不测试 getter/setter、框架本身、常量；禁止测试只断言常量或 mock 被测逻辑本身。 | 全局 | 测试代码 | 计划，未执行：测试有效性审查 | TODO | §二、二十五 | 1–15 |
| REQ-TEST-003 | 集成测试使用 Testcontainers PostgreSQL，覆盖 Flyway 空库重放和 unique constraint。 | D/M1/M2/M3 | DatabaseTestSupport; M1真实D JAR/PG fixture | 真实PostgreSQL空库重放D V1–V4/M1 V1–V3/M2 V1–V2/M3 V1–V2，隔离、唯一约束/并发及本地流程通过；跨模块幂等仍待Phase8 | PARTIAL | §二十五 | 2–10 |
| REQ-TEST-004 | 集成测试覆盖并发预约、room_gate lock 和占用互斥，确认冲突不会生成双重预约。 | M1 | ReservationWorkflowTest 首次六请求并发 | 实际PostgreSQL房间锁；首次同座位六竞争仅一201/五409，仅一预约及占用；不同座位均可成功 | DONE | §二十五 | 4/10 |
| REQ-TEST-005 | 集成测试通过真实 HTTP 覆盖 M2→M1、M3→M1 和 idempotency，不以生产 mock 替代。 | M2/M3/M1 | 跨服务集成测试 | 计划，未执行：成功、重复、冲突和失败结果 | TODO | §二十五 | 8/10 |
| REQ-TEST-006 | API 测试覆盖 401、403、正常请求、409、非法参数和角色隔离。 | 全服务 | D/M1真实HTTP/PostgreSQL测试 | D与M1的401/403/正常/409/非法参数/角色及本人隔离通过；其他模块继续补齐 | PARTIAL | §二十五 | 3–10 |
| REQ-TEST-007 | Playwright E2E 覆盖学生登录 → 搜索 → 预约，以及签到。 | Web/M1/D | 学生页面 | 计划，未执行：真实后端 Playwright 报告 | TODO | §二十五 | 9/10 |
| REQ-TEST-008 | Playwright E2E 覆盖整教室申请 → 审批 → M1 座位不可约。 | Web/M2/M1 | 申请/审批/座位页面 | 计划，未执行：真实跨模块 Playwright 报告 | TODO | §二十五 | 9/10 |
| REQ-TEST-009 | Playwright E2E 覆盖报修 → 开始维修 → M1 下架，以及验收 → 座位恢复。 | Web/M3/M1 | 报修/维修/座位页面 | 计划，未执行：真实跨模块 Playwright 报告 | TODO | §二十五 | 9/10 |
| REQ-TEST-010 | 至少一次 mutation-style 人工验证：临时破坏业务规则，确认测试失败，恢复代码并重新验证。 | 全局 | 实际变异验证 | 计划，未执行：变异失败与恢复通过的真实输出 | TODO | §二十六 | 10 |
| REQ-TEST-011 | 在 docs/review/test-effectiveness.md 记录变异内容、命令、失败证据、恢复和结果；4 小时改为 8 小时为源示例。 | 全局 | docs/review/test-effectiveness.md | 计划，未执行：记录与真实执行证据核对 | TODO | §二十六 | 10 |
| REQ-TEST-012 | 提供 scripts/test-all.sh，整合 Testcontainers、Playwright 和跨服务测试，实际执行退出码必须为 0。 | 全局 | scripts/test-all.sh | 计划，未执行：全量脚本退出码与报告 | TODO | Phase 10 | 10 |

## 本地运行、CI/CD、发布和云部署

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-OPS-001 | 开发 Compose 包含 postgres、四业务服务、web、nginx；mailhog 可选。 | 运维 | compose.yaml; deploy/nginx/nginx.conf | 已执行：compose up -d、七容器 healthy 与 HTTP smoke | DONE | §三十; Phase 1 | 1 |
| REQ-OPS-002 | 生产数据库不得放入应用 Compose，使用独立数据库服务。 | 运维 | 生产 Compose | 计划，未执行：配置审查与外部数据库连接 | TODO | §三十 | 13/14 |
| REQ-OPS-003 | PR 流水线依次执行 checkout、backend lint、backend test、frontend lint、frontend unit test、integration test、build、docker build。 | CI | .github/workflows/ci.yml | 计划，未执行：完整 CI 实际运行 | TODO | §三十一; Phase 11 | 11 |
| REQ-OPS-004 | 任一步骤失败立即停止；lint、test、build 失败不能合并。 | CI | CI / 分支保护 | 计划，未执行：各类失败与合并门禁验证 | TODO | §三十一; Phase 11 | 11 |
| REQ-OPS-005 | main merge 触发构建镜像、commit SHA 标签、推送 registry、部署测试服务器、运行 Flyway、compose pull/up、health、smoke test。 | CD | deploy-test.yml | 计划，未执行：合并触发的真实测试部署 | TODO | §三十二; Phase 12 | 12 |
| REQ-OPS-006 | Git tag（如 v0.1.0）触发生产发布，必须人工批准后执行。 | CD | 生产发布流程 | 计划，未执行：tag、批准和部署记录 | TODO | §三十二; Phase 13 | 13 |
| REQ-OPS-007 | 每次发布保存 tag、commit SHA、image tag、migration version、deploy time、operator。 | 运维 | 发布记录 | 计划，未执行：六字段完整及相互可追踪 | TODO | §三十二 | 13 |
| REQ-OPS-008 | 以 RELEASE_TAG 选择镜像版本，回滚执行旧 tag 的 compose pull 和 compose up -d。 | 运维 | deploy.sh / rollback.sh | 计划，未执行：v0.1.0→v0.2.0→回滚 v0.1.0 | TODO | §三十三; Phase 13 | 13 |
| REQ-OPS-009 | schema 优先向前兼容，应用回滚不能依赖 migration downgrade。 | 数据库/运维 | migration / 回滚说明 | 计划，未执行：新 schema 上旧应用兼容验收 | TODO | §三十三 | 2 设计; 13 验证 |
| REQ-OPS-010 | 华为云采用 Internet→HTTPS→EIP/可选 ELB→Nginx→ECS→Compose 四服务→RDS PostgreSQL。 | 运维 | deploy/huawei | 计划，未执行：云端完整访问及依赖验证 | TODO | §三十四; Phase 14 | 14 |
| REQ-OPS-011 | 镜像由 GitHub Actions 构建推送 Huawei SWR，再由 ECS 拉取运行。 | 运维 | Actions / SWR / ECS | 计划，未执行：镜像推拉与版本追踪 | TODO | §三十四; Phase 14 | 14 |
| REQ-OPS-012 | 创建 deploy/huawei/README.md、bootstrap-ecs.sh、deploy.sh、rollback.sh、healthcheck.sh、docker-compose.prod.yml、nginx.conf、env.example。 | 运维 | deploy/huawei | 计划，未执行：八份文件与实际部署验证 | TODO | §三十五 | 14 |
| REQ-OPS-013 | bootstrap-ecs.sh 安装 Docker、Compose plugin，创建目录、设置运行用户、启用 Docker，不硬编码凭据。 | 运维 | bootstrap-ecs.sh | 计划，未执行：干净 ECS 引导执行及配置审查 | TODO | §三十五 | 14 |
| REQ-OPS-014 | 验收公网只开放 HTTPS、DB 不开放公网、SWR 可拉取、CI/CD 可部署、health 正常、版本可追踪。 | 运维 | 华为云环境 | 计划，未执行：六项云端验收记录 | TODO | Phase 14 | 14 |
| REQ-OPS-015 | ECS+Compose 稳定后才允许 deploy/helm；Compose service 映射 Kubernetes Deployment+Service。 | 运维 | deploy/helm（可选） | 计划，未执行：演进批准与部署验证 | TODO | §三十六 | 14 后可选 |
| REQ-OPS-016 | 可选 CCE 演进保持 image、env、health endpoint、database 不变。 | 运维 | CCE / Helm（可选） | 计划，未执行：四项兼容性核验 | TODO | §三十六 | 14 后可选 |

## 可选自然语言业务 Agent

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-AGENT-001 | Agent 只能最后实现，前面全部阶段完成后才可开始；不属于 Phase 0 交付。 | Optional | 可选 Agent | 计划，未执行：前置阶段验收及范围确认 | TODO | §三十七; Phase 15 | 15 可选 |
| REQ-AGENT-002 | Agent 不得直接访问数据库，只能调用公开 API，沿用真实后端鉴权。 | Optional | API 工具层 | 计划，未执行：数据库不可达及 API 调用审查 | TODO | §三十七 | 15 可选 |
| REQ-AGENT-003 | 支持公开工具 searchSeats、createReservation、listReservationHistory。 | Optional/M1 | 三个 M1 Agent 工具 | 计划，未执行：真实 API 工具行为验证 | TODO | §三十七 | 15 可选 |
| REQ-AGENT-004 | 支持 createClassroomApplication、getClassroomApplication、cancelClassroomApplication。 | Optional/M2 | 三个 M2 Agent 工具 | 计划，未执行：真实 API 工具行为验证 | TODO | §三十七 | 15 可选 |
| REQ-AGENT-005 | 支持 createRepairTicket、getRepairTicket、cancelRepairTicket。 | Optional/M3 | 三个 M3 Agent 工具 | 计划，未执行：真实 API 工具行为验证 | TODO | §三十七 | 15 可选 |
| REQ-AGENT-006 | 教室申请参数不足必须追问日期、开始时间、结束时间、区域、用途，不得猜测填充。 | Optional | 对话参数收集 | 计划，未执行：缺项及模糊表达验证 | TODO | §三十七; Phase 15 | 15 可选 |
| REQ-AGENT-007 | LLM 仅完成自然语言→intent→tool call→template/structured result，权限、审批、预约结果、座位和工单状态均由业务 API 决定。 | Optional | Agent 调用与结果 | 计划，未执行：越权、失败和真实结果一致性验证 | TODO | §三十七; Phase 15 | 15 可选 |

## 工程伦理记录

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-ETH-001 | 持续维护 docs/ethics/ETHICS_LOG.md，记录实际问题与决策，不在最后补写空泛文章。 | 治理 | docs/ethics/ETHICS_LOG.md | 随Phase1–4记录身份/范围/最小数据、预约时间/取消/历史保留和快照边界；持续事项不以文档存在完成 | PARTIAL | §三十八 | 3 起持续 |
| REQ-ETH-002 | 记录并决定违约记录保存周期，实施前明确保留、删除和可见范围。 | M1/D | ETHICS_LOG; GET/api/v1/violations | 最小违约字段仅本人可见；保留/删除/申诉周期仍待审，不假定永久保存，不加处罚 | PARTIAL | §三十八 | 3–5 起持续 |
| REQ-ETH-003 | 记录管理员权限边界及个人信息最小化问题。 | D/全服务 | D 角色/显式 room scope; ETHICS_LOG | 记录系统管理员/房间管理员边界、五用户 seed 和字段最小化；方案待审，部门审批与生产个人信息仍留后续 | PARTIAL | §三十八 | 3 起持续 |
| REQ-ETH-004 | 记录日志数据相关风险及处置，遵守敏感字段禁止记录要求。 | 全服务 | D 请求/异常/审计日志; ETHICS_LOG | 已记录日志最小化与审计保留风险；本轮实际 D 日志抽查无私有凭据/生成码，未来模块/部署继续处置和审阅 | PARTIAL | §三十八 | 1 起持续 |
| REQ-ETH-005 | 记录 Agent 越权风险与基于 API 权限的约束措施。 | Optional | Agent / ETHICS_LOG | 计划，未执行：越权测试与风险处置 | TODO | §三十八 | 15 可选 |
| REQ-ETH-006 | 记录自动撤销风险，包括时间边界、错误处置和受影响用户的处理。 | M1/M2 | NO_SHOW / 审批超时 | 计划，未执行：边界案例与伦理决策记录 | TODO | §三十八 | 5/6 起持续 |
| REQ-ETH-007 | 记录维修下架误伤风险及对既有预约的处理决策。 | M3/M1 | ETHICS_LOG Phase7 | 已记录维修误伤/既有预约不静默取消、验收释放失败/未知回执风险；实际冲突告知/恢复待Phase8review | PARTIAL | §三十八 | 7/8 起持续 |

## 后续阶段范围与验收门禁

| ID | Requirement | Module | API/Page | Test | Status | Source | Phase |
|---|---|---|---|---|---|---|---|
| REQ-PH-001 | 仅获下一阶段确认后创建 4 Spring Boot services、1 Vue app、Dockerfile、Compose、Nginx；每服务有 health/info，docker compose up -d 后全部 healthy。 | 全局 | 工程骨架 | 已执行：后端 16 tests、前端 6 tests/build、七容器/HTTP/浏览器验收 | DONE | Phase 1 | 1 |
| REQ-PH-002 | 建四 schema、独立账号、Flyway、最小 seed；开发环境 docker compose down -v 后 up -d，全新数据库自动 migration。 | 数据库 | 四服务数据库工程; deploy/postgres; LocalDemoSeed | 已执行：23 项 JUnit/Testcontainers；Compose down -v/up -d 空卷自动迁移、七容器健康、数据库隔离检查；保留卷重启计数/密码哈希不变，见 Phase 2 验证记录 | DONE | Phase 2 | 2 |
| REQ-PH-003 | 实现 login、RBAC、user、role、permission、building、room、seat、settings、checkin code、audit；管理员能维护空间，学生无管理权限，后端 RBAC 生效。 | D | D 32 操作; V2/V3; OpenAPI/运行脚本 | 已执行：后端 49 项/前端 6 项及 lint/类型检查/build 通过；保留原卷 V1→V3、七容器 healthy、真实五用户登录/管理员空间与参数/范围内房管/日码/审计/学生拒绝/内部隐藏及恢复检查全部通过 | DONE | Phase 3 | 3 |
| REQ-PH-004 | 实现房间/座位/可用性查询、创建、取消、历史、再预约、room_gate、seat_occupancy；验收不能重复预约、默认 4 小时、插座过滤、历史再预约。 | M1 | M1 V2/8 API; OpenAPI; 运行脚本 | 75项后端/前端6项与构建通过；保留原卷M1 V1→V2、七容器、网关公共层回归与预约/冲突/插座/取消/历史再预约、隔离/账本和数据保留检查通过；最终干净版本重建另记 | DONE | Phase 4 | 4 |
| REQ-PH-005 | 实现签到、T−15/T+10 提醒、T+15 NO_SHOW、违约和占用释放，使用可注入 Clock。 | M1/D | M1生命周期; D站内收件箱; V3/V4 | 完整clean reactor102项、前端6项、保留原卷D4/M1 V3升级及网关检查通过；最终干净SHA另核对，结束等待Phase6 | DONE | Phase 5 | 5 |
| REQ-PH-006 | 实现 submit、approval、approve、reject、cancel、timeout；单元测试可 mock M1，生产必须真实 HTTP client。 | M2 | M2六操作/真实D/M1客户端 | 130测试及首轮保留卷/真实两级审批验收通过，分项已上传；最终SHA重建结果另见仓库外交付报告 | DONE | Phase 6 | 6 |
| REQ-PH-007 | 实现 report、assign、start、finish、accept、cancel、review、escalate，并满足 §十四 rejectAcceptance 要求。 | M3 | M3十操作/升级任务 | 153后端与首轮原卷升级/真实工单验收通过；实际M1联动、完整UI留Phase8/9 | DONE | Phase 7 | 7 |
| REQ-PH-008 | 连接 M2→M1、M3→M1，删除生产 mock；以真实审批占用及维修下架/验收恢复 E2E 验收。 | M2/M3/M1 | 跨模块集成 | 计划，未执行：两条真实跨模块流程 | TODO | Phase 8 | 8 |
| REQ-PH-009 | 完成 student、approval、maintenance、admin 前端流程，主要业务无需 Postman。 | Web | apps/web | 计划，未执行：全角色 Web 人工验收 | TODO | Phase 9 | 9 |
| REQ-PH-010 | 建 Testcontainers、Playwright、cross-service integration、scripts/test-all.sh；实际执行脚本退出 0。 | 测试 | 测试体系 | 计划，未执行：全量测试实际通过 | TODO | Phase 10 | 10 |
| REQ-PH-011 | 创建 .github/workflows/ci.yml；lint/test/build 失败均不能合并。 | CI | CI | 计划，未执行：失败门禁验收 | TODO | Phase 11 | 11 |
| REQ-PH-012 | 创建 deploy-test.yml；main merge→build→push→deploy→migration→health→smoke test。 | CD | 测试环境部署 | 计划，未执行：真实自动部署验收 | TODO | Phase 12 | 12 |
| REQ-PH-013 | 创建 deploy.sh、rollback.sh、RELEASE.md；验收 v0.1.0→v0.2.0→rollback v0.1.0。 | 运维 | 生产发布和回滚 | 计划，未执行：发布、批准、回滚与版本记录 | TODO | Phase 13 | 13 |
| REQ-PH-014 | 部署 SWR、ECS、RDS PostgreSQL、Nginx、HTTPS，并完成公网、私网、镜像、CI/CD、健康和版本六项验收。 | 运维 | 华为云部署 | 计划，未执行：云端真实验收 | TODO | Phase 14 | 14 |
| REQ-PH-015 | 前面全部完成后，可实现自然语言→intent→API tool→structured result；模糊需求追问、真实 API、无数据库直连。 | Optional | 自然语言 Agent | 计划，未执行：三项 Agent 验收 | TODO | Phase 15 | 15 可选 |

## 基线统计与更新规则

- 需求总数：186。
- 当前状态：DONE 66；TODO 78；PARTIAL 42；BLOCKED 0。
- 分类计数：REQ-P0: 8, REQ-GOV: 12, REQ-ARCH: 12, REQ-DATA: 12, REQ-D: 8, REQ-M1: 17, REQ-M2: 10, REQ-M3: 12, REQ-API: 8, REQ-SEC: 12, REQ-ENG: 9, REQ-UI: 9, REQ-TEST: 12, REQ-OPS: 16, REQ-AGENT: 7, REQ-ETH: 7, REQ-PH: 15。
- API/Page 和 Test 的计划内容须在对应阶段替换/补充为真实契约、代码/测试路径和执行证据；保留源要求与稳定 ID。
- 标记 DONE 前，检查源 §三十九 DoD 和本阶段适用范围，更新实际验收记录；不得把计划测试名称、文档审阅或工具退出码冒充业务测试通过。
- Phase 0–4保留为历史基线；当前仅Phase7获授权，完成后停止等待Phase8。未完成的正常COMPLETED、完整Web、真实M1封房/释放和CI/CD需求继续保留，不以模型/文档代替验收。

本轮真实封房/释放依赖继续保留M2-006/008为PARTIAL、M2-010为TODO；不提前执行Phase8。阶段本地功能完成后才逐项晋升，GitHub CI仍未配置。
