# 校园资源服务平台架构基线

状态：Phase 0 建立的目标架构基线，当前补充 Phase 3 D 公共层，仍待人工 Review。Phase 1/2 的骨架和隔离数据库保留；Phase 3 已实现 32 个 D 操作、V2/V3、身份权限/空间/参数/日码/审计，并通过后端 49 项、前端 6 项及原卷升级/七容器/实际 API 验收。本文保留完整目标架构，未将后续业务标为完成；证据见 [Phase 3 记录](../plans/phase-3.md)。完成后等待 Phase 4 确认。

依据：[原始执行要求](../requirements/CODEX_EXECUTION_PROMPT.md)第 1–40 节及 Phase 0–15。完整需求和状态以 [REQUIREMENTS.md](../requirements/REQUIREMENTS.md) 与 [TRACEABILITY.md](../requirements/TRACEABILITY.md) 为准；数据归属见 [DATA_OWNERSHIP.md](DATA_OWNERSHIP.md)，边界决策见 [ADR-001](../adr/ADR-001-service-boundary.md)。

## 1. 架构范围与原则

采用 Monorepo、前后端分离、4 个后端服务、REST API 和 PostgreSQL。核心业务结果由确定性的业务规则、权限检查、状态机和数据库约束产生。优先保证真实运行、可测试、可追踪与简单维护。

Phase 0 为文档基线，Phase 1 已交付四服务、Vue 状态页和容器，Phase 2 建立四 schema/八账号/V1/最小 seed。Phase 3 交付 D 登录/JWT/RBAC、用户/角色/权限、空间主数据、参数、每日房间码和审计；D 提供四个私网主数据/参数/验码操作。通知投递、实际 M1 预约/签到、审批、工单、调用方业务集成、完整 Web 和 CI/CD 仍按后续阶段实施。

禁止为课程项目引入 Kafka、Nacos、Consul、Service Mesh、Event Sourcing、CQRS 等非必要基础设施。第一版不使用 Redis，也不使用 Redis 锁、分布式事务或 BPMN 引擎。不得用生产 mock、假接口、假数据或默认成功掩盖缺失能力。

## 2. 服务职责与 HTTP 依赖

| 模块 | 服务 | 责任边界 |
|---|---|---|
| D 公共层 | `platform-service` | 用户、登录、RBAC、角色、权限、楼宇、教室、自习室、座位主数据、系统参数、签到码、通知接口、操作审计，以及本服务健康与版本信息 |
| M1 自习座位预约 | `reservation-service` | 自习室/座位/可用时间查询、创建与取消预约、签到、提醒、违约、历史和再次预约；统一拥有座位占用账本和房间并发锁；为 M2/M3 创建与释放房间占用 |
| M2 整教室预约与审批 | `classroom-service` | 整教室申请、多级审批、批准、驳回、撤销、审批超时撤销、审批状态和占用同步状态；通过 HTTP 请求 M1 占用或释放教室 |
| M3 物业报修工单 | `maintenance-service` | 故障上报、派单、开始维修、维修中、超时升级、完工、验收、评价、取消和占用同步状态；通过 HTTP 请求 M1 下架或恢复教室 |

```mermaid
flowchart LR
    Web[Vue Web] -->|公开 REST API| D[D / platform-service]
    Web -->|公开 REST API| M1[M1 / reservation-service]
    Web -->|公开 REST API| M2[M2 / classroom-service]
    Web -->|公开 REST API| M3[M3 / maintenance-service]
    M1 -->|HTTP：所需公共能力| D
    M2 -->|HTTP：所需公共能力| D
    M3 -->|HTTP：所需公共能力| D
    M2 -->|HTTP：创建 / 释放房间占用| M1
    M3 -->|HTTP：创建 / 释放维修占用| M1
```

图中箭头表示调用依赖，不表示共享数据库权限。D 是身份、空间主数据和公共能力的所有者；M1/M2/M3 访问这些服务数据必须通过 HTTP，具体公开契约须在相应阶段设计并人工 Review。Phase 3 的 D JWT 使用 HS256/issuer/audience/到期/authVersion 和实时 DB 权限；三个内部调用方使用独立 opaque 凭据，只有 M1 可验码。该方案待人工 review，后续调用方接入与组织范围尚须相应业务验证；图不要求每次请求都远程查询 D。

