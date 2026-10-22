# 数据所有权与一致性边界

状态：Phase 2 数据库实现方案，待草稿 PR 人工 Review。四 schema、八个数据库账号、四服务 Flyway V1 及 D 的八张基础表已有真实 PostgreSQL/Testcontainers 验证；这不代表完整业务模型或后续阶段已完成，也不代表 schema 已获人工批准。

依据：[原始执行要求](../requirements/CODEX_EXECUTION_PROMPT.md)第 5–16、24、27、29 节。配套：[架构](ARCHITECTURE.md)、[ADR-001](../adr/ADR-001-service-boundary.md)、[需求](../requirements/REQUIREMENTS.md)、[追踪表](../requirements/TRACEABILITY.md)。

## 1. 单实例、四 schema、四个服务权限边界

| 服务 | 模块 | 唯一可访问 schema | 所有权 |
|---|---|---|---|
| `platform-service` | D | `platform` | 身份与权限、空间主数据、系统参数、签到码、公共操作审计；通知接口归 D，是否持久化通知记录待设计 |
| `reservation-service` | M1 | `reservation` | 预约、占用、房间锁、签到结果、违约及预约事件 |
| `classroom-service` | M2 | `classroom` | 整教室申请、审批步骤、审批行为、申请事件及同步状态 |
| `maintenance-service` | M3 | `maintenance` | 工单、派单/维修/验收/评价行为、超时升级事件及同步状态；源未指定具体表名 |

一个 PostgreSQL 实例只降低部署成本，不构成共享数据访问授权。每个服务使用独立数据库账号且仅访问本 schema；禁止跨 schema 查询、写入、JOIN、外键及通过共享数据库账号绕过边界。跨服务数据访问一律走 HTTP API。

Phase 2 由 [PostgreSQL 空库引导脚本](../../deploy/postgres/010-service-databases.sh) 创建以下角色。八个角色均为独立 LOGIN，且无超级用户、建库、建角色、复制、绕过行权限及角色继承能力，无角色成员授权；密码从私有环境注入，不提交 Git。

| schema | Flyway 迁移账号 / schema owner | 应用账号 |
|---|---|---|
| `platform` | `platform_migrator` | `platform_app` |
| `reservation` | `reservation_migrator` | `reservation_app` |
| `classroom` | `classroom_migrator` | `classroom_app` |
| `maintenance` | `maintenance_migrator` | `maintenance_app` |

引导脚本撤销数据库及 `public` schema 的 PUBLIC 默认权限；各角色只获得该库 CONNECT，应用只获得自身 schema USAGE。迁移账号以 owner 身份在本 schema 执行 DDL，其新表默认向本服务 app 授予 SELECT/INSERT/UPDATE/DELETE，新序列授予 USAGE/SELECT。app 无 schema CREATE、DDL 或切换 migrator 的权限。每份 V1 显式撤销 app 对自身 `flyway_schema_history` 的全部权限，包括读取和修改；该表只由迁移账号维护。各角色 search_path 固定为自身 schema 与 `pg_catalog`，各服务连接也指定自身 schema。

只有同一服务拥有的数据实体之间可以在后续模型中设计数据库外键。跨服务的用户、房间、座位及业务来源标识均为逻辑 ID，不建立跨 schema 外键。源服务负责其数据真实性，使用方负责按契约校验与处理失效引用；删除限制、快照及历史保留策略尚未确定。

## 2. D：身份与空间主数据

源指定 RBAC 表为 `user`、`role`、`permission`、`user_role`、`role_permission`。D 拥有用户密码哈希、角色权限、楼宇、房间（教室/自习室）、座位主数据和系统参数。Phase 2 的 [V1 迁移](../../services/platform-service/src/main/resources/db/migration/V1__platform_schema.sql) 只创建最小 seed 必需的以下八表，作为待人工审阅的 schema 方案：

