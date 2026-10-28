# 平台 API 契约（Phase 8 成员二草案）

本文件描述 D 公共层的实际代码方案和待审阅草案，机器契约为 [OpenAPI 3 JSON](../../contracts/openapi/platform.v1.json)。Phase 3 交付基础能力，Phase 4 增加内部房间目录及座位筛选，Phase 5 增加真实 M1 提醒接收和本人站内收件箱；真实结果以阶段验证记录为准，静态契约不能代替运行验证或人工审阅。运行信息端点另见 [runtime.md](runtime.md)。当前契约含 39 个方法/路径组合，其中两个 Phase 8 成员二接口仍是草案，须由非作者审核后冻结。预约与实际签到由 M1 决定；M2审批与M3工单由各自服务提供。

## 地址、认证与响应

公开 API 由本地网关 `http://127.0.0.1:18080/api/v1` 代理至 platform-service。除登录外，下列公开 API 都要求 `Authorization: Bearer <access JWT>`。固定 HS256、issuer `https://campus-resource.local/platform`、audience `campus-web`，默认 15 分钟有效期；同时检查 exp、iat、nbf、subject 和 authVersion。签名秘密来自私有运行环境，不通过 issuer 域名发现密钥，不提供 refresh token。

认证后每个请求重新读取用户启用状态、角色和权限。停用或密码变更使已有 JWT 无效；角色/权限/房间范围调整在后续请求立即生效。用户名不区分为独立“学号”字段；第一版用同一个 username 标识登录，不接学校统一身份认证。响应中的用户对象不包含密码哈希或 authVersion。

成功返回 `{"data": ..., "requestId": "..."}`；列表的 data 直接为数组，没有 total/page 外壳。失败返回 `{"code": "...", "message": "...", "requestId": "..."}`。X-Request-ID 响应头与 body 相同，由服务器生成；客户端按 code 处理错误，不依赖 message 的语言。JSON 业务成功使用 200 或 201；本阶段没有硬删除或 204 接口。网关自身的路由拒绝、超时或请求体大小限制可能先于应用返回，不承诺这些网关错误也是应用 JSON envelope。

| 状态 | 语义 / 稳定 code 示例 |
|---|---|
| 400 | 语法、字段、范围或分页校验失败：VALIDATION_ERROR；空空间 PATCH：EMPTY_PATCH |
| 401 | 缺少/失效 JWT 或内部身份：UNAUTHORIZED；登录与当前密码错误：AUTHENTICATION_FAILED |
| 403 | 权限、角色或房间范围不符：FORBIDDEN |
| 404 | USER_NOT_FOUND、ROLE_NOT_FOUND、BUILDING_NOT_FOUND、ROOM_NOT_FOUND、SEAT_NOT_FOUND、CHECKIN_CODE_NOT_FOUND、NOT_FOUND |
| 409 | 唯一键等 DATA_CONFLICT、LAST_SYSTEM_ADMIN、SYSTEM_ADMIN_PERMISSIONS_FIXED、IDENTITY_CHANGED；NOTIFICATION_SOURCE_CONFLICT、AUDIT_EVENT_SOURCE_CONFLICT |
| 422 | 未知角色/权限/房间引用或不符合管理规则：INVALID_REFERENCE_OR_VALUE；通知为 NOTIFICATION_RECIPIENT_UNAVAILABLE / NOTIFICATION_INVALID_WINDOW；审计为 AUDIT_EVENT_NOT_ALLOWED |
| 500 / 503 | 不返回异常详情：INTERNAL_ERROR / DATABASE_UNAVAILABLE |

带分页的列表支持 limit 1–100、offset ≥0；用户/审计/通知默认 limit=50，空间默认 100。排序及过滤见下面各组与 OpenAPI。输入中的密码为写入字段，不得放在 URL、查询字符串或日志中。新设密码必须非空白且为 12–72 个 UTF-8 字节，使用 BCrypt cost 12；登录密码最多 72 字节。