M1 的房间、座位查询可以结合 D 主数据与自身占用数据，但不能直接读取 `platform` 表。M2/M3 对教室不可用的影响只能经由 M1 占用 API；D 的空间主数据管理不能直接修改 M1 账本。未规定 M2 与 M3 直接相互调用。

D 的操作审计和通知接口是公共能力；各业务模块的业务事件仍归相应模块。审计传递和通知失败如何处理、是否要求调用成功后才完成某个业务动作，均待契约 Review，不引入消息队列作为默认实现。

## 3. 固定技术栈

| 层次 | 技术 |
|---|---|
| 后端运行与框架 | Java 21、Spring Boot 3.x、Spring Security、Maven |
| 后端数据与接口 | MyBatis、PostgreSQL、Flyway、Bean Validation、OpenAPI 3 |
| 后端验证 | JUnit 5、Testcontainers、JaCoCo |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Axios |
| 前端验证 | Vitest、Playwright |
| 容器与交付 | Docker、Docker Compose、Nginx、GitHub Actions |
| 华为云目标 | SWR、ECS、RDS PostgreSQL |
| 后续可选 | ELB、CCE、DCS Redis；第一版不使用 Redis，CCE 仅在 ECS + Compose 稳定后考虑 |

本表是完整目标选型；实际依赖版本以根 POM、各模块构建文件和 `pnpm-lock.yaml` 为准。前后端构建配置已在 Phase 1 建立，MyBatis、PostgreSQL 驱动、Flyway 与 Testcontainers 在 Phase 2 接入；D 的静态 OpenAPI 已与 32 个实际操作一致并随 Phase 3 交付；其他业务契约、业务 E2E、CI/CD 和云部署仍待后续阶段。

## 4. 数据所有权与并发边界

一个 PostgreSQL 实例容纳 `platform`、`reservation`、`classroom`、`maintenance` 四个 schema。Phase 2 为每服务分别建立 `<schema>_app` 与 `<schema>_migrator`：前者无 DDL 或 Flyway 历史表访问权限，后者只拥有本 schema，均不得访问其他服务 schema。D 在原 V1 八表基础上追加 V2/V3，当前共 12 张基础业务表，并撤销 app 对审计的 UPDATE/DELETE；M1/M2/M3 仍只有 V1 迁移/权限基线，没有业务表。具体当前表与后续模型见 [DATA_OWNERSHIP.md](DATA_OWNERSHIP.md)。禁止跨 schema SQL、跨服务读写表、跨服务外键和借共享数据库绕过 HTTP。业务外键引用其他服务实体时，仅保存逻辑 ID，由所属服务 API 提供校验与数据。

D 拥有空间主数据；M1 拥有 `room_gate`、`seat_reservation`、`seat_occupancy`、`checkin`、`violation`、`reservation_event`。房间和座位“存在什么”与某时段“是否可用”是不同责任：不可用来源必须由 M1 账本表示，不能全部压缩为 `seat.status`。

所有改变房间座位状态的操作使用 M1 自有的 `room_gate(room_id PRIMARY KEY)`，在一个数据库事务中锁定对应房间行，检查占用并创建或释放占用后提交。覆盖 M1 创建/取消预约、M2 创建/释放教室占用和 M3 创建/释放维修占用。锁粒度为房间。D 不持有此锁，也不跨服务参与该本地事务。

`room_gate` 的初始化、D 创建/停用/删除房间时的协调和历史数据保留规则尚待明确。不能因为锁行尚不存在而跳过互斥控制。Phase 2 不提前创建这些业务表；M1 预约/占用模型与并发行为在 Phase 4、签到和违约模型在 Phase 5 随规则实现并验证。

## 5. 占用契约与跨服务一致性

M2/M3 调用 M1 的源指定契约如下；本文保留基线路径，并不声称已提供可调用接口：

| 操作 | 契约 |
|---|---|
| 创建房间占用 | `POST /internal/v1/room-blocks` |
| 释放房间占用 | `DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId}` |
| POST 字段 | `roomId`、`startAt`、`endAt`、`sourceModule`、`sourceType`、`sourceRefId` |
| 幂等唯一键 | `(source_module, source_ref_id, source_type)` |
| 重复创建 | 内容一致返回已有结果；内容冲突返回 `409 Conflict` |

