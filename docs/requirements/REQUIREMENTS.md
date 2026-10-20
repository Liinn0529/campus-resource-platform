# 需求基线（Phase 0）

本项目为“校园资源服务平台”。本文件将源执行提示转换为稳定、可验收的需求，不代表功能已经实现。唯一业务来源为 [CODEX_EXECUTION_PROMPT.md](CODEX_EXECUTION_PROMPT.md)；来源栏的“§一…§四十一”对应原文中文章节，“§五.1”等对应章节内服务小节，“Phase N”对应原文阶段标题。原文没有明确的设计不得借本文件悄然补全，集中记录于未决问题。

本次授权仅覆盖 Phase 0：治理、需求与架构文档。Phase 1–15、构建配置、数据库 migration、业务实现、自动化测试、CI/CD、真实部署均未开始。下表中的阶段是后续规划，不构成执行授权。Phase 0 八条文档项经整体检查后记 DONE（仅表示文档交付验收）；其他条目全部 TODO。治理约束虽适用于本次工作，其持续性验收也不因写入文档而自动 DONE。

模块：D=platform-service（公共层），M1=reservation-service（自习座位与占用真值），M2=classroom-service（整教室审批），M3=maintenance-service（物业工单），Web=apps/web，Optional=最后可选自然语言 Agent。D 提供身份、权限、空间主数据、参数、签到码、通知和审计；M1/M2/M3 不得直接读 D 表。M2/M3 的房间占用及释放通过 M1 HTTP 契约执行。

需求 ID 一经分配保持稳定：修改条目内容保留 ID；拆分时新增 ID 并记录关联，不重排现有编号。需求与 [TRACEABILITY.md](TRACEABILITY.md) 一对一；矩阵“Test”为计划验证方式，当前没有已实现或已执行的业务自动化测试；Phase 0 的实际文档检查见验证记录。来源中的示例字段、路径和枚举按附录保留；源示例测试名和 DONE 状态不作为项目现状。

共 **186 条需求**。各分类数量：REQ-P0: 8, REQ-GOV: 12, REQ-ARCH: 12, REQ-DATA: 12, REQ-D: 8, REQ-M1: 17, REQ-M2: 10, REQ-M3: 12, REQ-API: 8, REQ-SEC: 12, REQ-ENG: 9, REQ-UI: 9, REQ-TEST: 12, REQ-OPS: 16, REQ-AGENT: 7, REQ-ETH: 7, REQ-PH: 15。

## Phase 0 文档交付

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-P0-001 | Phase 0; §四十一 | 治理 | 0 | 创建 AGENTS.md，固化阶段门禁、开发原则、技术限制、人工审查与真实验证要求。 |
| REQ-P0-002 | Phase 0; §四十一 | 治理 | 0 | 创建 README.md，说明项目目标、当前仅 Phase 0、模块边界、文档入口和后续阶段。 |
| REQ-P0-003 | Phase 0 | 治理 | 0 | 创建 REQUIREMENTS.md，为全部功能、工程要求、禁止事项与阶段要求分配稳定 ID，明确来源及计划阶段。 |
| REQ-P0-004 | §二十三; Phase 0 | 治理 | 0 | 创建 TRACEABILITY.md，逐条映射需求、模块、API/页面、验证计划及真实状态，不删除未完成需求。 |
| REQ-P0-005 | Phase 0; §四、五 | 架构 | 0 | 创建 ARCHITECTURE.md，以文字说明四服务、前后端分离、HTTP 依赖、固定技术栈、并发和跨服务一致性。 |
| REQ-P0-006 | Phase 0; §六、七 | 架构 | 0 | 创建 DATA_OWNERSHIP.md，明确 D/M1/M2/M3、schema、表和主数据所有权及跨模块访问边界。 |
| REQ-P0-007 | Phase 0 | 架构 | 0 | 创建 ADR-001-service-boundary.md，记录服务边界及禁止跨库访问的决策、理由和影响。 |
| REQ-P0-008 | Phase 0; §四、四十一 | 治理 | 0 | 创建基础目录及 phase-0.md；记录文件、设计、实际命令、真实验证结果、未完成项和建议提交信息；仅交付治理文档，不提前生成工程或业务代码。 |