## 角色、范围与参考目录

V3 在所有环境加载七类固定角色和 14 项权限参考定义，初始共 35 条角色权限关系。角色 code 不通过 API 新建或删除；权限 code 来自固定后端目录，不能提交任意新权限。SYSTEM_ADMIN 的权限关系固定，其他角色可由系统管理员调整，但全局管理仍同时要求 SYSTEM_ADMIN 身份和对应权限，房间维护仍受实际 room scope 限制。

| 角色 | 初始权限 code |
|---|---|
| SYSTEM_ADMIN | 全部 14 项 |
| STUDENT | reservation:create、reservation:view:self、classroom:apply、maintenance:report、space:view、settings:view |
| ROOM_ADMIN | classroom:approve、space:view、space:manage、settings:view、checkin-code:manage |
| DEPARTMENT_ADMIN | classroom:approve、space:view、settings:view |
| PROPERTY_STAFF | space:view、settings:view |
| PROPERTY_MANAGER | maintenance:assign、space:view、settings:view |
| OPERATOR | space:view、settings:view |

全部权限为以上集合，再加全局 user:manage、role:manage、audit:view、settings:manage。预约相关权限已用于 M1；审批和派单权限名称仍只是参考目录，不表示对应业务已经实现。房间管理员默认没有任何房间范围；管理员显式分配后才能维护该房间。普通启用空间目录供有 space:view 的用户读取；管理目录按授权范围过滤，不能把普通查询权限当作修改权限。

local seed 保持五个演示用户，不为 DEPARTMENT_ADMIN、PROPERTY_MANAGER、OPERATOR 额外生成用户，也不暗中授予 demo_room_admin 房间范围。额外真实/测试业务账号应通过授权管理创建，不能扩充演示 seed。

## 身份与用户管理（12 个操作）

下列路径以 `/api/v1` 为前缀。

| 方法与路径 | 请求 / 响应 data | 权限 |
|---|---|---|
| POST /auth/login | `{username,password}` → `{accessToken,tokenType,expiresAt,user}` | 无需 JWT；错误用户名/密码/停用使用相同通用 401 |
| GET /me | Actor：id、username、displayName、roles、permissions | 已登录 |
| PUT /me/password | `{currentPassword,newPassword}` → `{changed:true}` | 已登录；改密后旧密码和旧 JWT 失效，须重新登录 |
| GET /users | User 数组：id、username、displayName、enabled、roles、roomIds；按 id 升序分页 | SYSTEM_ADMIN + user:manage |
| POST /users | `{username,displayName,password,roleCodes}` → 新建 User，201 | SYSTEM_ADMIN + user:manage |
| PATCH /users/{id} | displayName、enabled 中至少一项非 null → User | SYSTEM_ADMIN + user:manage |
| PUT /users/{id}/password | `{password}` → User；重置后旧 JWT 失效 | SYSTEM_ADMIN + user:manage |
| PUT /users/{id}/roles | `{roleCodes:[...]}` 全量替换 → User | SYSTEM_ADMIN + user:manage |
| PUT /users/{id}/room-scopes | `{roomIds:[...]}` 全量替换 → User | SYSTEM_ADMIN + user:manage；目标用户必须有 ROOM_ADMIN 或 DEPARTMENT_ADMIN 角色 |
| GET /roles | `{id,code,name,permissionCodes}` 数组 | SYSTEM_ADMIN + role:manage |
| GET /permissions | `{id,code,name}` 数组 | SYSTEM_ADMIN + role:manage |
| PUT /roles/{code}/permissions | `{permissionCodes:[...]}` 全量替换 → Role | SYSTEM_ADMIN + role:manage；不能修改 SYSTEM_ADMIN 权限 |

创建用户默认 enabled=true，username 为 `[A-Za-z0-9][A-Za-z0-9._-]{2,63}`，displayName 非空白且最多 100 字符；roleCodes 最多七项，可以为空但只能引用既有角色。不隐式分配房间。角色移除 ROOM_ADMIN 时同时删除该用户的房间范围。取消最后一名启用 SYSTEM_ADMIN 的角色或停用该用户返回 409，相关操作由共享角色锁串行保护。