M2 的 `sourceModule=M2`、`sourceType=CLASSROOM_BOOKING`；M3 的 `sourceModule=M3`、`sourceType=MAINTENANCE`，维修允许 `endAt=null`，表示没有指定结束时间，不能自行代入默认维修时长。

DELETE 路径缺少唯一键中的 `sourceType`。应在公共契约人工 Review 时明确是否限制 `sourceModule` 到唯一类型，或调整释放契约；本基线不擅自增加参数、扩大删除范围或认定不存在歧义。重复释放、已释放记录的再创建、冲突内容比较口径与响应内容也需确认。

不使用分布式事务。M2 批准和 M3 开始维修必须显式表示本地业务进度与 M1 同步进度：

```text
M2：APPROVING -> APPROVED_PENDING_SYNC -> 调用 M1 -> APPROVED
    同步失败：APPROVED_PENDING_SYNC -> 有界重试 -> SYNC_FAILED

M3：ASSIGNED -> BLOCKING_ROOM -> 调用 M1 -> IN_PROGRESS
    同步失败：有界重试耗尽 -> SYNC_FAILED
```

只重试网络错误和 HTTP 5xx；HTTP 4xx 不重试；源要求上限为“最多 3 次”。这 3 次是包含首次调用的总尝试次数，还是首次失败后的重试次数，源文档未明确，必须确认后再实现，不能隐含选择。4xx 的业务错误呈现和最终状态处理、重试间隔、执行载体、人工恢复入口与释放操作的同步状态也待设计。

网络超时可能发生在 M1 已提交但调用方未收到响应之后，因此调用方不能把超时直接等同于“没有占用”，也不能在尚未确认同步成功时报告 `APPROVED` 或 `IN_PROGRESS`。后续实现需依托约定的幂等键处理重复请求，并验证进程重启、重试耗尽和同步失败的真实行为。

## 6. 时间规则与业务状态

| 模块 | 源指定状态 |
|---|---|
| M1 预约 | `CREATED`、`CANCELLED`、`CHECKED_IN`、`NO_SHOW`、`COMPLETED` |
| M2 申请 | `SUBMITTED`、`APPROVING`、`APPROVED_PENDING_SYNC`、`APPROVED`、`REJECTED`、`CANCELLED`、`AUTO_CANCELLED`、`SYNC_FAILED` |
| M3 工单 | `REPORTED`、`ASSIGNED`、`BLOCKING_ROOM`、`IN_PROGRESS`、`WAITING_ACCEPTANCE`、`COMPLETED`、`CANCELLED`、`SYNC_FAILED` |

M1 预约按整点小时，默认连续最多 4 小时，最大时长由系统参数管理。创建前必须检查 M1/M2/M3 全部有效占用，冲突返回 409。支持插座筛选、历史查询和再次预约历史座位；再次预约仍需按当前条件验证，不能绕过占用规则。

提醒时间为预约开始前 15 分钟、开始后 10 分钟；开始后 15 分钟未签到触发 NO_SHOW，并取消预约、释放占用、新增违约及审计/事件。源同时列出 `NO_SHOW` 状态和“取消预约”动作，最终状态表达及相应转移待确认，不能自行用 `CANCELLED` 吞掉违约语义。时间逻辑使用可注入 `Clock`。

D 在 Phase 3 的具体方案为每日每房间一个 8 位随机字符签到码，Clock/显式 ZoneId 决定今日、room/date 主键保证唯一、同日 ensure 不轮换；二维码载荷尚未实现。M1 后续须校验房间与预约并记录实际签到结果。首版不做 GPS、人脸识别、蓝牙和 NFC。

M2 第一版审批链为普通学生 → `ROOM_ADMIN` → `DEPARTMENT_ADMIN`。M3 使用 `due_at` 和定时扫描记录 escalation event 并在 UI 标记。审批超时时长、维修时限、预约取消限制、签到窗口、时区及区间端点语义未由源指定，须在相应阶段前确认，不能在基线中添加默认政策。