| 当前表 | 已实现的数据库边界 |
|---|---|
| `platform."user"` | identity 主键、唯一用户名、非空 BCrypt 格式密码哈希、演示显示名称、启用标记、创建时间 |
| `platform.role` | identity 主键、唯一角色 code、名称；七类角色字典 |
| `platform.permission` | identity 主键、唯一权限 code、名称；Phase 2 不填充权限矩阵 |
| `platform.user_role` | `(user_id, role_id)` 复合主键；仅引用本 schema 的用户和角色 |
| `platform.role_permission` | `(role_id, permission_id)` 复合主键；仅引用本 schema 的角色和权限；Phase 2 无分配数据 |
| `platform.building` | identity 主键、唯一楼宇 code、名称 |
| `platform.room` | identity 主键、building 外键、楼内 code 唯一、`STUDY_ROOM/CLASSROOM` 类型约束、启用标记 |
| `platform.seat` | identity 主键、room 外键、房内 code 唯一、插座标记、启用标记；不设置业务占用状态 |

基础实体 id 由数据库 identity 生成，关联通过同 schema 外键约束；seed 不指定 id、不重置序列。登录、JWT、后端 RBAC、权限矩阵、主数据管理端点均留 Phase 3，不能以这些表存在替代相应业务验收。

D 拥有未来的 `room_checkin_code`，字段为 `room_id`、`date`、`code`、`created_at`；本阶段尚未建表，随 Phase 3 签到码契约确定后迁移。要求每天每房间一个 6–8 位随机字符签到码，并以数据库唯一约束保证要求；可生成 QR Code。业务日期的时区以及 `code` 自身的唯一范围需 Review，不能从示例推导全局唯一策略。签到码明文不得进入日志。系统参数、审计及可能的通知持久化表也留对应业务阶段设计。

M1 通过 D 的服务契约校验签到码对应房间，并结合 M1 预约完成签到；D 的“发码记录”不等于 M1 的“预约签到结果”。D 的系统参数包含最大预约时长，默认值为 4 小时；参数变更的生效口径和读取策略仍需确认。

M2/M3 对房间不可用的影响不能通过修改 D 的座位主数据代替占用记录。D 对房间/座位的维护与占用的关系，尤其是有未来预约时停用或删除资源的处理，必须在实现前明确。

## 3. M1：占用与房间并发控制的唯一所有者

Phase 2 的 `reservation` V1 只建立迁移版本/权限基线，不创建以下业务表。`room_gate`、预约和占用在 Phase 4，签到、违约及相关事件在 Phase 5 随真实规则创建；未决枚举、锁行初始化和释放语义仍须先确认。

| 源指定实体 | 责任 |
|---|---|
| `room_gate` | 每房间一条互斥控制记录，`room_id` 为主键；归 M1，不属于 D |
| `seat_reservation` | 预约业务记录和 `CREATED/CANCELLED/CHECKED_IN/NO_SHOW/COMPLETED` 状态 |
| `seat_occupancy` | 统一记录来自 M1/M2/M3 的占用、时间及释放信息 |
| `checkin` | 与预约相关的签到结果 |
| `violation` | 未按规则签到等源指定违约记录 |
| `reservation_event` | 预约事件；与 D 操作审计分别归属，不以共享表替代 |

`seat_occupancy` 至少包含以下源指定字段，本文不预先给出 SQL 类型或迁移：

| 字段 | 基线含义 |
|---|---|
| `id` | 占用记录标识 |
| `room_id` | D 房间的逻辑标识 |
| `seat_id`（可空） | D 座位的逻辑标识；与房间占用的对应模型需在数据库/契约设计中明确 |
| `start_at` | 占用开始时间 |
| `end_at`（可空） | 占用结束时间；维修可未指定结束时间 |
| `source_module` | `M1`、`M2` 或 `M3` |
| `source_type` | `RESERVATION`、`CLASSROOM_BOOKING` 或 `MAINTENANCE` |
| `source_ref_id` | 来源业务记录的逻辑标识 |
| `status` | 占用状态；源未定义其枚举，不复用预约状态作为已定设计 |
| `created_at` | 创建时间 |
| `released_at` | 释放时间 |

