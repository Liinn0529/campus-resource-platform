# 数据所有权与一致性边界

状态：Phase 4 M1 基础预约实现方案，schema/契约仍待草稿 PR 人工 Review。Phase 2/3 的四 schema、八账号、D V1–V3 与最小 seed 保留；本轮 M1 新增 V2 四张业务表、8 个公开操作，D 增加内部房间列表至 33 个操作。75 个后端测试通过（完整 reactor 69 项后新增 6 项边界，M1 最终子集 28 项）；首次 Compose 保留原卷升级、七容器健康、真实网关流程与数据库/密码保留验证通过，最终干净版本重建仍按 [Phase 4 验证](../review/phase-4-validation.md)另记，不预填。完成后停止等待 Phase 5。具体 API 见[平台](../api/platform.md)与[预约](../api/reservation.md)契约，测试通过不表示人工已批准 schema 或进入下一阶段。

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

引导脚本撤销数据库及 `public` schema 的 PUBLIC 默认权限；各角色只获得该库 CONNECT，应用只获得自身 schema USAGE。迁移账号以 owner 身份在本 schema 执行 DDL，其新表默认向本服务 app 授予 SELECT/INSERT/UPDATE/DELETE，新序列授予 USAGE/SELECT。app 无 schema CREATE、DDL 或切换 migrator 的权限。每份 V1 显式撤销 app 对自身 `flyway_schema_history` 的全部权限，包括读取和修改；该表只由迁移账号维护。Phase 3 V2 额外撤销 platform_app 对 audit_log 的 UPDATE/DELETE/TRUNCATE，保留新增和授权查询。各角色 search_path 固定为自身 schema 与 `pg_catalog`，各服务连接也指定自身 schema。

只有同一服务拥有的数据实体之间可以设计数据库外键。跨服务的用户、房间、座位及业务来源标识均为逻辑 ID，不建立跨 schema 外键。源服务负责其数据真实性，使用方负责按契约校验与处理失效引用；删除限制、快照及历史保留策略尚未确定。

## 2. D：身份与空间主数据

源指定 RBAC 表为 `user`、`role`、`permission`、`user_role`、`role_permission`。D 拥有用户密码哈希、角色权限、楼宇、房间（教室/自习室）、座位主数据和系统参数。Phase 2 的 [V1 迁移](../../services/platform-service/src/main/resources/db/migration/V1__platform_schema.sql) 创建以下八表，原文件保持不变；Phase 3 用[新 V2](../../services/platform-service/src/main/resources/db/migration/V2__platform_common_services.sql)扩展，再以[独立 V3 参考目录](../../services/platform-service/src/main/resources/db/migration/V3__platform_reference_permissions.sql)加载身份权限/参数定义：

| 当前表 | 已实现的数据库边界 |
|---|---|
| `platform."user"` | identity 主键、唯一用户名、非空 BCrypt 格式密码哈希、显示名称、启用标记、创建时间；V2 增加非负 auth_version，用于改密/启用状态变更后失效旧 JWT |
| `platform.role` | identity 主键、唯一角色 code、名称；七类固定角色字典；V3 在所有环境建立 |
| `platform.permission` | identity 主键、唯一权限 code、名称；Phase 3 V3 建立 14 项权限参考定义 |
| `platform.user_role` | `(user_id, role_id)` 复合主键；仅引用本 schema 的用户和角色 |
| `platform.role_permission` | `(role_id, permission_id)` 复合主键；仅引用本 schema 的角色和权限；Phase 3 初始 35 条参考关系，后端按当前 DB 授权 |
| `platform.building` | identity 主键、唯一楼宇 code、名称 |
| `platform.room` | identity 主键、building 外键、楼内 code 唯一、`STUDY_ROOM/CLASSROOM` 类型约束、启用标记 |
| `platform.seat` | identity 主键、room 外键、房内 code 唯一、插座标记、启用标记；不设置业务占用状态 |
| `platform.user_room_scope` | V2 `(user_id,room_id)` 复合主键，均为 D 外键；管理接口只向 ROOM_ADMIN 分配，无隐式范围 |
| `platform.system_setting` | V2 key 主键、正整数 value；V3 默认 max_reservation_hours=4 |
| `platform.room_checkin_code` | V2 `(room_id,date)` 主键、D room 外键、8 位安全字符约束、created_at；code 不要求全局唯一 |
| `platform.audit_log` | V2 identity 主键、同 D actor 用户引用、来源/动作/目标 ID、UUID request_id、时间；app 只新增/查询，不可更新或删除 |