## 7. API、安全与可观测性

公开 API 使用 `/api/v1`，服务内部 API 使用 `/internal/v1`。成功响应包含 `data`、`requestId`；错误包含 `code`、`message`、`requestId`。使用真实 HTTP 状态：200、201、204、400、401、403、404、409、422、500、503；204 的无响应体语义应在契约中明确，不能为统一封装强行附加 JSON，也不能把所有结果包装成 HTTP 200。

第一版使用用户名/学号、密码和有过期时间的 JWT，密码采用 BCrypt；暂不接入学校统一身份认证。权限在后端验证，前端隐藏按钮仅用于交互。角色为 `STUDENT`、`ROOM_ADMIN`、`DEPARTMENT_ADMIN`、`PROPERTY_STAFF`、`PROPERTY_MANAGER`、`OPERATOR`、`SYSTEM_ADMIN`；角色的数据范围和具体授权矩阵需人工确认。

严格验证边界包括 HTTP 请求、JWT、权限、数据库约束、外部 HTTP 响应、状态机转换、环境变量；内部不重复 Bean Validation，不使用普遍 try/catch、无意义 Optional、默认成功或无限重试。

生产要求 HTTPS only、参数化 SQL、CORS 白名单、无默认生产密码、Git 无凭据、数据库仅私网、内部 API 不公开、生产 Swagger 受限。内部调用身份认证及来源模块防伪规则必须在开放内部契约前明确，不能把请求体 `sourceModule` 当作可信调用身份。

日志至少包含 `timestamp`、`level`、`service`、`requestId`、`userId`、`method`、`path`、`status`、`latencyMs`；不记录密码、JWT、refresh token、签到码明文或敏感个人信息。每个服务的 `/actuator/health` 和 `/actuator/info` 已在 Phase 1 分别实现，后者包含 `service`、`version`、`gitCommit`、`buildTime`；Phase 2 health 增加真实数据源连接检查，详情不对外暴露。D 在 Phase 3 已实现认证和模板化请求日志，实际日志抽查未出现本地私有凭据或生成码；其他服务完整业务日志及认证接入仍随对应阶段交付。

## 8. 本地与华为云目标拓扑

本地开发 Compose 已配置 `postgres`、四个后端服务、`web` 和 `nginx`，Phase 1 已完成工程骨架验收；`mailhog` 可选且当前未配置。Phase 2 数据库重放与整体验收单独记录。浏览器访问入口与服务内部通信分开，内部 API 和数据库不能通过公共入口暴露。

```mermaid
flowchart TB
    Browser[本地浏览器] --> Nginx[Nginx]
    Nginx --> Web[Web]
    Nginx --> APIs[四个后端服务的公开 API]
    APIs --> DB[单 PostgreSQL / 四 schema / 独立账号]
```

此图保留本地拓扑。当前仅 Nginx 绑定 loopback，默认 `127.0.0.1:18080`，代理 Vue、四服务只读运行信息及 D 的 `/api/v1/`；D 后端实际验证身份、权限和房间范围。数据库与后端无宿主端口，`/internal` 不经公共网关暴露；M1/M2/M3 尚未开放业务 API。具体配置见 `compose.yaml`、`deploy/nginx/nginx.conf` 及[本地手册](../runbooks/local-development.md)；后续服务间 HTTP 调用走内部网络。

```mermaid
flowchart TB
    Internet[Internet] -->|HTTPS| Edge[EIP / 可选 ELB]
    Edge --> Nginx[Nginx / ECS]
    Nginx --> Runtime[ECS 上的 Docker Compose：Web 与四个后端服务]
    Runtime -->|私网| RDS[Huawei RDS PostgreSQL]
    Actions[GitHub Actions] --> SWR[Huawei SWR]
    SWR -->|镜像拉取| Runtime
```

生产目标为 SWR + ECS + RDS PostgreSQL + Nginx + HTTPS；生产数据库不放在应用 Compose 中。华为云验收要求公网仅开放 HTTPS、数据库无公网入口、镜像可拉取、CI/CD 可部署、健康与版本可追踪。ELB 可选，CCE 只能在 ECS + Compose 稳定后加入，且保持镜像、环境变量、健康端点和数据库不变。