来源组合按业务含义分别为 M1/RESERVATION、M2/CLASSROOM_BOOKING、M3/MAINTENANCE。调用方自身的申请或工单状态不取代 M1 占用账本；M1 也不决定 M2 审批结果或 M3 验收结果。房间占用需使该房间座位在对应时间不可预约；是否用一条房间记录表达、如何处理房间下增删座位，须在具体模型中说明并验证。

源要求唯一键为 `(source_module, source_ref_id, source_type)`：相同键且内容一致返回已有结果，内容冲突返回 409。不能为了给房间每个座位复制记录而破坏此约束，也不能省略来源记录。已释放记录保留多久、原键是否可重用、内容比较包含哪些字段，均待契约 Review。

```text
开始 M1 本地数据库事务
  锁定对应 room_gate 行：SELECT ... FOR UPDATE
  检查相关 M1/M2/M3 占用
  创建或释放占用，并维护相关 M1 本地业务记录
提交事务
```

以上规则适用于 M1 创建/取消预约及 M2/M3 经 HTTP 触发的占用创建/释放。按房间串行化写操作，不使用 Redis 锁。主数据来自 D 并不改变锁的所有权；锁行创建时机、主数据确认和本地事务的先后关系需后续设计，不能在锁行缺失时绕过互斥。

## 4. M2 与 M3：业务状态与占用状态分开管理

Phase 2 的 `classroom`、`maintenance` V1 只建立迁移版本/权限基线，无审批、申请或工单表。M2 表随 Phase 6，M3 表随 Phase 7 创建；不以占位业务表固定未定状态与字段。

M2 拥有 `classroom_request`、`approval_step`、`approval_action`、`classroom_event`，包含审批链、审批决定和自身同步状态。批准后先进入 `APPROVED_PENDING_SYNC`，真实调用 M1 成功后进入 `APPROVED`；失败经规定的有界重试进入 `SYNC_FAILED`，不得将未确认同步伪装成已完成批准。

M3 拥有工单与派单、维修、验收、评价、取消、超时升级相关事实。源未要求具体表名，因此本阶段不发明 DDL。开始维修经历 `ASSIGNED -> BLOCKING_ROOM -> 调用 M1 -> IN_PROGRESS`；维修验收到恢复座位需要经 M1 释放占用。释放失败状态、恢复与再次维修的引用复用规则需人工 Review。

M2/M3 只能通过 `POST /internal/v1/room-blocks` 和 `DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId}` 请求 M1 改变占用。DELETE 只传两项而唯一键有三项，这是待解决的契约歧义，不是跨表查询或宽泛删除的理由。

跨服务不使用分布式事务。网络错误和 5xx 可以有界重试，4xx 不重试，源要求上限“最多 3 次”，其是否包含首次调用尚待确认。网络超时后远端可能已提交；重复调用仍须遵守幂等规则，并保留可识别的同步状态。不能因本地回滚就声称 M1 占用已被回滚。

## 5. 逻辑引用与数据读取矩阵

| 使用方 | 引用/读取的数据 | 所有者 | 合法访问路径 |
|---|---|---|---|
| M1 | 用户、房间、座位、系统参数、签到码、公共审计/通知能力 | D | HTTP；业务记录只保留必要逻辑 ID，持久化快照范围待确认 |
| M2 | 申请人、审批身份/权限、房间 | D | HTTP；不得 JOIN `platform` |
| M3 | 报修人、处理人员身份/权限、房间 | D | HTTP；不得 JOIN `platform` |
| M2 | 本申请对应的房间占用创建/释放 | M1 | 内部 HTTP 占用契约 |
| M3 | 本工单对应的维修占用创建/释放 | M1 | 内部 HTTP 占用契约 |
| M1 | 占用来源申请/工单标识 | M2/M3 | `source_ref_id` 逻辑引用；当前占用契约不授权查询来源数据库 |
| Web | 各模块公开业务数据 | 对应后端服务 | 公开 API，无数据库访问权 |
| 可选 Agent | 源指定业务操作 | 对应后端服务 | 公开 API，继承真实用户权限，不直连数据库 |

