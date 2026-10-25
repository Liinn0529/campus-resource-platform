# Phase 3 平台 API 契约

本文件描述 D 公共层的实际代码方案，完整机器契约为 [OpenAPI 3 JSON](../../contracts/openapi/platform.v1.json)。Phase 3 交付基础能力，Phase 4 增加内部房间目录及座位筛选；真实结果以阶段验证记录为准，不以静态契约代替运行验证或人工审阅。运行信息端点另见 [runtime.md](runtime.md)。本文共列 33 个方法/路径组合，不包含其他模块的预约、实际签到、审批、工单、通知投递或远程审计写入端点。

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
| 409 | 唯一键等 DATA_CONFLICT、LAST_SYSTEM_ADMIN、SYSTEM_ADMIN_PERMISSIONS_FIXED、IDENTITY_CHANGED |
| 422 | 未知角色/权限/房间引用或不符合管理规则：INVALID_REFERENCE_OR_VALUE |
| 500 / 503 | 不返回异常详情：INTERNAL_ERROR / DATABASE_UNAVAILABLE |

带分页的列表支持 limit 1–100、offset ≥0；用户/审计默认 limit=50，空间默认 100。排序及过滤见下面各组与 OpenAPI。输入中的密码为写入字段，不得放在 URL、查询字符串或日志中。新设密码必须非空白且为 12–72 个 UTF-8 字节，使用 BCrypt cost 12；登录密码最多 72 字节。

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

全部权限为以上集合，再加全局 user:manage、role:manage、audit:view、settings:manage。后续模块的权限名称只是参考目录，不表示预约、审批或派单接口已经实现。房间管理员默认没有任何房间范围；管理员显式分配后才能维护该房间。普通启用空间目录供有 space:view 的用户读取；管理目录按授权范围过滤，不能把普通查询权限当作修改权限。

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
| PUT /users/{id}/room-scopes | `{roomIds:[...]}` 全量替换 → User | SYSTEM_ADMIN + user:manage；目标用户必须有 ROOM_ADMIN 角色 |
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

## 参数、签到码与审计（5 个公开操作）

| 方法与路径 | 请求 / 行为 | 权限 |
|---|---|---|
| GET /api/v1/settings | `{maxReservationHours}`，初始参考值 4 | settings:view |
| PUT /api/v1/settings | `{maxReservationHours:正整数}` → Settings | SYSTEM_ADMIN + settings:manage |
| GET /api/v1/rooms/{id}/checkin-code | 只读今日已有 `{roomId,date,code}`；未创建返回 404 | checkin-code:manage + 房间范围 |
| POST /api/v1/rooms/{id}/checkin-code | 确保今日记录存在后返回同样对象；首次和重复均为 200 | checkin-code:manage + 房间范围 |
| GET /api/v1/audit-events | AuditEvent 数组，按 id 降序分页，可用 targetType 过滤 | SYSTEM_ADMIN + audit:view |

最大连续预约时长存为正整数，D 仅维护和提供该值；M1 如何执行以及参数变化对既有预约的影响留 Phase 4。

签到码日期由注入 Clock 和显式 `CAMPUS_BUSINESS_ZONE` 计算，本地为 Asia/Shanghai；客户端不能提交任意日期。每日每房间一条记录，由 `(room_id,date)` 主键保护。代码为 SecureRandom 生成的 8 位 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` 字符，不含 I/O/0/1，不要求跨房间全局唯一。重复或并发 ensure 返回同一条记录且只产生一条创建审计。不提供同日轮换、QR 载荷、预约检查或实际签到。停用房间不能生成/读取有效码；学生不能取码。验证码和令牌均不得写入日志或审计。

D 修改与审计同事务。AuditEvent 包含 id、actorUserId、sourceService、action、targetType、targetId、requestId、occurredAt，没有输入正文、密码、JWT 或签到码。targetType 过滤为 `[A-Z_]{1,32}`；审计应用身份不可 UPDATE/DELETE，查询由权限控制。审计保存期限仍未定，当前不提供清理或导出机制。

## 私网内部接口（5 个操作）

以下请求只能在 backend 网络访问 `http://platform-service:8080`，公网/本地浏览器网关对 `/internal` 始终返回 404。Header 同样使用 `Authorization: Bearer <opaque service secret>`，但它是与用户 JWT 完全分开的秘密和安全链；用户 JWT 不能替代服务身份。

| 方法与路径 | 能力 | 调用方 |
|---|---|---|
| GET /internal/v1/rooms | 启用房间目录，buildingId 可选，按 ID 排序与分页，保留原始房间类型 | reservation-service、classroom-service、maintenance-service |
| GET /internal/v1/rooms/{id} | 读取房间主数据，包括 enabled | 三个服务身份 |
| GET /internal/v1/rooms/{roomId}/seats | 座位主数据分页；enabledOnly、hasPowerOutlet、seatId 可选筛选 | 三个服务身份 |
| GET /internal/v1/settings | 读取最大预约时长 | 三个服务身份 |
| POST /internal/v1/checkin-codes/verify | `{roomId,code}` → `{valid:boolean}` | 仅 reservation-service |

三个秘密分别对应 `PLATFORM_RESERVATION_TOKEN`、`PLATFORM_CLASSROOM_TOKEN`、`PLATFORM_MAINTENANCE_TOKEN`，必须独立且至少 32 个 UTF-8 字节。D 根据受验证的凭据判断调用方，不信任请求体自报 module。语法正确但房间不存在/停用、当日未生成码或不匹配均返回 200、valid=false；非法 ID 或不是合法 8 位格式的 code 返回 400。校验不生成记录、不泄露正确码，也不校验预约或用户归属；这些属于 M1 的实际签到阶段。

内部目录的 limit 为 1–100（默认 100），offset≥0；调用方逐页读取，不能把第一页当成完整数据。座位默认仍包含停用主数据，以保留原契约；`enabledOnly=true` 时只返回启用房间内的启用座位。`hasPowerOutlet=true/false` 按真实插座标记筛选，`seatId` 是房间内的精确正整数 ID；全部筛选在 SQL 的 LIMIT/OFFSET 前执行。禁用房间在 enabledOnly 模式返回空座位列表，未知房间返回 404。D 只提供主数据快照，不返回或判断 M1 占用状态。

通知和跨服务审计写入尚无可调用实现。后续应先明确调用身份、字段最小化、幂等/投递状态和保存政策，再增加有真实行为的契约，不能返回空成功表示已经送达或审计完成。