基础实体 id 由数据库 identity 生成，关联通过同 schema 外键约束；local seed 不指定 id、不重置序列。Phase 3 的真实 MyBatis/HTTP 实现包括登录、JWT、后端 RBAC、用户/角色管理及空间维护，后端真实测试与 Compose 原库升级/实际 API 验收已通过；具体行为证据来自测试和运行检查，不能以表/端点存在替代验收。SYSTEM_ADMIN 管理全局 D，ROOM_ADMIN 只能维护显式范围内房间/座位和对应签到码，默认无范围。角色权限与范围从当前 DB 读取；全局管理还要求 SYSTEM_ADMIN 身份，移除/停用最后一名启用系统管理员被拒绝。

D 已在 V2 建立 `room_checkin_code(room_id,date,code,created_at)`。当前草案以注入 Clock 和显式 ZoneId 计算今日，local 使用 Asia/Shanghai，生成 8 位 SecureRandom 字符，并以 (room_id,date) 主键及 room 行锁保证每日/并发 ensure 同条且只记一次创建审计。GET 只读已有今日记录；POST ensure 首次/重复均为 200，不提供当日轮换或全局 code 唯一。SYSTEM_ADMIN 或已授权房间的 ROOM_ADMIN 可取码，学生不可读码；停用房间不能生成或校验有效码。时区/码规则为待人工 review 的具体方案，QR 载荷、实际预约签到窗口和补签到仍留 Phase 5；明文码不写入日志或审计。

D 现提供内部房间列表、room/seat 主数据、settings 读取和 checkin-codes/verify 共五个操作；座位读取增加 enabledOnly/hasPowerOutlet/seatId，过滤在 D SQL 分页前执行。三调用方使用各自独立 opaque 凭据，只有 reservation-service 可验码，用户 JWT 不能冒充内部身份，Nginx 不公开 internal。M1 本轮真实通过 HTTP 转发用户 JWT 到 D `/api/v1/me` 验证当前身份/权限，另用自身服务凭据获取元数据与参数；不共享 JWT 签名秘密、不读 D 表。最大时长每次新建/再预约读取当前正整数快照（初始 4），不追溯改写原预约；实际签到仍留 Phase 5。

M2/M3 对房间不可用的影响不能通过修改 D 的座位主数据代替占用记录。D 对房间/座位的维护与占用的关系，尤其是有未来预约时停用或删除资源的处理，必须在实现前明确。

## 3. M1：占用与房间并发控制的唯一所有者

Phase 2 的 `reservation` V1 只建立迁移版本/权限基线。本轮追加 [V2](../../services/reservation-service/src/main/resources/db/migration/V2__reservation_workflow.sql)，创建 room_gate、seat_reservation、seat_occupancy、reservation_event；checkin/violation 留 Phase 5。所有 D 用户/房间/座位均为逻辑 ID，仅 rebook_of 和事件到预约使用 M1 自身外键。预约约束保留五种源状态，本轮只产生 CREATED/CANCELLED，禁止用时间流逝提前写 COMPLETED/NO_SHOW。

| 源指定实体 | 责任 |
|---|---|
| `room_gate` | 每房间一条互斥控制记录，`room_id` 为主键；归 M1，不属于 D |
| `seat_reservation` | 预约业务记录和 `CREATED/CANCELLED/CHECKED_IN/NO_SHOW/COMPLETED` 状态 |
| `seat_occupancy` | 统一记录来自 M1/M2/M3 的占用、时间及释放信息 |
| `checkin` | 与预约相关的签到结果 |
| `violation` | 未按规则签到等源指定违约记录 |
| `reservation_event` | 预约事件；与 D 操作审计分别归属，不以共享表替代 |

`seat_occupancy` 保留以下全部 11 个源指定字段，具体类型/约束见 V2：

| 字段 | 基线含义 |
|---|---|
| `id` | 占用记录标识 |
| `room_id` | D 房间的逻辑标识 |
| `seat_id`（可空） | D 座位的逻辑标识；null 表示整房间，影响该区间内房间所有座位 |
| `start_at` | 占用开始时间 |
| `end_at`（可空） | 占用结束时间；维修可未指定结束时间 |
| `source_module` | `M1`、`M2` 或 `M3` |
| `source_type` | `RESERVATION`、`CLASSROOM_BOOKING` 或 `MAINTENANCE` |
| `source_ref_id` | 来源业务记录的逻辑标识 |
| `status` | 本轮具体方案为 ACTIVE/RELEASED；与 released_at 空值关联约束，不复用预约状态 |
| `created_at` | 创建时间 |
| `released_at` | 释放时间 |