用户管理不提供硬删除。PATCH 的缺失/null 字段不修改原值；没有任何有效字段、空白 displayName 返回 422，超过字段长度等 Bean Validation 错误为 400。角色、权限和房间关系替换与审计在同一数据库事务内执行；失败不保留一半关系。

## 空间管理（11 个公开操作）

| 方法与路径 | 请求 / 行为 | 权限 |
|---|---|---|
| GET /api/v1/buildings | Building 数组，id 升序分页 | space:view |
| POST /api/v1/buildings | `{code,name}` → Building，201 | SYSTEM_ADMIN + space:manage |
| PUT /api/v1/buildings/{id} | `{code,name}` → Building | SYSTEM_ADMIN + space:manage |
| GET /api/v1/rooms | 仅 enabled 房间，可按 buildingId 过滤，id 升序分页 | space:view |
| POST /api/v1/rooms | `{buildingId,code,name,type,enabled}` → Room，201 | SYSTEM_ADMIN + space:manage |
| GET /api/v1/managed-rooms | 包含已停用房间；系统管理员全局，其他用户只返回显式范围；支持 buildingId/分页 | space:manage |
| GET /api/v1/rooms/{id} | 单个 Room；非管理范围的已停用房间不可见 | space:view |
| PATCH /api/v1/rooms/{id} | 可选 buildingId、code、name、type、enabled，至少一项 → Room | space:manage + 当前房间范围；buildingId/code/type 任一字段出现还要求 SYSTEM_ADMIN |
| GET /api/v1/rooms/{roomId}/seats | Seat 数组；管理范围可见停用座位，其他用户只见启用座位/房间 | space:view |
| POST /api/v1/rooms/{roomId}/seats | `{code,hasPowerOutlet,enabled}` → Seat，201 | space:manage + 该房间范围 |
| PATCH /api/v1/seats/{id} | 可选 roomId、code、hasPowerOutlet、enabled，至少一项 → Seat | space:manage + 原房间范围；出现 roomId 还要求 SYSTEM_ADMIN |

Building 为 id/code/name；Room 另含 buildingId/type/enabled；Seat 为 id/roomId/code/hasPowerOutlet/enabled。code 格式为 `[A-Za-z0-9][A-Za-z0-9_-]{0,63}`；房间 type 仅 STUDY_ROOM 或 CLASSROOM。楼宇 code 唯一，房间 code 在楼内唯一，座位 code 在房间内唯一，冲突返回 409。创建空间要求显式 enabled；名称非空白且最多 100 字符。ID 为正整数；未知楼宇/房间/座位返回相应 404，但未授权用户可能在查询资源前先被 403 拒绝。

本阶段没有硬删除。启停和移动主数据尚未与未来预约/占用协调；相关业务政策必须在 M1 与跨模块阶段继续定义，不能把 D 的 enabled 当作预约真值。

## 参数、签到码与审计（6 个公开操作）

| 方法与路径 | 请求 / 行为 | 权限 |
|---|---|---|
| GET /api/v1/settings | `{maxReservationHours}`，初始参考值 4 | settings:view |
| PUT /api/v1/settings | `{maxReservationHours:正整数}` → Settings | SYSTEM_ADMIN + settings:manage |
| GET /api/v1/rooms/{id}/checkin-code | 只读今日已有 `{roomId,date,code}`；未创建返回 404 | checkin-code:manage + 房间范围 |
| POST /api/v1/rooms/{id}/checkin-code | 确保今日记录存在后返回同样对象；首次和重复均为 200 | checkin-code:manage + 房间范围 |
| GET /api/v1/audit-events | AuditEvent 数组，按 id 降序分页，可用 targetType 过滤 | SYSTEM_ADMIN + audit:view |
| GET /api/v1/maintenance-staff | 当前可派维修人员 `{id,displayName}` 列表，按 id 升序分页 | PROPERTY_MANAGER + maintenance:assign |