本矩阵表示责任边界，不预先承诺 HTTP 接口的具体方法/路径、缓存策略或每次调用次数。确需增加跨服务契约时，按公共契约与跨模块变更规则人工 Review。

## 6. 时间、迁移与保留策略

M1 按整点小时校验预约，默认连续最多 4 小时，创建前检查所有来源占用；时间逻辑使用可注入 `Clock`。区间是否采用左闭右开、输入时间标准化、业务时区、历史展示、预约取消和签到允许时间窗仍待确认。`end_at=null` 的维修占用不能被默认为零时长或自动失效。

每服务 V1 已位于自身 `src/main/resources/db/migration/`，应用启动时由 Flyway 使用专属 migrator 执行；`create-schemas=false`、`clean-disabled=true`，schema 和角色由空库引导创建，禁止应用自行扩权。Flyway 迁移只向前，已执行/发布版本不可修改，修复必须创建新迁移。真实 Testcontainers 空库已自动完成各服务 V1 并通过 Flyway 校验；开发 Compose 也已实际完成本项目 down -v/up -d 空卷迁移及保留卷重启验证，见 [Phase 2 验证记录](../review/phase-2-validation.md)，不混称同一次验证。

结构与 seed 分离。D 的 `db/seed/local/*.sql` 仅由 `local` profile 的 `LocalDemoSeed` 在事务中执行：七个角色字典、五个演示用户及五条用户角色关系、两栋楼、四个房间、四十个座位。五用户分别是 `demo_admin:SYSTEM_ADMIN`、`demo_student_01:STUDENT`、`demo_student_02:STUDENT`、`demo_room_admin:ROOM_ADMIN`、`demo_property_staff:PROPERTY_STAFF`，没有新增其他演示用户或预约、审批、工单。权限与角色权限表保持空，不能假设额外审批人已经存在。

本地演示密码从私有环境提供，校验为非空白且 24–72 UTF-8 字节，生成五个独立 BCrypt(12) 哈希后参数绑定。各 seed 依据自然唯一键 `ON CONFLICT DO NOTHING`，关系 id 通过本 schema JOIN 获取，座位只匹配四个指定演示房间。重复执行不会扩充数量、覆盖已有字段或重置已有密码。非 `local` profile 已验证不需要演示密码、不执行 seed、八表全部为空；生产凭据流程仍属于后续运维阶段。

违约、操作审计、业务事件、通知、个人信息的保存周期未指定；保留、删除、脱敏、停用资源与历史可追溯的政策应持续记录于工程伦理文档。不得预先填入任意保留天数，亦不得将日志当作业务真值来源。

## 7. 当前验证与后续边界

Phase 0 只检查文档覆盖与一致性。Phase 2 已执行 `scripts/test-backend.sh`，JUnit 共 23 项通过、失败 0、错误 0、跳过 0：D 的 7 项 local 测试和 1 项 non-local 测试，另三服务各 5 项。测试用真实 Testcontainers PostgreSQL 及实际引导脚本，覆盖四服务 V1、各 app 在本 schema 的 DML、app 与 migrator 对其他 schema 实际表的读写拒绝、app DDL/角色切换/迁移历史读取与修改拒绝、精确 seed、BCrypt 密码匹配、重放不重置密码、非 local 无 seed，以及各服务 health/info 和路径限制。公共数据库断言位于 [DatabaseTestSupport](../../tests/java/cn/campus/resource/testing/DatabaseTestSupport.java)，服务测试位于各模块 `src/test/java/`。

后续仍必须以真实业务行为验证：预约/占用等业务唯一约束；房间锁阻止并发重复预约；M2/M3 经 HTTP 改变 M1 占用；重试遵守错误类型和次数限制；重复请求与内容冲突处理正确；释放与恢复不误删其他来源占用；后端 RBAC 和完整敏感日志规则生效。这些业务模型、端点、测试及 CI/CD 均未因 Phase 2 数据库基础完成而标为 DONE。