来源组合按业务含义分别为 M1/RESERVATION、M2/CLASSROOM_BOOKING、M3/MAINTENANCE。调用方自身的申请或工单状态不取代 M1 占用账本；M1 也不决定 M2 审批结果或 M3 验收结果。一条 seat_id=null 的 ACTIVE 房间记录覆盖查询到的该房间座位，不复制每座记录；本轮以真实隔离数据库中的 M2/M3 形状夹具验证冲突，但尚无真实 M2/M3 占用 HTTP 写接口。资源增删/移动与已有预约的协调仍待审。

源要求唯一键为 `(source_module, source_ref_id, source_type)`：相同键且内容一致返回已有结果，内容冲突返回 409。不能为了给房间每个座位复制记录而破坏此约束，也不能省略来源记录。已释放记录保留多久、原键是否可重用、内容比较包含哪些字段，均待契约 Review。

```text
先通过真实 D HTTP 校验当前身份、启用房间/座位与参数
开始 M1 本地数据库事务（不在持锁期间请求 D）
  INSERT room_gate ... ON CONFLICT DO NOTHING
  锁定对应 room_gate 行：SELECT ... FOR UPDATE
  重新检查 Clock 与业务时间
  检查相关 M1/M2/M3 占用
  创建或释放占用，并维护相关 M1 本地业务记录
提交事务
```

所有改变占用的实现均应遵守同房间事务规则。本轮 M1 懒建锁行与首次并发真实测试已通过：同座位六个竞争请求仅一个 201，其余 409；不同座位可分别成功。创建同事务写预约/ACTIVE 占用/CREATED 或 REBOOKED 事件；取消锁房间及记录，仅将精确 M1/RESERVATION/预约 ID 来源改为 RELEASED，保留取消和事件历史；账本不一致则回滚，重复取消不新增事件。M2/M3 写接口未来也必须遵守同一锁，不将本轮读冲突兼容性当作其集成完成。D 校验只是 HTTP 快照，不声称与 M1 提交具有跨服务原子性。

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

本矩阵保留目标责任边界。Phase 4 已实现 M1→D 的真实身份/主数据/参数 HTTP，D 有五个内部读取/验码操作，见[平台 OpenAPI](../../contracts/openapi/platform.v1.json)与[预约 OpenAPI](../../contracts/openapi/reservation.v1.json)。实际签到、M2/M3 业务调用、远程审计写入和通知仍未实现。后续新增方法/路径、缓存或调用行为仍须按公共契约和跨模块规则人工 Review。

## 6. 时间、迁移与保留策略

M1 本轮具体待审方案：带 offset 输入转 Instant；显式业务时区的分钟/秒/纳秒为 0，正整数小时区间；采用 `[start,end)`，相邻可用。新建/再预约开始不得早于注入 Clock，读取当前 D 最大连续时长（初始 4），不私加未来天数或一人多座限制。只有本人 CREATED 且开始前可取消；重复 CANCELLED 幂等，开始时及以后 409。历史为取消或已结束区间，不自动转换状态；再预约新建 ID 并重读当前条件。签到窗口、自动完成/违约仍未定。所有 ACTIVE 来源与 seat_id=null 均参与冲突，end_at=null 持续影响直到释放，不默认为零时长。

各迁移位于自身 `src/main/resources/db/migration/`；公共层当前 V1–V3，M1 为 V1–V2，M2/M3 为 V1，应用启动时由 Flyway 使用专属 migrator 执行；`create-schemas=false`、`clean-disabled=true`，schema 和角色由空库引导创建，禁止应用自行扩权。Flyway 迁移只向前，已执行/发布版本不可修改，修复必须创建新迁移。真实 Testcontainers 空库已自动完成各服务 V1 并通过 Flyway 校验；开发 Compose 也已实际完成本项目 down -v/up -d 空卷迁移及保留卷重启验证，见 [Phase 2 验证记录](../review/phase-2-validation.md)，不混称同一次验证。

结构与演示 seed 分离。V2 只有结构/权限变更；V3 是全环境的七角色、14 权限、初始 35 条角色权限关系和默认参数参考数据，不创建演示用户/空间。D 的 `db/seed/local/*.sql` 仅由 `local` profile 的 `LocalDemoSeed` 在事务中执行，保留角色字典的幂等写入、五个演示用户及五条用户角色关系、两栋楼、四个房间、四十个座位。五用户分别是 `demo_admin:SYSTEM_ADMIN`、`demo_student_01:STUDENT`、`demo_student_02:STUDENT`、`demo_room_admin:ROOM_ADMIN`、`demo_property_staff:PROPERTY_STAFF`，没有新增其他演示用户或预约、审批、工单。角色权限参考目录不增加用户或额外审批人；demo_room_admin 初始没有房间 scope。通过管理员 API 创建的合法业务/测试用户不同于扩充 local seed。