最大连续预约时长存为正整数，D 维护和提供该值；M1 每次新建/再预约读取当前快照，既有预约不随参数改写，详见[预约契约](reservation.md)。

签到码日期由注入 Clock 和显式 `CAMPUS_BUSINESS_ZONE` 计算，本地为 Asia/Shanghai；客户端不能提交任意日期。每日每房间一条记录，由 `(room_id,date)` 主键保护。代码为 SecureRandom 生成的 8 位 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` 字符，不含 I/O/0/1，不要求跨房间全局唯一。重复或并发 ensure 返回同一条记录且只产生一条创建审计。不提供同日轮换或 QR 载荷；预约资格与实际签到由 M1 的真实 HTTP 调用及房间锁事务决定。停用房间不能生成/读取有效码；学生不能取码。验证码和令牌均不得写入日志或审计。

D 修改与审计同事务。AuditEvent 包含 id、actorUserId、sourceService、sourceEventId（D本地事件为null）、action、targetType、targetId、requestId、occurredAt，没有输入正文、密码、JWT 或签到码。targetType 过滤为 `[A-Z_]{1,32}`；审计应用身份不可 UPDATE/DELETE，查询由权限控制。审计保存期限仍未定，当前不提供清理或导出机制。

## 私网内部接口（6 个操作）

以下请求只能在 backend 网络访问 `http://platform-service:8080`，公网/本地浏览器网关对 `/internal` 始终返回 404。Header 同样使用 `Authorization: Bearer <opaque service secret>`，但它是与用户 JWT 完全分开的秘密和安全链；用户 JWT 不能替代服务身份。

| 方法与路径 | 能力 | 调用方 |
|---|---|---|
| GET /internal/v1/rooms | 启用房间目录，buildingId 可选，按 ID 排序与分页，保留原始房间类型 | reservation-service、classroom-service、maintenance-service |
| GET /internal/v1/rooms/{id} | 读取房间主数据，包括 enabled | 三个服务身份 |
| GET /internal/v1/rooms/{roomId}/seats | 座位主数据分页；enabledOnly、hasPowerOutlet、seatId 可选筛选 | 三个服务身份 |
| GET /internal/v1/settings | 读取最大预约时长 | 三个服务身份 |
| POST /internal/v1/checkin-codes/verify | `{roomId,code}` → `{valid:boolean}` | 仅 reservation-service |
| POST /internal/v1/notifications | 最小提醒载荷 → 已持久化 Notification；首次201、相同重复200 | 仅 reservation-service |

三个秘密分别对应 `PLATFORM_RESERVATION_TOKEN`、`PLATFORM_CLASSROOM_TOKEN`、`PLATFORM_MAINTENANCE_TOKEN`，必须独立且至少 32 个 UTF-8 字节。D 根据受验证的凭据判断调用方，不信任请求体自报 module。语法正确但房间不存在/停用、当日未生成码或不匹配均返回 200、valid=false；非法 ID 或不是合法 8 位格式的 code 返回 400。校验不生成记录、不泄露正确码，也不校验预约或用户归属；M1 Phase 5 使用该真实结果再校验本人预约、签到窗口和账本。

内部目录的 limit 为 1–100（默认 100），offset≥0；调用方逐页读取，不能把第一页当成完整数据。座位默认仍包含停用主数据，以保留原契约；`enabledOnly=true` 时只返回启用房间内的启用座位。`hasPowerOutlet=true/false` 按真实插座标记筛选，`seatId` 是房间内的精确正整数 ID；全部筛选在 SQL 的 LIMIT/OFFSET 前执行。禁用房间在 enabledOnly 模式返回空座位列表，未知房间返回 404。D 只提供主数据快照，不返回或判断 M1 占用状态。

## 站内提醒收件箱（1 个公开操作）