## 执行治理、追踪与质量门禁

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-GOV-001 | 角色; §一、二 | 全局 | 0–15 | 目标为可交付、可测试、可部署、可追踪的真实系统；遵守业务确定性、简单可维护、真实运行、可验证和明确边界的优先级。 |
| REQ-GOV-002 | 角色; §二、四十 | 全局 | 0–15 | 严格按 Phase 0→15 顺序，一次仅完成一个 Phase；禁止一次性生成整个系统及自动跳阶段。 |
| REQ-GOV-003 | §四十、四十一 | 全局 | 0–15 | 每阶段完成列出修改文件、关键设计、实际命令、测试结果、未完成内容和建议 commit；等待人工确认后进入下一阶段。 |
| REQ-GOV-004 | §四十一 | 治理 | 0 | 执行前扫描仓库，保留有效代码，空仓库建基础目录，已有结构尽量复用；本次不开始 Phase 1、不建 Spring Boot 服务、不生成业务代码。 |
| REQ-GOV-005 | §二十二 | 全局 | 0–15 | 分支使用 main、feature/*、fix/*；每个需求一个分支，main 保持可构建，禁止直接大规模修改 main。 |
| REQ-GOV-006 | §二十二; Phase 11 | 全局 | 1–15 | PR 必须测试通过才合并；schema、公共契约及跨模块变更必须人工 review。 |
| REQ-GOV-007 | §二、二十二 | 全局 | 0–15 | Git 语义冲突由人工处理，代理不得自动解决。 |
| REQ-GOV-008 | §二十三 | 全局 | 0–15 | 追踪状态仅 TODO、PARTIAL、BLOCKED、DONE；未完成需求始终保留，示例 DONE 不代表真实完成。 |
| REQ-GOV-009 | 角色; §二、三十九 | 全局 | 1–15 | 不得以 mock、假接口、假测试、假成功结果掩盖未完成；禁止 mock 掉真实业务后宣称集成完成。 |
| REQ-GOV-010 | §三十九 | 全局 | 1–15 | DONE 必须同时有需求 ID、真实业务逻辑、无生产 mock、必要 migration、通过的测试、更新的 API 文档、验证的 RBAC。 |
| REQ-GOV-011 | §三十九 | 全局 | 1–15 | DONE 还必须无敏感日志、Docker 可运行、CI 通过、追踪表更新、人工验收可复现、Git 历史可追踪。 |
| REQ-GOV-012 | §一、三十九 | 全局 | 1–15 | 从 Git commit 可追踪到构建镜像、部署版本与发布记录，不宣称没有执行过的验证成功。 |

## 架构与技术栈

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-ARCH-001 | §一、四、五 | D/M1/M2/M3 | 0 设计; 1 实现 | 使用 Monorepo、前后端分离、四个后端服务、REST API 和 PostgreSQL；清晰区分 D、M1、M2、M3。 |
| REQ-ARCH-002 | §三 | 全局 | 1 起 | 后端采用 Java 21、Spring Boot 3.x、Spring Security、MyBatis、PostgreSQL、Flyway、Maven、Bean Validation、OpenAPI 3、JUnit 5、Testcontainers、JaCoCo。 |
| REQ-ARCH-003 | §三 | Web | 1 起 | 前端采用 Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios、Vitest、Playwright。 |
| REQ-ARCH-004 | §三 | 运维 | 1/11–14 | 运维采用 Docker、Docker Compose、Nginx、GitHub Actions、华为云 SWR/ECS/RDS PostgreSQL。 |
| REQ-ARCH-005 | §四 | 全局 | 0 目录; 1 起文件 | 遵循附录 A 的目标目录；Phase 0 只建文档与空目录，pom.xml、package.json、pnpm-workspace.yaml 等工程文件留后续。 |
| REQ-ARCH-006 | §一、五、六 | D/M1/M2/M3 | 0 设计; 3–8 实现 | 跨模块只能通过 HTTP API；业务服务通过 D 获取其拥有的身份、权限和空间主数据；M2/M3 通过 M1 创建及释放房间占用。 |
| REQ-ARCH-007 | §六 | D/M1/M2/M3 | 2 | 一个 PostgreSQL 实例包含 platform、reservation、classroom、maintenance 四个独立 schema；各服务仅拥有对应 schema。 |
| REQ-ARCH-008 | §六 | D/M1/M2/M3 | 2 | 每服务独立数据库账号且只可访问自身 schema；禁止跨 schema SQL、跨服务直接读表或写表。 |
| REQ-ARCH-009 | §二、十三 | 全局 | 0–15 | 禁止无必要的 Kafka、Nacos、Consul、Service Mesh、Event Sourcing、CQRS；M2 第一版不实现 BPMN 引擎。 |
| REQ-ARCH-010 | §三、八 | 全局 | 1–14 | 第一版不使用 Redis，也不使用 Redis lock；并发依赖 PostgreSQL room_gate 房间锁。 |
| REQ-ARCH-011 | §十 | M2/M3/M1 | 6–8 | 不使用分布式事务；本地持久化业务状态，通过 HTTP 和有限重试显式表达同步结果。 |
| REQ-ARCH-012 | §三、三十六 | 运维 | 14 后可选 | ELB、CCE、DCS Redis 均为后续可选；CCE 仅 ECS+Compose 稳定后允许，不能借可选技术违反第一版不用 Redis 的约束。 |

## 数据结构、迁移与演示数据

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-DATA-001 | §七 | M1 | 2/4/5 | 建立 room_gate、seat_reservation、seat_occupancy、checkin、violation、reservation_event；不能只靠 seat.status 表示全部业务状态。 |
| REQ-DATA-002 | §七 | M1 | 2/4 | seat_occupancy 完整保留附录 B 的 11 个字段、可空性及来源枚举，能够追溯不可用来源。 |
| REQ-DATA-003 | §八 | M1 | 2/4 | 建立 room_gate(room_id PRIMARY KEY)，房间级互斥所需行必须可获取。 |
| REQ-DATA-004 | §九 | M1 | 2/8 | 占用来源具有 (source_module, source_ref_id, source_type) 唯一约束。 |
| REQ-DATA-005 | §十二 | D | 2/3 | 建立 room_checkin_code，保留 room_id、date、code、created_at；每日每 room 一个码并由数据库唯一约束保证。 |
| REQ-DATA-006 | §十三 | M2 | 2/6 | 建立 classroom_request、approval_step、approval_action、classroom_event。 |
| REQ-DATA-007 | §十五 | D | 2/3 | 建立 user、role、permission、user_role、role_permission，保留附录 C 的七类角色和权限示例。 |
| REQ-DATA-008 | §二十四 | D/M1/M2/M3 | 2 起 | 各服务 migration 放在 src/main/resources/db/migration/，使用 Flyway 版本命名，schema 版本化。 |
| REQ-DATA-009 | §二、二十四 | D/M1/M2/M3 | 2 起 | migration 只向前；不修改已经执行/发布的 migration，修复新增 migration；禁止手工修改生产 schema。 |
| REQ-DATA-010 | §二十四; Phase 2 | D/M1/M2/M3 | 2 | 全新空库可完整重放 migration；seed 与 schema migration 分离。 |
| REQ-DATA-011 | §十九 | D | 2 | 只提供最小演示 seed：1 system admin、2 students、1 room admin、1 property staff、2 buildings、4 rooms、40 seats。 |
| REQ-DATA-012 | §十九 | 全局 | 2 起 | 禁止 AI 虚构业务数据或自动填满 seed；演示数据必须明确、最小且不冒充真实运营数据。 |

## D 公共层

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-D-001 | §五.1; Phase 3 | D | 3 | platform-service 提供用户、角色、权限和 RBAC 管理；具体权限分配必须后端生效。 |
| REQ-D-002 | §五.1、十六; Phase 3 | D | 3 | 提供用户名/学号加密码登录并签发 JWT，第一版暂不接学校统一身份认证。 |
| REQ-D-003 | §五.1; Phase 3 | D | 3 | 管理楼宇、教室、自习室与座位主数据，管理员可维护空间。 |
| REQ-D-004 | §五.1、十一; Phase 3 | D/M1 | 3/4 | 管理系统参数，含最大连续预约时长；M1 通过约定 API 使用参数。 |
| REQ-D-005 | §五.1、十二; Phase 3 | D | 3 | 每房间每天生成 6–8 位随机字符签到码；提供校验所需能力，可生成 QR Code。 |
| REQ-D-006 | §五.1、十一 | D/M1 | 3 契约; 5 实现 | 提供通知接口，供 M1 提醒调用；实际渠道、投递状态和去重策略待确认，不用假通知宣称送达。 |
| REQ-D-007 | §五.1; Phase 3 | D | 3 | 提供操作审计，支持授权查询；业务事件与公共审计之间通过 HTTP 契约关联。 |
| REQ-D-008 | §五.1、二十八 | D/M1/M2/M3 | 1 | 每个服务都提供 health 与 version/info；D 的职责列表不替代其他服务自身端点。 |

## M1 自习座位预约

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-M1-001 | §五.2、十一; Phase 4 | M1 | 4 | 查询自习室、座位和可用时段，结果综合 M1/M2/M3 占用。 |
| REQ-M1-002 | §十一; Phase 4 | M1 | 4 | 支持按插座条件过滤座位，筛选结果与真实座位主数据一致。 |
| REQ-M1-003 | §十一; Phase 4 | M1 | 4 | 预约按整点小时，默认最多连续 4 小时，最大时长读取系统参数。 |
| REQ-M1-004 | §十一; Phase 4 | M1 | 4 | 创建预约前检查所有 M1/M2/M3 占用，已占用返回 HTTP 409，不允许重复预约。 |
| REQ-M1-005 | §八; Phase 4 | M1 | 4 | M1 创建、取消预约均在事务内 SELECT room_gate FOR UPDATE，随后检查并创建/释放占用，最后提交，锁粒度为 room。 |
| REQ-M1-006 | §五.2、十一; Phase 4 | M1 | 4 | 提供预约取消并释放对应占用；允许取消的状态及时间边界须先明确，不能臆造。 |
| REQ-M1-007 | §五.2、十一; Phase 4 | M1 | 4 | 提供当前预约、预约详情和历史预约查询；个人范围由后端权限限制。 |
| REQ-M1-008 | §五.2、十一; Phase 4 | M1 | 4 | 可再次预约历史座位，创建新预约时仍执行时段、权限、参数和冲突检查。 |
| REQ-M1-009 | §十一 | M1 | 4/5 | 预约状态限定为 CREATED、CANCELLED、CHECKED_IN、NO_SHOW、COMPLETED；完整转换守卫及完成触发规则待确认。 |
| REQ-M1-010 | §十一; Phase 5 | M1/D | 5 | 以预约开始时间 T 为基准：T−15min 第一次提醒、T+10min 第二次提醒、T+15min 未签到判 NO_SHOW。 |
| REQ-M1-011 | §十一; Phase 5 | M1 | 5 | NO_SHOW 处置需结束预约有效性、释放 occupancy、新增 violation、新增 audit/event；最终持久状态与“取消预约”表述歧义见 OQ-01。 |
| REQ-M1-012 | §十一; Phase 5 | M1 | 5 | 所有相关时间逻辑使用可注入 Clock，测试不得依赖真实等待或系统当前时间。 |
| REQ-M1-013 | §十二; Phase 5 | M1/D | 5 | Web 输入签到码后校验 room、reservation 再完成签到；签到结果由后端确定。 |
| REQ-M1-014 | §十二 | M1/Web | 5 | 第一版签到不使用 GPS、人脸识别、蓝牙、NFC。 |
| REQ-M1-015 | §七、八、九; Phase 8 | M1 | 4 基础; 8 集成 | M1 作为座位占用账本唯一所有者，提供 M2 教室占用、M3 维修占用及释放能力，并保留来源。 |
| REQ-M1-016 | §八 | M1/M2/M3 | 8 | M2 创建/释放教室占用、M3 创建/释放维修占用均由 M1 在 room_gate 同房间锁事务内执行。 |
| REQ-M1-017 | §九 | M1/M3 | 8 | 维修占用支持 endAt=null 的开放结束时间；释放时更新账本释放信息，区间冲突边界待确认。 |

## M2 整教室申请与审批

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-M2-001 | §五.3、十三; Phase 6 | M2 | 6 | 提供整教室申请、状态查询和详情，维护申请、审批步骤、审批动作与事件记录。 |
| REQ-M2-002 | §十三; Phase 6 | M2 | 6 | 第一版固定审批链：普通学生提交 → ROOM_ADMIN → DEPARTMENT_ADMIN，逐级审批。 |
| REQ-M2-003 | §五.3、十三; Phase 6 | M2 | 6 | 支持批准及驳回，必须按当前状态和审批步骤执行后端权限校验。 |
| REQ-M2-004 | §五.3、十三; Phase 6 | M2 | 6 | 支持申请撤销及审批超时自动撤销；超时阈值、可撤销状态和与占用释放的协调待确认。 |
| REQ-M2-005 | §十三 | M2 | 6 | 状态完整保留 SUBMITTED、APPROVING、APPROVED_PENDING_SYNC、APPROVED、REJECTED、CANCELLED、AUTO_CANCELLED、SYNC_FAILED。 |
| REQ-M2-006 | §十 | M2/M1 | 6/8 | 最终批准流程为 APPROVING → APPROVED_PENDING_SYNC → 调 M1 → APPROVED，只有 M1 真实占用成功后才能显示 APPROVED。 |
| REQ-M2-007 | §十 | M2/M1 | 6/8 | 占用同步失败保持明确中间态并按有限重试策略处理，最终失败标记 SYNC_FAILED，不默认审批成功。 |
| REQ-M2-008 | §五.3、九 | M2/M1 | 6/8 | 需要释放教室时调用 M1 释放契约，禁止直接删除 M1 记录；取消与正常到期释放规则待确认。 |
| REQ-M2-009 | Phase 6 | M2 | 6 | 单元测试可以 mock M1，生产代码必须使用真实 HTTP client；单测 mock 不能当作跨模块集成证据。 |
| REQ-M2-010 | Phase 8 | M2/M1/Web | 8 | 真实验收：M2 审批成功 → HTTP M1 → 教室全部座位不可预约。 |

## M3 物业报修工单

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-M3-001 | §五.4、十四; Phase 7 | M3 | 7 | 支持 report 故障上报、工单查询与详情。 |
| REQ-M3-002 | §五.4、十四; Phase 7 | M3 | 7 | 支持 assign 派单，派单动作由后端权限与状态守卫控制。 |
| REQ-M3-003 | §十、十四; Phase 7 | M3/M1 | 7/8 | start 开始维修按 ASSIGNED → BLOCKING_ROOM → 调 M1 → IN_PROGRESS 执行，未成功占用不能假报维修下架完成。 |
| REQ-M3-004 | §十四; Phase 7 | M3 | 7 | 支持 finish 完工及 WAITING_ACCEPTANCE，完工到待验收的守卫需明确。 |
| REQ-M3-005 | §五.4、十四; Phase 7/8 | M3/M1 | 7/8 | 支持 accept 验收并完成工单；验收完成后通过 M1 恢复座位，释放失败恢复策略待确认。 |
| REQ-M3-006 | §十四 | M3 | 7 | 支持 rejectAcceptance 拒绝验收；返回状态、后续返修及占用延续规则待确认。 |
| REQ-M3-007 | §五.4、十四; Phase 7 | M3 | 7 | 支持 cancel 取消，需定义可取消状态及现有维修占用的释放规则。 |
| REQ-M3-008 | §五.4、十四; Phase 7 | M3 | 7 | 支持 review 评价，评价主体、次数与可评价阶段待确认。 |
| REQ-M3-009 | §十四; Phase 7 | M3 | 7 | 使用 due_at 定时扫描超时，写 escalation event 并在 UI 标记，不能仅改变展示而无事件。 |
| REQ-M3-010 | §十四 | M3 | 7 | 状态完整保留 REPORTED、ASSIGNED、BLOCKING_ROOM、IN_PROGRESS、WAITING_ACCEPTANCE、COMPLETED、CANCELLED、SYNC_FAILED。 |
| REQ-M3-011 | §十 | M3/M1 | 7/8 | 网络错误和 5xx 允许有限重试，4xx 不重试；超出次数进入 SYNC_FAILED。 |
| REQ-M3-012 | Phase 8 | M3/M1/Web | 8 | 真实验收：报修 → 开始维修 → HTTP M1 → 房间座位下架 → 验收完成 → M1 恢复。 |

## HTTP 契约与同步语义

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-API-001 | §十七 | D/M1/M2/M3 | 1 契约; 3–8 实现 | 公开 API 使用 /api/v1，内部 API 使用 /internal/v1，OpenAPI 3 文档随真实契约维护。 |
| REQ-API-002 | §十七 | D/M1/M2/M3 | 3–8 | 成功响应统一含 data、requestId；失败响应统一含 code、message、requestId，保留业务错误码。 |
| REQ-API-003 | §十七 | D/M1/M2/M3 | 3–8 | 按语义使用 200、201、204、400、401、403、404、409、422、500、503；禁止所有接口都返回 200。 |
| REQ-API-004 | §九 | M1/M2/M3 | 8 | 提供 POST /internal/v1/room-blocks，请求字段 roomId、startAt、endAt、sourceModule、sourceType、sourceRefId 与附录 D 一致。 |
| REQ-API-005 | §九 | M1/M2/M3 | 8 | 提供 DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId} 释放来源占用；不得擅自更改路径解决三元唯一键歧义。 |
| REQ-API-006 | §九 | M1/M2/M3 | 8 | 同一来源重复请求内容一致返回已有结果，内容冲突返回 409 Conflict；不能产生重复占用。 |
| REQ-API-007 | §十 | M2/M3/M1 | 6–8 | 只重试网络错误和 5xx，不重试 4xx，最多 3 次，超出进入 SYNC_FAILED；“3 次”计数口径待确认。 |
| REQ-API-008 | §一、十七、三十九 | D/M1/M2/M3 | 3–15 | API、HTTP client 和前端数据必须来自真实业务实现，公共契约更新后经人工审查并同步文档。 |

## 认证、授权和安全边界

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-SEC-001 | §十六、二十九 | D | 3 | 密码使用 BCrypt 存储和校验，不保存明文或可逆密码。 |
| REQ-SEC-002 | §十六、二十九 | D/全服务 | 3 | JWT 必须验证且有 expiration；失效、过期或篡改凭证不能认证。 |
| REQ-SEC-003 | §十五、二十九 | 全服务/Web | 3 起 | 权限必须后端校验，前端隐藏按钮不能作为权限控制；角色与个人数据范围隔离。 |
| REQ-SEC-004 | §十五 | D | 3 | RBAC 保留附录 C 的角色和权限示例；角色到权限及组织/房间范围映射须在实现前确认。 |
| REQ-SEC-005 | §十六、二十九 | 运维 | 13/14 | 生产只通过 HTTPS 暴露业务流量。 |
| REQ-SEC-006 | §二十九 | 全服务 | 3 起 | SQL 参数化，禁止把不可信输入拼接为 SQL。 |
| REQ-SEC-007 | §二十九 | 全服务 | 1 配置; 3 起验证 | CORS 使用明确白名单，避免任意来源访问。 |
| REQ-SEC-008 | §二、二十九、三十五 | 全局 | 0–15 | Git 不包含 token、密码或密钥；部署脚本不得硬编码凭据。 |
| REQ-SEC-009 | §二十九 | 运维/D | 13/14 | 生产不得保留默认密码，初始管理员凭据必须外部提供并明确运维流程。 |
| REQ-SEC-010 | §二十九 | 数据库/运维 | 13/14 | 数据库只在私网可达，不向公网开放。 |
| REQ-SEC-011 | §二十九 | 全服务/运维 | 1 设计; 8/14 验证 | 内部 API 不得公开，生产 Swagger 必须限制访问；内部服务认证机制与凭据轮换待确认。 |
| REQ-SEC-012 | §二十七 | 全服务 | 1 起 | 日志不得记录 password、JWT、refresh token、签到码明文或敏感个人信息。 |

## 代码约束与可观测性

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-ENG-001 | §二、十八 | 全局 | 1–15 | 不创建无意义抽象；简单 CRUD 不堆 Manager/Helper/Factory/Adapter；内部代码保持简洁。 |
| REQ-ENG-002 | §十八 | 全服务 | 1–15 | 严格校验集中在 HTTP request、JWT、权限、DB constraint、外部 HTTP response、状态机转换和环境变量边界。 |
| REQ-ENG-003 | §二、十八 | 全服务 | 1–15 | 禁止每层重复 null check/重复校验、内部对象反复 Bean Validation、无意义 Optional。 |
| REQ-ENG-004 | §二、十八 | 全服务 | 1–15 | 禁止所有方法 try/catch、catch Exception 后返回默认值、无意义 fallback、默认成功和无限重试。 |
| REQ-ENG-005 | §十八、二十七 | 全服务 | 1–15 | 避免大量无意义日志；最低记录 timestamp、level、service、requestId、userId、method、path、status、latencyMs。 |
| REQ-ENG-006 | §十九 | 全局 | 1–15 | 注释仅解释业务规则、架构原因、并发逻辑和跨模块契约，不生成大段教学式注释。 |
| REQ-ENG-007 | §二十八 | 全服务 | 1 | GET /actuator/info 返回 service、version、gitCommit、buildTime，值来自真实构建。 |
| REQ-ENG-008 | §一、十九、三十七 | 全局 | 0–15 | AI 仅用于开发及最后可选 Agent，不成为核心业务真值来源，不决定权限、审批、预约、座位或工单状态。 |
| REQ-ENG-009 | §十九 | Web/全局 | 1–15 | 禁止 AI 推荐座位、AI 智能维修、AI 自动审批、AI 自动生成公告、AI 生成运营结论和 AI 虚构内容。 |

## 前端页面与真实业务流程

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-UI-001 | §二十 | Web/D | 9 | 提供 /login 与 /student/home，以真实身份和业务数据呈现入口。 |
| REQ-UI-002 | §二十 | Web/M1 | 9 | 提供自习室列表、详情、预约列表、详情及历史五类页面，完整保留附录 E 路由。 |
| REQ-UI-003 | §二十 | Web/M2/M3 | 9 | 提供学生整教室申请及报修页面。 |
| REQ-UI-004 | §二十 | Web/M2 | 9 | 提供审批列表和审批详情。 |
| REQ-UI-005 | §二十 | Web/M3 | 9 | 提供维修工单列表和详情。 |
| REQ-UI-006 | §二十 | Web/D | 9 | 提供空间、房间、座位、用户、角色、设置、审计七个管理页面。 |
| REQ-UI-007 | §二十一 | Web | 9 | UI 清晰、克制、业务化、响应式；学生端 Mobile friendly，管理端 Desktop first。 |
| REQ-UI-008 | §二十一 | Web | 9 | 避免大量渐变、无意义动画、AI 风格装饰、Fake Dashboard、无真实来源图表。 |
| REQ-UI-009 | Phase 9 | Web/全服务 | 9 | 学生、审批、维修、管理主要业务通过 Web 完成，无需 Postman。 |

## 测试层次与有效性

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-TEST-001 | §二、二十五 | 全局 | 3–10 | 单元测试验证状态机、时间规则、权限、最大预约时间、取消规则和超时逻辑。 |
| REQ-TEST-002 | §二、二十五 | 全局 | 1–15 | 不测试 getter/setter、框架本身、常量；禁止测试只断言常量或 mock 被测逻辑本身。 |
| REQ-TEST-003 | §二十五 | D/M1/M2/M3 | 2–10 | 集成测试使用 Testcontainers PostgreSQL，覆盖 Flyway 空库重放和 unique constraint。 |
| REQ-TEST-004 | §二十五 | M1 | 4/10 | 集成测试覆盖并发预约、room_gate lock 和占用互斥，确认冲突不会生成双重预约。 |
| REQ-TEST-005 | §二十五 | M2/M3/M1 | 8/10 | 集成测试通过真实 HTTP 覆盖 M2→M1、M3→M1 和 idempotency，不以生产 mock 替代。 |
| REQ-TEST-006 | §二十五 | 全服务 | 3–10 | API 测试覆盖 401、403、正常请求、409、非法参数和角色隔离。 |
| REQ-TEST-007 | §二十五 | Web/M1/D | 9/10 | Playwright E2E 覆盖学生登录 → 搜索 → 预约，以及签到。 |
| REQ-TEST-008 | §二十五 | Web/M2/M1 | 9/10 | Playwright E2E 覆盖整教室申请 → 审批 → M1 座位不可约。 |
| REQ-TEST-009 | §二十五 | Web/M3/M1 | 9/10 | Playwright E2E 覆盖报修 → 开始维修 → M1 下架，以及验收 → 座位恢复。 |
| REQ-TEST-010 | §二十六 | 全局 | 10 | 至少一次 mutation-style 人工验证：临时破坏业务规则，确认测试失败，恢复代码并重新验证。 |
| REQ-TEST-011 | §二十六 | 全局 | 10 | 在 docs/review/test-effectiveness.md 记录变异内容、命令、失败证据、恢复和结果；4 小时改为 8 小时为源示例。 |
| REQ-TEST-012 | Phase 10 | 全局 | 10 | 提供 scripts/test-all.sh，整合 Testcontainers、Playwright 和跨服务测试，实际执行退出码必须为 0。 |

## 本地运行、CI/CD、发布和云部署

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-OPS-001 | §三十; Phase 1 | 运维 | 1 | 开发 Compose 包含 postgres、四业务服务、web、nginx；mailhog 可选。 |
| REQ-OPS-002 | §三十 | 运维 | 13/14 | 生产数据库不得放入应用 Compose，使用独立数据库服务。 |
| REQ-OPS-003 | §三十一; Phase 11 | CI | 11 | PR 流水线依次执行 checkout、backend lint、backend test、frontend lint、frontend unit test、integration test、build、docker build。 |
| REQ-OPS-004 | §三十一; Phase 11 | CI | 11 | 任一步骤失败立即停止；lint、test、build 失败不能合并。 |
| REQ-OPS-005 | §三十二; Phase 12 | CD | 12 | main merge 触发构建镜像、commit SHA 标签、推送 registry、部署测试服务器、运行 Flyway、compose pull/up、health、smoke test。 |
| REQ-OPS-006 | §三十二; Phase 13 | CD | 13 | Git tag（如 v0.1.0）触发生产发布，必须人工批准后执行。 |
| REQ-OPS-007 | §三十二 | 运维 | 13 | 每次发布保存 tag、commit SHA、image tag、migration version、deploy time、operator。 |
| REQ-OPS-008 | §三十三; Phase 13 | 运维 | 13 | 以 RELEASE_TAG 选择镜像版本，回滚执行旧 tag 的 compose pull 和 compose up -d。 |
| REQ-OPS-009 | §三十三 | 数据库/运维 | 2 设计; 13 验证 | schema 优先向前兼容，应用回滚不能依赖 migration downgrade。 |
| REQ-OPS-010 | §三十四; Phase 14 | 运维 | 14 | 华为云采用 Internet→HTTPS→EIP/可选 ELB→Nginx→ECS→Compose 四服务→RDS PostgreSQL。 |
| REQ-OPS-011 | §三十四; Phase 14 | 运维 | 14 | 镜像由 GitHub Actions 构建推送 Huawei SWR，再由 ECS 拉取运行。 |
| REQ-OPS-012 | §三十五 | 运维 | 14 | 创建 deploy/huawei/README.md、bootstrap-ecs.sh、deploy.sh、rollback.sh、healthcheck.sh、docker-compose.prod.yml、nginx.conf、env.example。 |
| REQ-OPS-013 | §三十五 | 运维 | 14 | bootstrap-ecs.sh 安装 Docker、Compose plugin，创建目录、设置运行用户、启用 Docker，不硬编码凭据。 |
| REQ-OPS-014 | Phase 14 | 运维 | 14 | 验收公网只开放 HTTPS、DB 不开放公网、SWR 可拉取、CI/CD 可部署、health 正常、版本可追踪。 |
| REQ-OPS-015 | §三十六 | 运维 | 14 后可选 | ECS+Compose 稳定后才允许 deploy/helm；Compose service 映射 Kubernetes Deployment+Service。 |
| REQ-OPS-016 | §三十六 | 运维 | 14 后可选 | 可选 CCE 演进保持 image、env、health endpoint、database 不变。 |

## 可选自然语言业务 Agent

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-AGENT-001 | §三十七; Phase 15 | Optional | 15 可选 | Agent 只能最后实现，前面全部阶段完成后才可开始；不属于 Phase 0 交付。 |
| REQ-AGENT-002 | §三十七 | Optional | 15 可选 | Agent 不得直接访问数据库，只能调用公开 API，沿用真实后端鉴权。 |
| REQ-AGENT-003 | §三十七 | Optional/M1 | 15 可选 | 支持公开工具 searchSeats、createReservation、listReservationHistory。 |
| REQ-AGENT-004 | §三十七 | Optional/M2 | 15 可选 | 支持 createClassroomApplication、getClassroomApplication、cancelClassroomApplication。 |
| REQ-AGENT-005 | §三十七 | Optional/M3 | 15 可选 | 支持 createRepairTicket、getRepairTicket、cancelRepairTicket。 |
| REQ-AGENT-006 | §三十七; Phase 15 | Optional | 15 可选 | 教室申请参数不足必须追问日期、开始时间、结束时间、区域、用途，不得猜测填充。 |
| REQ-AGENT-007 | §三十七; Phase 15 | Optional | 15 可选 | LLM 仅完成自然语言→intent→tool call→template/structured result，权限、审批、预约结果、座位和工单状态均由业务 API 决定。 |

## 工程伦理记录

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-ETH-001 | §三十八 | 治理 | 3 起持续 | 持续维护 docs/ethics/ETHICS_LOG.md，记录实际问题与决策，不在最后补写空泛文章。 |
| REQ-ETH-002 | §三十八 | M1/D | 3–5 起持续 | 记录并决定违约记录保存周期，实施前明确保留、删除和可见范围。 |
| REQ-ETH-003 | §三十八 | D/全服务 | 3 起持续 | 记录管理员权限边界及个人信息最小化问题。 |
| REQ-ETH-004 | §三十八 | 全服务 | 1 起持续 | 记录日志数据相关风险及处置，遵守敏感字段禁止记录要求。 |
| REQ-ETH-005 | §三十八 | Optional | 15 可选 | 记录 Agent 越权风险与基于 API 权限的约束措施。 |
| REQ-ETH-006 | §三十八 | M1/M2 | 5/6 起持续 | 记录自动撤销风险，包括时间边界、错误处置和受影响用户的处理。 |
| REQ-ETH-007 | §三十八 | M3/M1 | 7/8 起持续 | 记录维修下架误伤风险及对既有预约的处理决策。 |

## 后续阶段范围与验收门禁

| ID | 来源 | 模块 | 计划阶段 | 可验收要求 |
|---|---|---|---|---|
| REQ-PH-001 | Phase 1 | 全局 | 1 | 仅获下一阶段确认后创建 4 Spring Boot services、1 Vue app、Dockerfile、Compose、Nginx；每服务有 health/info，docker compose up -d 后全部 healthy。 |
| REQ-PH-002 | Phase 2 | 数据库 | 2 | 建四 schema、独立账号、Flyway、最小 seed；开发环境 docker compose down -v 后 up -d，全新数据库自动 migration。 |
| REQ-PH-003 | Phase 3 | D | 3 | 实现 login、RBAC、user、role、permission、building、room、seat、settings、checkin code、audit；管理员能维护空间，学生无管理权限，后端 RBAC 生效。 |
| REQ-PH-004 | Phase 4 | M1 | 4 | 实现房间/座位/可用性查询、创建、取消、历史、再预约、room_gate、seat_occupancy；验收不能重复预约、默认 4 小时、插座过滤、历史再预约。 |
| REQ-PH-005 | Phase 5 | M1/D | 5 | 实现签到、T−15/T+10 提醒、T+15 NO_SHOW、违约和占用释放，使用可注入 Clock。 |
| REQ-PH-006 | Phase 6 | M2 | 6 | 实现 submit、approval、approve、reject、cancel、timeout；单元测试可 mock M1，生产必须真实 HTTP client。 |
| REQ-PH-007 | Phase 7 | M3 | 7 | 实现 report、assign、start、finish、accept、cancel、review、escalate，并满足 §十四 rejectAcceptance 要求。 |
| REQ-PH-008 | Phase 8 | M2/M3/M1 | 8 | 连接 M2→M1、M3→M1，删除生产 mock；以真实审批占用及维修下架/验收恢复 E2E 验收。 |
| REQ-PH-009 | Phase 9 | Web | 9 | 完成 student、approval、maintenance、admin 前端流程，主要业务无需 Postman。 |
| REQ-PH-010 | Phase 10 | 测试 | 10 | 建 Testcontainers、Playwright、cross-service integration、scripts/test-all.sh；实际执行脚本退出 0。 |
| REQ-PH-011 | Phase 11 | CI | 11 | 创建 .github/workflows/ci.yml；lint/test/build 失败均不能合并。 |
| REQ-PH-012 | Phase 12 | CD | 12 | 创建 deploy-test.yml；main merge→build→push→deploy→migration→health→smoke test。 |
| REQ-PH-013 | Phase 13 | 运维 | 13 | 创建 deploy.sh、rollback.sh、RELEASE.md；验收 v0.1.0→v0.2.0→rollback v0.1.0。 |
| REQ-PH-014 | Phase 14 | 运维 | 14 | 部署 SWR、ECS、RDS PostgreSQL、Nginx、HTTPS，并完成公网、私网、镜像、CI/CD、健康和版本六项验收。 |
| REQ-PH-015 | Phase 15 | Optional | 15 可选 | 前面全部完成后，可实现自然语言→intent→API tool→structured result；模糊需求追问、真实 API、无数据库直连。 |

## 附录 A：技术栈、服务职责与目标目录

技术栈以 REQ-ARCH-002..004 为准，列出的工具全部保留；精确 patch 版本、依赖锁定和工具配置由后续工程阶段处理，Phase 0 不生成工程文件。

| 模块 | 服务 | 完整职责 |
|---|---|---|
| D | platform-service | 用户、登录、RBAC、角色、权限、楼宇、教室、自习室、座位主数据、系统参数、签到码、通知接口、操作审计、health、version |
| M1 | reservation-service | 自习室查询、座位查询、可用时间查询、座位预约、预约取消、签到、提醒、违约、预约历史、重新预约、座位占用账本 |
| M2 | classroom-service | 整教室申请、多级审批、批准、驳回、申请撤销、审批超时撤销、审批状态查询、调用 M1 占用/释放教室 |
| M3 | maintenance-service | 故障上报、派单、开始维修、维修中、超时升级、完工、验收、评价、取消、调用 M1 下架/恢复教室 |

以下为原文目标结构，**不是 Phase 0 文件交付清单**。Phase 0 仅创建治理文档及基础空目录；特别是根构建清单、应用文件、脚本、工作流、Compose 和 `.env.example` 不在本次生成。

```text
campus-resource-platform/
├── AGENTS.md
├── README.md
├── pom.xml
├── package.json
├── pnpm-workspace.yaml
├── docs/
│   ├── requirements/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── plans/
│   ├── review/
│   ├── ethics/
│   └── runbooks/
├── contracts/openapi/
├── services/
│   ├── platform-service/
│   ├── reservation-service/
│   ├── classroom-service/
│   └── maintenance-service/
├── apps/web/
├── deploy/
│   ├── compose/
│   ├── nginx/
│   └── huawei/
├── scripts/
├── .github/workflows/
└── .env.example
```

## 附录 B：数据、状态与时间规则原值

| 所有者 | 原文明确的结构 | 要求 |
|---|---|---|
| D / platform | user、role、permission、user_role、role_permission | RBAC 表；业务名称保留，物理 SQL 引用方式待实现设计 |
| D / platform | room_checkin_code | room_id、date、code、created_at；每日每房间一个码，6–8 位随机字符，DB 唯一约束，可生成 QR Code |
| M1 / reservation | room_gate | room_id PRIMARY KEY，锁粒度 room |
| M1 / reservation | seat_reservation、seat_occupancy、checkin、violation、reservation_event | 不使用单个 seat.status 承担全部状态 |
| M2 / classroom | classroom_request、approval_step、approval_action、classroom_event | 固定两级审批 |
| M3 / maintenance | 工单和事件实体 | 原文未指定物理表名，不能把设计候选误报为源要求；明确包含 due_at、escalation event |

`seat_occupancy` 必须保留以下全部字段；原文没有给出其他字段的 SQL 类型/空值限制，后续设计需明确：

| 字段 | 原文约束/意义 |
|---|---|
| id | 占用记录标识 |
| room_id | 房间标识 |
| seat_id | nullable；房间级占用可无单座位标识 |
| start_at | 占用开始 |
| end_at | nullable；维修示例为 null |
| source_module | M1 / M2 / M3 |
| source_type | RESERVATION / CLASSROOM_BOOKING / MAINTENANCE |
| source_ref_id | 来源业务标识 |
| status | 占用状态；原文未定义具体枚举 |
| created_at | 创建时间 |
| released_at | 释放时间 |

唯一约束：`(source_module, source_ref_id, source_type)`。M1 的预约、M2 的教室占用、M3 的维修占用均通过同一房间锁协调；来源三元组和释放 API 的差异见 OQ-07。

| 模块 | 原始状态集合 |
|---|---|
| M1 | CREATED、CANCELLED、CHECKED_IN、NO_SHOW、COMPLETED |
| M2 | SUBMITTED、APPROVING、APPROVED_PENDING_SYNC、APPROVED、REJECTED、CANCELLED、AUTO_CANCELLED、SYNC_FAILED |
| M3 | REPORTED、ASSIGNED、BLOCKING_ROOM、IN_PROGRESS、WAITING_ACCEPTANCE、COMPLETED、CANCELLED、SYNC_FAILED |

明确的同步路径只有：

```text
M2: APPROVING → APPROVED_PENDING_SYNC → 调 M1 → APPROVED
失败: APPROVED_PENDING_SYNC → retry → SYNC_FAILED
M3: ASSIGNED → BLOCKING_ROOM → 调 M1 → IN_PROGRESS
重试: 网络错误或 5xx；不重试 4xx；最多 3 次；超出 → SYNC_FAILED
```

M3 操作完整清单为 `report / assign / start / finish / accept / rejectAcceptance / cancel / review`，并有定时 `escalate`。Phase 7 的简写清单没有 `rejectAcceptance`，但 §十四 明确要求，不能遗漏。

| 时间规则 | 原始值 |
|---|---|
| 预约单位 | 整点小时 |
| 最大连续时长 | 默认 4 小时，可通过系统参数配置 |
| 提醒 1 | T−15min |
| 提醒 2 | T+10min |
| NO_SHOW | T+15min（未签到），结束预约有效性、释放 occupancy、新增 violation、新增 audit/event |
| 时间可测试性 | 注入 Clock |
| 签到码周期 | 每天每 room 一个 |
| 审批超时 | 存在自动撤销，具体阈值未给出 |
| 维修超时 | due_at + 定时扫描 + escalation event + UI 标记 |

## 附录 C：身份、权限与最小 seed

原文角色完整集合：`STUDENT`、`ROOM_ADMIN`、`DEPARTMENT_ADMIN`、`PROPERTY_STAFF`、`PROPERTY_MANAGER`、`OPERATOR`、`SYSTEM_ADMIN`。

原文权限**示例**完整集合：`reservation:create`、`reservation:view:self`、`classroom:apply`、`classroom:approve`、`maintenance:report`、`maintenance:assign`、`space:manage`、`user:manage`、`audit:view`。这不是穷尽权限清单，具体 API→权限与角色→权限映射在实现前确认，不能仅按前端路由推导。

审批链：普通学生 → `ROOM_ADMIN` → `DEPARTMENT_ADMIN`。最小 seed：1 system admin、2 students、1 room admin、1 property staff、2 buildings、4 rooms、40 seats。原文没有为每个角色要求 seed 用户；不得擅自为了流程演示增加 AI 填充数据，审批演示所需账号问题见 OQ-11。

## 附录 D：原始 HTTP 契约

创建房间占用：`POST /internal/v1/room-blocks`。

M2 原始示例（示例数据并非真实业务数据）：

```json
{
  "roomId": 1001,
  "startAt": "2026-11-10T14:00:00+08:00",
  "endAt": "2026-11-10T17:00:00+08:00",
  "sourceModule": "M2",
  "sourceType": "CLASSROOM_BOOKING",
  "sourceRefId": "CB-20261110-001"
}
```

M3 原始示例：

```json
{
  "roomId": 1001,
  "startAt": "2026-11-10T14:00:00+08:00",
  "endAt": null,
  "sourceModule": "M3",
  "sourceType": "MAINTENANCE",
  "sourceRefId": "WO-20261110-008"
}
```

释放：`DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId}`。重复请求：内容一致返回已有结果；内容冲突 `409 Conflict`。原文未明确释放响应体、重复释放的状态码、已释放来源重建语义，不在 Phase 0 擅自规定。

外部路径前缀 `/api/v1`，内部路径前缀 `/internal/v1`。统一成功/失败示例：

```json
{"data": {}, "requestId": "..."}
```

```json
{"code": "RESERVATION_TIME_CONFLICT", "message": "所选时段已不可预约", "requestId": "..."}
```

HTTP 状态集合：`200 / 201 / 204 / 400 / 401 / 403 / 404 / 409 / 422 / 500 / 503`，不能全部返回 200。每服务提供 `GET /actuator/health`、`GET /actuator/info`；info 含 `service`、`version`、`gitCommit`、`buildTime`。

源 §二十三 的 `GET /api/v1/availability` 和 `ReservationAvailabilityIT` 是追踪格式示例，不自动成为已批准、已实现的真实契约或测试。本矩阵未把该示例标成 DONE。

## 附录 E：完整页面路由

| 用户区域 | 必须保留的路径 |
|---|---|
| Student | /login |
| Student | /student/home |
| Student | /student/rooms |
| Student | /student/rooms/:id |
| Student | /student/reservations |
| Student | /student/reservations/:id |
| Student | /student/history |
| Student | /student/classroom-applications |
| Student | /student/repairs |
| Approval | /approvals |
| Approval | /approvals/:id |
| Maintenance | /maintenance |
| Maintenance | /maintenance/:id |
| Admin | /admin/spaces |
| Admin | /admin/rooms |
| Admin | /admin/seats |
| Admin | /admin/users |
| Admin | /admin/roles |
| Admin | /admin/settings |
| Admin | /admin/audit |

路由并不替代 API 权限；Student、Approval、Maintenance、Admin 是页面分组，不增加原文没有的 RBAC 角色。

## 附录 F：阶段输出与建议提交信息

下列为源文档的阶段顺序与建议 commit，全部仅规划；进入任何下一阶段仍须人工确认。Phase 0 结束输出必须包含 `Phase 0 Result`、`Files Created`、`Files Modified`、`Requirement IDs`、`Architecture Decisions`、`Validation Performed`、`Remaining Issues`、`Recommended Commit`。

| Phase | 建议 commit message |
|---|---|
| 0 | chore: bootstrap project governance and requirements |
| 1 | feat: scaffold platform services and web application |
| 2 | feat: add versioned database schemas and seed data |
| 3 | feat: implement platform identity and resource management |
| 4 | feat: implement seat reservation workflow |
| 5 | feat: add reservation check-in and no-show handling |
| 6 | feat: implement classroom booking approval workflow |
| 7 | feat: implement maintenance work order workflow |
| 8 | feat: integrate classroom and maintenance state with reservations |
| 9 | feat: complete role-based web workflows |
| 10 | test: add cross-module integration and end-to-end coverage |
| 11 | ci: add automated quality gates |
| 12 | ci: add automated test environment deployment |
| 13 | ops: add production release and rollback workflow |
| 14 | ops: add Huawei Cloud deployment configuration |
| 15 | feat: add natural-language business agent plugin |

DoD 原始条件全部保留：Requirement ID linked；Real business logic implemented；No production mock；Migration added if needed；Tests passed；API docs updated；RBAC verified；No sensitive logs；Docker works；CI passes；TRACEABILITY updated；Manual acceptance reproducible；Git history traceable。

Phase 0 的业务、Docker、CI 条件因阶段范围不适用，不能伪造通过；本阶段可在文档验收后仅将 REQ-P0 项标记 DONE，并说明阶段适用性，其他实现需求仍 TODO。

## 未决问题（不阻塞 Phase 0 文档；相关实现前必须确认）

| ID | 歧义或缺失 | 关联需求 | 最迟处理阶段 |
|---|---|---|---|
| OQ-01 | M1 写明 NO_SHOW 状态，同时要求“取消预约”；需确定最终状态、取消时间窗口、签到窗口、COMPLETED 触发、NO_SHOW 后补签到政策与状态转换守卫。Phase 0 不把 NO_SHOW 覆盖成 CANCELLED。 | REQ-M1-006、009、011、013 | 4/5 |
| OQ-02 | 时间示例有 +08:00，但未统一业务时区、跨日整点、区间开闭边界、未来可预约窗口、已有预约遇最大时长参数变化的处理；不能直接宣称采用某种边界算法。 | REQ-M1-003、004、017 | 4 |
| OQ-03 | 提醒渠道（站内/邮件等）、T+10 对已签到者是否发送、调度频率、停机补发、去重及送达失败规则未指定。mailhog 仅可选不代表邮件已确定。 | REQ-D-006、REQ-M1-010 | 5 |
| OQ-04 | 签到码 code 本身是否全局唯一、唯一键是否 (room_id,date)、码有效时区/窗口、重新生成、二维码载荷和接口责任细节待明确；原文确定的是每天每房间一个码。 | REQ-DATA-005、REQ-D-005、REQ-M1-013 | 2/3/5 |
| OQ-05 | M2 审批超时阈值、每级审批责任范围、撤销允许状态、已批准申请撤销、正常到期释放及释放失败后显示状态均未定义。 | REQ-M2-002、004、008 | 6 |
| OQ-06 | M3 rejectAcceptance 返回哪个状态、验收权限、评价规则、可取消范围、due_at 计算、升级接收方/重复升级与释放失败恢复未定义。 | REQ-M3-004..011 | 7 |
| OQ-07 | 唯一键包含 source_type，DELETE 路径却没有它；需确认来源模块与类型是否固定一一映射、source_ref_id 唯一范围、重复释放和释放后重建语义。幂等 payload 的“相同”比较方式也未定义。 | REQ-DATA-004、REQ-API-005、006 | 2 契约设计; 8 实现前 |
| OQ-08 | “最多 3 次”指重试 3 次还是总尝试 3 次；退避、状态持久化、进程重启/并发 worker、4xx 的终止状态、SYNC_FAILED 人工恢复、释放是否用同样重试均需确认。 | REQ-ARCH-011、REQ-API-007、REQ-M2-007、REQ-M3-011 | 6–8 |
| OQ-09 | M2/M3 房间占用遇现有 M1 预约时的拒绝、取消、通知及补偿政策未给出；禁止擅自清空既有预约或用维修优先级绕开约束。 | REQ-M1-004、015..017、REQ-ETH-007 | 6–8 |
| OQ-10 | 公共主数据查询、参数、签到码验证、审计、通知的 HTTP 契约以及 JWT 信任/密钥机制、内部服务身份验证、组织/房间授权范围待设计与人工 review。 | REQ-ARCH-006、REQ-D-001..007、REQ-SEC-002..004、011 | 3/8 |
| OQ-11 | 固定最小 seed 无 DEPARTMENT_ADMIN 或 PROPERTY_MANAGER 用户，完整审批/派单演示需通过授权管理创建或明确角色分配；不得擅自扩充 seed 数量。 | REQ-DATA-011、REQ-M2-002、REQ-SEC-004 | 2/3/6 |
| OQ-12 | 违约、预约、工单、审计和日志生命周期，个人信息字段、导出/删除范围、自动撤销与维修误伤纠正政策未定，需持续伦理记录。 | REQ-ETH-001..007、REQ-SEC-012 | 3 起各相关实现前 |
| OQ-13 | seat_occupancy.status 枚举、其他字段 SQL 类型/可空性、M3 物理表名、room_gate 初始化/缺行策略、迁移执行账号与应用账号授权细节待 Phase 2 数据设计。 | REQ-DATA-001..008、REQ-ARCH-008 | 2 |
| OQ-14 | 统一成功 envelope 与 HTTP 204 无响应体如何兼容，错误码清单、分页/过滤及 DELETE 重复响应需在 OpenAPI 中明确。 | REQ-API-002、003、005 | 3/8 |
| OQ-15 | 云地域、域名、证书、SWR 命名、测试服务器和生产审批操作人、secret 注入/轮换、备份和保留策略未指定，待对应运维阶段确定。 | REQ-OPS-005..014、REQ-SEC-005、008..011 | 12–14 |

## 来源覆盖索引

| 源章节 | 主要需求分类 |
|---|---|
| 角色、一、二 | GOV、ARCH、ENG、SEC、TEST |
| 三、四 | ARCH、P0 |
| 五、六 | D、M1、M2、M3、ARCH、DATA |
| 七、八 | DATA、M1 |
| 九、十 | API、M1、M2、M3、ARCH |
| 十一、十二 | M1、D、DATA |
| 十三、十四 | M2、M3、DATA |
| 十五、十六 | SEC、D、DATA |
| 十七、十八 | API、ENG |
| 十九 | DATA、ENG |
| 二十、二十一 | UI |
| 二十二、二十三 | GOV、P0 |
| 二十四、二十五、二十六 | DATA、TEST |
| 二十七、二十八、二十九 | ENG、SEC、D |
| 三十、三十一、三十二、三十三 | OPS |
| 三十四、三十五、三十六 | OPS、ARCH |
| 三十七、三十八 | AGENT、ETH |
| 三十九、四十、四十一 | GOV、P0、PH |
| Phase 0 | P0 |
| Phase 1–15 | PH 及对应功能/工程分类 |