PR 质量门包括后端 lint/test、前端 lint/unit test、集成测试、构建与 Docker build，任一步失败即停止。main 合并后的测试环境部署需构建并以 commit SHA 标记镜像、推送、迁移、启动、健康检查和 smoke test；生产通过 Git tag 和人工批准发布，记录 tag、commit SHA、image tag、migration version、deploy time、operator。

应用回滚通过 `RELEASE_TAG` 选择旧镜像，数据库优先采用向前兼容 schema，不依赖 migration downgrade。迁移仅向前，已执行/已发布迁移不得修改，修复须新建迁移；schema 与 seed 分离，并能从空库重放。上述发布流水线、`RELEASE_TAG` 配置和回滚能力均尚未实现。

## 9. 验证与分阶段交付

单元测试覆盖状态、时间、权限、最大预约时长、取消和超时；Testcontainers PostgreSQL 覆盖真实迁移、并发、房间锁、唯一约束和幂等；API 验证 401/403/正常请求/409/非法参数/角色隔离；Playwright 验证学生预约、签到、M2 审批到 M1 不可预约、M3 维修下架到验收恢复。至少一次人工 mutation-style 验证须确认改变业务规则会导致测试失败并恢复代码，记录真实结果。

| Phase | 允许进入的工作范围 |
|---|---|
| 0（历史基线） | 治理、需求与架构文档、目录；已交付 |
| 1（已交付） | 四个 Spring Boot 服务、Vue 应用、Dockerfile/Compose/Nginx、health/info |
| 2（已交付） | 四 schema、各自应用/迁移账号、Flyway、最小 seed |
| 3（本轮完成） | D 身份权限、资源、参数、签到码与审计；完成后停止等待 Phase 4 确认 |
| 4 | M1 基础预约、房间锁与占用账本 |
| 5 | M1 签到、提醒、违约与释放 |
| 6 | M2 申请审批、撤销与超时，生产真实 HTTP client |
| 7 | M3 工单、验收、评价与升级 |
| 8 | M2→M1、M3→M1 的真实跨模块集成及相应 E2E |
| 9 | 各角色完整 Web 流程 |
| 10 | 测试体系、跨服务验证及 `scripts/test-all.sh` |
| 11 | PR CI 质量门 |
| 12 | 测试环境自动部署 |
| 13 | 生产发布与回滚 |
| 14 | 华为云部署与验收 |
| 15（可选） | 前序全部完成后，开发只通过公开 API 的自然语言 Agent |

每次只执行一个 Phase，并在结束时列出文件、设计、实际命令、测试结果、未完成项和建议 commit，再等待人工确认。测试必须随对应业务验证，Phase 10 不意味着前序阶段可以无验证交付。

可选 Agent 只能把自然语言转为意图、API 工具调用和模板结果；参数不足必须追问，不可直连数据库或决定权限、审批、预约、座位、工单状态。不得先做 AI 推荐、AI 自动审批或用 AI 生成虚构运营内容。

## 10. 人工 Review 待确认事项

这些事项是后续实现的决策输入，尚未成为默认政策：

1. DELETE 路径与三元幂等键的一致性；重复释放、已释放后重用业务引用及请求内容冲突比较规则。
2. “最多 3 次”的计数含义、重试间隔/调度/恢复、4xx 最终状态及释放失败的同步状态。
3. D 主数据变更与 M1 锁行生命周期；资源停用/删除对已存在预约与占用的影响。
4. 预约区间端点、业务时区、签到窗口、取消规则、NO_SHOW 状态表达、审批和维修超时阈值。
5. D 的 JWT、独立内部身份、SYSTEM_ADMIN 全局/ROOM_ADMIN 显式范围已有 Phase 3 待审方案；其他模块接入、部门范围、通知与跨服务审计失败处理仍未完成。
6. 违约、个人信息、审计及业务事件的保存周期；应在工程伦理记录中持续跟踪实际决定。

上述问题作为持续未决事项保留。Phase 3 对 D 所需日码/身份/范围/响应给出具体待审方案，未替 M1/M2/M3 的业务窗口、并发、释放和生命周期作决定；数据库与公共契约仍须人工 Review，不能以测试、执行授权或上传仓库推定架构已获批准。