`GET /api/v1/notifications` 要求用户 JWT 和 `reservation:view:self`，只返回当前认证用户的记录，SYSTEM_ADMIN 同样没有跨用户覆盖。支持 limit（默认50、1–100）和 offset（默认0、非负），按 id 降序；`data` 是 Notification 数组。历史消息保留，`expiresAt` 是原提醒的业务有效窗口，不代表收件箱自动删除时间。

Notification 字段为 `id`、`userId`、`sourceModule`（固定 M1）、`sourceRefId`、`reservationId`、`roomId`、`startAt`、`kind`、`dueAt`、`expiresAt`、`createdAt`。时间序列化为 UTC Instant；createdAt 来自 D 接收时的 Clock。kind 为 `CHECKIN_FIRST` 或 `CHECKIN_SECOND`，客户端可以据此展示对应的确定性提醒。没有任意消息正文、签到码、密码或额外联系方式字段。

当前能力是可查询的真实站内持久记录。D 的成功响应证明消息已经进入本人收件箱；未提供用户已读回执、邮件、短信或推送渠道，不能把接收等同于这些渠道送达。完整业务 Web 收件箱属于 Phase 9。

## M1 提醒接收、幂等与审计

`POST /internal/v1/notifications` 只接受 reservation-service 的专属 opaque 凭据。用户 JWT、M2/M3 凭据均不能调用；公共网关继续隐藏 internal。JSON 必填字段为：

| 字段 | 约束 |
|---|---|
| sourceRefId | `[A-Za-z0-9][A-Za-z0-9:_-]{0,63}`，实际 M1 使用 `reservation-reminder:<持久提醒ID>` |
| userId、reservationId、roomId | 正整数；M1 提供的最小逻辑关联，不跨 schema 外键或 SQL |
| startAt、dueAt、expiresAt | 必须带 offset，转换成 Instant 比较 |
| kind | CHECKIN_FIRST 或 CHECKIN_SECOND |

FIRST 的 dueAt 必须为 startAt−15分钟、expiresAt 为 startAt+10分钟；SECOND 必须分别为 startAt+10分钟和 startAt+15分钟。D 校验这些字段关系；M1 负责当前预约资格和实际发送窗口，D 不反向调用 M1。畸形 JSON、缺失/非法/未知字段为400；不符合窗口关系为422 `NOTIFICATION_INVALID_WINDOW`。这里不接受任意通知文本或客户端自报 sourceModule。

新来源 `(M1,sourceRefId)` 要求接收人存在且启用，失败为422 `NOTIFICATION_RECIPIENT_UNAVAILABLE`。首次返回201完整 Notification；记录与 `NOTIFICATION_ACCEPTED` 审计同一 D 事务提交，审计 actor 为空、source_service 为 reservation-service。相同来源、相同接收人/预约/房间/kind/各时间返回200原记录和原ID，即使随后用户停用也保留已经接收的事实；不重复写消息或接受审计。时间按 Instant 比较，因此等价 offset 不算变更。来源被用于不同载荷时返回409 `NOTIFICATION_SOURCE_CONFLICT`。