本地演示密码从私有环境提供，校验为非空白且 24–72 UTF-8 字节，生成五个独立 BCrypt(12) 哈希后参数绑定。各 seed 依据自然唯一键 `ON CONFLICT DO NOTHING`，关系 id 通过本 schema JOIN 获取，座位只匹配四个指定演示房间。重复执行不会扩充数量、覆盖已有字段或重置已有密码。Phase 3 的真实 non-local 测试已验证：保留 V3 参考目录和参数，不创建演示用户/空间/码/审计，也不要求演示密码。所有 profile 均要求外部 JWT/独立内部凭据和显式业务时区；无默认生产管理员，初始化及秘密注入/轮换流程仍须后续运维审阅。

违约、操作审计、业务事件、通知、个人信息的保存周期未指定；保留、删除、脱敏、停用资源与历史可追溯的政策应持续记录于工程伦理文档。不得预先填入任意保留天数，亦不得将日志当作业务真值来源。

## 7. 当前验证与后续边界

Phase 0 只检查文档覆盖与一致性。以下保留历史：Phase 2 已执行 `scripts/test-backend.sh`，JUnit 共 23 项通过、失败 0、错误 0、跳过 0：D 的 7 项 local 测试和 1 项 non-local 测试，另三服务各 5 项。测试用真实 Testcontainers PostgreSQL 及实际引导脚本，覆盖四服务 V1、各 app 在本 schema 的 DML、app 与 migrator 对其他 schema 实际表的读写拒绝、app DDL/角色切换/迁移历史读取与修改拒绝、精确 seed、BCrypt 密码匹配、重放不重置密码、非 local 无 seed，以及各服务 health/info 和路径限制。公共数据库断言位于 [DatabaseTestSupport](../../tests/java/cn/campus/resource/testing/DatabaseTestSupport.java)，服务测试位于各模块 `src/test/java/`。

Phase 3 当前已有 32 操作及真实 HTTP/PostgreSQL 测试代码：登录/改密/重置、JWT 到期/签名/issuer/audience/authVersion、即时撤权、用户/角色/范围管理、最后启用系统管理员并发保护、空间维护、参数、上海日界/并发码及内部身份。完整套件已执行，Surefire 核对共 49 项通过、0 失败、0 错误、0 跳过：D 身份 12 项、空间/公共服务 7 项、用户/角色/审计 7 项、运行/seed 7 项、non-local 1 项，另三服务各 5 项；前端 6 项及 lint/类型检查/构建也通过。通过代码测试的首个功能分项已提交 c5fd067012ea40c8a9163261e0bb9b68f4c71a19。实际 Compose 保留已有 Phase 2 数据卷从 V1 升至 V3，原演示实体与五用户密码指纹不变，五镜像构建、七容器健康、公开/内部边界和真实公共层 smoke 已通过。首次联调为 c5fd067012ea40c8a9163261e0bb9b68f4c71a19-dirty / 2026-10-24T13:03:02Z；本轮没有执行 down -v，不将 Phase 2 历史重放冒充本次升级。最终干净提交重建元数据由仓库外交付报告记录，过程见[Phase 3 记录](../plans/phase-3.md)。

Phase 4 以真实 D JAR/HTTP 与 Testcontainers PostgreSQL 验证 M1 查询/插座、当前 D 参数、首次房间锁竞争、相邻区间和所有来源/开放结束冲突、即时撤权/停用、本人取消/历史/再预约及账本回滚。完整 reactor 69 项通过后再加入 6 项客户端错误响应边界测试，M1 最终子集 28 项通过；当前 Surefire 共 75 项通过、0 失败/错误/跳过。客户端错误响应夹具仅验证失败边界，不替代真实 D 集成。首次 Compose 保留卷升级至 D V3/M1 V2、七容器健康、真实网关公共层回归与预约/取消/再预约均通过，原演示计数和五用户密码指纹不变；实际 Nginx 配置与数据库隔离/审计/账本/事件检查通过。首次构建为 6d98a8752f9f5babb4e1f28b178d3dacaf798bf9-dirty / 2026-10-25T14:30:16Z；最终干净版本重建另见 [Phase 4 验证](../review/phase-4-validation.md)，不预填。后续仍须验证签到、M2/M3 经 HTTP 改变占用的幂等/失败恢复、完整 Web、全服务日志、CI/CD 与云部署。