M1 持久队列使用相同 sourceRefId 处理超时后回执未知和进程恢复，D 唯一约束裁决并发重复。M1 只有在验证完整匹配回执后记录 ACCEPTED；业务重试、过期、在途限制见[预约契约](reservation.md#提醒调度投递与恢复)。D 的记录不参与决定预约状态。

通知与违约保存期限、删除和申诉政策仍待人工 review，当前保留不能解释为批准永久保存。当前没有通用跨服务审计写入端点；D 只为自己的真实通知接收写审计，M1 的签到/NO_SHOW 事件由其本地事务记录。

## Phase 6 本人审批范围

`GET /api/v1/me/room-scopes` 要求当前用户具有 `classroom:approve`，返回成功 envelope，data 为本人已授权房间 ID 升序数组；不接收其他用户ID。缺失/失效JWT为401，当前无审批权限为403，不把拒绝改成空范围。系统管理员经原管理API向 ROOM_ADMIN 或 DEPARTMENT_ADMIN 显式分配范围，移除两类角色后清除范围；同一JWT下一请求立即看到当前范围/权限。

部门范围仅用于M2审批，不授予D空间维护或签到码读取；D原ROOM_ADMIN/系统管理员的资源维护规则保持。M2仍须同时验证对应阶段角色、权限和明确房间范围，不能仅凭SYSTEM_ADMIN或一个审批权限越级。部门组织模型、完整Web和M1整房间占用集成不由这个接口冒充已完成。

## Phase7维修人员资格

`GET /internal/v1/maintenance-staff/{id}`仅maintenance-service专属凭据可调用；其他有效服务403，用户JWT/无效凭据401，非法ID400。200 envelope的data仅含id与eligible；当前enabled且PROPERTY_STAFF才true，不存在、停用或失去角色均false，不返回姓名/密码/权限。M3派单时真实调用此接口；不共享D表，不扩seed。

## Phase8成员二接口草案（待成员四和服务负责人review）

`GET /api/v1/maintenance-staff` 仅当前同时具有 `PROPERTY_MANAGER` 角色及 `maintenance:assign` 权限的用户可读。支持 `limit`（默认50，1–100）与 `offset`（默认0，非负），按用户ID升序分页；只返回当前 enabled 且拥有 `PROPERTY_STAFF` 角色的 `{id,displayName}`。不返回用户名、联系方式、角色详情或凭据。M3浏览器页面使用当前用户JWT，不持有M3内部服务密钥。

`POST /internal/v1/audit-events` 通过M1/M2/M3各自现有的独立服务凭据鉴别来源，D要求认证服务身份与 `sourceModule` 一致。载荷只含 `sourceModule`、稳定 `sourceEventId`、白名单 `action`、白名单 `entityType`、`entityId`、可空 `actorUserId`、`occurredAt` 和 `sourceRequestId`；不收正文或个人详情。生产者必须从其真实用户认证上下文写入actor，系统事件为null，D信任该已认证生产者并校验 actor 外键，不跨schema核查业务表。

来源动作草案：M1 `RESERVATION` 允许 `CREATED/REBOOKED/CANCELLED/CHECKED_IN/NO_SHOW`；M2 `CLASSROOM_REQUEST` 允许 `SUBMITTED/REJECTED/FIRST_APPROVED/FINAL_APPROVED/CANCELLED/AUTO_CANCELLED/SYNC_FAILED/SYNC_ATTEMPTED/OCCUPANCY_CONFIRMED/SYNC_RETRY_SCHEDULED`；M3 `MAINTENANCE_TICKET` 允许 `REPORTED/ASSIGNED/START_REQUESTED/FINISHED/ACCEPT_REQUESTED/ACCEPTANCE_REJECTED/CANCELLED/REVIEWED/ESCALATED/SYNC_FAILED/SYNC_ATTEMPTED/OCCUPANCY_CONFIRMED/RELEASE_CONFIRMED/SYNC_RETRY_SCHEDULED`。动作枚举须由各模块负责人核对并由成员四复核，不代表生产者已实现投递。

新 `(source_service,sourceEventId)` 返回201与 `id/status=ACCEPTED`；相同键、相同载荷重放返回200及原ID；相同键载荷不一致返回409 `AUDIT_EVENT_SOURCE_CONFLICT`；来源身份不匹配403，动作/目标不在白名单422 `AUDIT_EVENT_NOT_ALLOWED`。重放比较把 `occurredAt` 转为 Instant 后截断到微秒，与 PostgreSQL 时间精度一致；落入同一微秒的亚微秒差异按相同载荷处理。此时间等价规则是待负责人和成员四确认的契约提案。插入和去重由D事务及唯一约束裁决，业务事件与投递队列仍由各生产者在自己的schema内原子保存并负责重试；D接收成功才表示审计已持久化，不表示业务事件生产者已确认投递。字段、白名单和actor可信边界均待人工评审。
