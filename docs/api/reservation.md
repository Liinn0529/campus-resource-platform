# M1 预约、签到与违约 API（Phase 5 草稿）

本页对应 [OpenAPI 3.0.3](../../contracts/openapi/reservation.v1.json) 中的 10 个操作，依据实际 `ReservationController`、`LifecycleController`、业务服务、`TimeRules` 和 `PlatformClient`。schema、公共契约和跨模块选择等待人工 review；文档与静态校验不能替代业务测试、Compose 重建或 GitHub SHA 验收。实际运行结果以阶段验收记录为准，基础预约选择见 [Phase 4 方案](../plans/phase-4.md)；本轮增加真实签到、定时 NO_SHOW、违约和真实 D 站内提醒，不提供公网时间控制或调度执行接口。

## 地址、身份与响应

API 使用网关 origin 下的根路径 `/api/v1/...`，OpenAPI server 为相对地址 `/`。本地预期 origin 是 `http://127.0.0.1:18080`；Nginx 根路径转发与最终端到端验收须以实际运行记录确认。服务内部地址与健康接口见 [运行 API](runtime.md)。

所有操作需要 `Authorization: Bearer <D 签发的用户 JWT>`。M1 每次通过真实 HTTP 将用户凭证转发到 D 已有 `GET /api/v1/me`：D 验证签名、有效期、authVersion 和启用状态，并返回当前权限。D 中的禁用、权限变化和凭证失效会作用于下一次 M1 请求。M1 不接收 D 的 JWT 签名密钥、不直接读取 D 数据表。

M1 使用另一个专属 opaque 服务凭据调用 D 内部房间、座位和参数接口，该凭据也用于 D 内部验码和通知接收，不能登录 M1 用户 API。D 故障、超时或响应不符合契约时返回 `503`，不返回假成功或空目录掩盖失败。D 公共及内部契约见 [D API](platform.md)。

成功统一返回 `{"data":...,"requestId":"<uuid>"}`，错误返回 `{"code":"...","message":"...","requestId":"<uuid>"}`。M1 为每次请求生成 `X-Request-ID`，与响应体的 `requestId` 相同。错误没有 `data`；客户端应按 `code` 判断，勿依赖中文 `message` 文本。网关在请求到达 M1 前产生的错误以网关实际响应为准。

## 操作与权限

| 方法与路径 | 权限 | 成功响应 |
|---|---|---|
| `GET /api/v1/reservation/rooms` | `space:view` | `200`，`data: Room[]` |
| `GET /api/v1/reservation/rooms/{id}/seats` | `space:view` | `200`，`data: Seat[]` |
| `GET /api/v1/reservation/rooms/{id}/availability` | `space:view` | `200`，`data: Availability` |
| `POST /api/v1/reservations` | `reservation:create` | `201`，`data: Booking`，相对 `Location` |
| `GET /api/v1/reservations` | `reservation:view:self` | `200`，`data: Booking[]` |
| `GET /api/v1/reservations/{id}` | `reservation:view:self` | `200`，`data: Booking` |
| `POST /api/v1/reservations/{id}/cancel` | `reservation:create` | `200`，`data: Booking` |
| `POST /api/v1/reservations/{id}/rebook` | `reservation:create` | `201`，`data: Booking`，相对 `Location` |
| `POST /api/v1/reservations/{id}/checkin` | `reservation:create` + 本人 | `200`，`data: Booking` |
| `GET /api/v1/violations` | `reservation:view:self` | `200`，`data: Violation[]` |

路径 ID 和可选 `buildingId` 均为正整数。目录查询的 `limit` 默认 `100`，本人预约/违约列表默认 `50`，范围均为 `1..100`；`offset` 默认 `0`，不得为负数。列表的 `data` 直接为数组，不提供总条数或分页外层对象。

## 目录与可用性

房间查询支持可选 `buildingId`，只返回 D 当前启用的房间，按 ID 升序。`Room` 字段为 `id`、`buildingId`、`code`、`name`、`type`、`enabled`。`type` 为 `STUDY_ROOM` 或 `CLASSROOM`；两种启用房间均允许 M1 座位预约，不擅自增加只允许自习室的限制。

座位查询先确认房间启用，再读取该房间启用的座位，按 ID 升序。`Seat` 字段为 `id`、`roomId`、`code`、`hasPowerOutlet`、`enabled`。可选 `hasPowerOutlet=true` 只取有插座，`false` 只取无插座，不传则包含两者。楼宇和插座筛选由 D SQL 在分页前执行，M1 不在当前页取出后再过滤。

可用性查询必须提供带 offset 的 `startAt`、`endAt`，并支持相同的插座筛选和分页。例如查询参数中的 `+08:00` 必须编码为 `%2B08:00`；使用 URI 构造器编码一次即可，避免把已有 `%2B` 再编码成 `%252B`。

`Availability` 是一个对象：

```json
{
  "roomId": 1,
  "startAt": "2026-10-26T01:00:00Z",
  "endAt": "2026-10-26T02:00:00Z",
  "seats": [
    {"id": 1, "code": "A01", "hasPowerOutlet": true, "available": true}
  ]
}
```

示例只说明 JSON 形状，不是新增 seed 或真实可预约承诺。`seats` 是已筛选、分页的座位数组，每项只有 `id`、`code`、`hasPowerOutlet`、`available`。`available` 表示该请求区间没有覆盖此座位的 ACTIVE 占用，包括 `seat_id=null` 的整房间占用。接口不生成可选时间段、可预约计划或预留座位。允许查询过去区间，不应用创建时的最大预约时长；创建仍须重新检查当前状态和冲突。

## 新建、本人列表与详情

新建 JSON 只包含 `roomId`、`seatId`、`startAt`、`endAt`，四项必填；ID 必须为正整数，时间必须带 offset。M1 从认证身份确定 `userId`，客户端不能代选预约人。未知 JSON 字段返回 `400 VALIDATION_ERROR`。

```json
{
  "roomId": 1,
  "seatId": 1,
  "startAt": "2026-10-26T09:00:00+08:00",
  "endAt": "2026-10-26T10:00:00+08:00"
}
```

实际请求应选择仍在将来的时段及真实启用的资源。成功创建返回 `201`，`Location` 为 `/api/v1/reservations/{新ID}`。`Booking` 字段如下：

| 字段 | 含义 |
|---|---|
| `id`、`userId`、`roomId`、`seatId` | 正整数 ID；用户、房间、座位是逻辑引用，无跨 schema 外键 |
| `startAt`、`endAt` | 转换后的 UTC Instant，响应使用 `Z` |
| `status` | 数据约束保留 `CREATED`、`CANCELLED`、`CHECKED_IN`、`NO_SHOW`、`COMPLETED`；当前实际产生前四种；自动 COMPLETED 转换仍未实现 |
| `createdAt` | 创建的 UTC Instant |
| `cancelledAt` | CANCELLED 时刻；NO_SHOW 结束有效性但不伪装为 CANCELLED，该字段仍为 `null` |
| `rebookOf` | 原历史预约 ID；普通新建为 `null` |

本人列表支持 `view=current|history`，默认 `current`，按 `startAt DESC, id DESC` 排序。`current` 是状态为 `CREATED/CHECKED_IN` 且 `endAt > Clock` 当前时刻；`history` 是状态为 `CANCELLED/NO_SHOW/COMPLETED` 或 `endAt <= Clock` 当前时刻。时间过去不会自动将状态写成 `COMPLETED`。

本人列表、详情、取消、再预约、签到和违约始终按认证用户隔离，`SYSTEM_ADMIN` 也没有跨用户覆盖。详情、取消、再预约、签到中，不存在和属于其他用户的预约统一为 `404 RESERVATION_NOT_FOUND`，不泄露他人预约是否存在。

## 时间、取消与再预约规则

请求的 `OffsetDateTime` 转为 Instant，以显式配置的业务时区检查整点；本地配置为 `Asia/Shanghai`。开始与结束的分钟、秒、纳秒均须为 `0`，结束晚于开始，持续时间为完整小时。

区间统一为 `[start,end)`。例如 `09:00–10:00` 与 `10:00–11:00` 可相邻；重叠判断为既有 `start_at < 请求 end` 且既有 `end_at IS NULL OR end_at > 请求 start`。所有 ACTIVE 来源均参与判断，同座位或整个房间的占用会冲突。

新建和再预约要求 `startAt >= Clock` 当前时刻，不能超过本次从 D 获取的 `maxReservationHours` 正整数快照，初始参数为 `4` 小时；每次创建重新读取，获得房间锁后再次校验当前时刻。不会另外增加未来预约天数或一人同时只能约一座的限制。可用性查询只校验区间形状，不能替代创建时校验。

取消没有请求体，只允许本人 `CREATED` 且当前时刻严格早于 `startAt`。开始时刻或以后、其他状态均返回 `409 RESERVATION_CANNOT_CANCEL`。已经 `CANCELLED` 的记录再次取消返回 `200` 原记录，即使已过开始时间，也不重复释放占用、不重复写事件。取消事务同时抑制尚未发送或租约已过期的提醒，已发出的在途 HTTP 边界见下文。

再预约只允许本人记录为 `CANCELLED/NO_SHOW/COMPLETED` 终态之一，或原 `endAt <= Clock` 当前时刻；否则返回 `409 RESERVATION_NOT_HISTORY`。JSON 仅有必填 `startAt`、`endAt`。它沿用原房间和座位，重新校验当前 D 元数据、参数、时间及占用，产生新 ID，`rebookOf` 指向原 ID，建立新占用来源并写 `REBOOKED` 事件。原记录保留，旧占用来源不会复用。

## 房间锁与占用账本边界

M1 先通过 HTTP 校验 D 元数据与参数，随后进入本地事务。事务内先 `INSERT ... ON CONFLICT DO NOTHING` 懒创建 `room_gate`，再 `SELECT ... FOR UPDATE`，确保房间首次并发请求也取得锁；锁后重新读取 Clock 复核时间。网络请求不占用该房间锁。

创建在同一事务写预约、ACTIVE 占用和实际预约事件。取消在房间锁和记录锁内，仅释放三元组 `(source_module=M1, source_ref_id=当前预约ID, source_type=RESERVATION)` 对应的占用，并同事务写取消记录与事件；若账本不一致则回滚，不默默部分成功。

`seat_occupancy` 有独立的 `ACTIVE/RELEASED` 生命周期，保留完整 11 字段来源账本与释放历史；来源三元组唯一。冲突查询兼容 M1 预约、M2 整教室和 M3 维修的占用形状，以及 `end_at=null`。本阶段的 M2/M3 来源仅用于真实隔离数据库测试夹具，不能据此声称真实 M2/M3 业务或内部占用 HTTP 接口已经集成。

D 校验只是 HTTP 快照，不承诺 D 元数据修改与 M1 提交跨服务原子。资源停用/移动与已有预约的协调政策仍待后续人工审阅。本阶段不引入分布式事务、跨 schema SQL 或 D→M1 循环调用。

## 错误状态与验证边界

| HTTP | 典型 code 与含义 |
|---|---|
| `400` | `VALIDATION_ERROR`：格式、必填项、正 ID、分页、查询参数类型或未知 JSON 字段 |
| `401` | `UNAUTHORIZED`：缺少/无效/过期/失效的用户凭证，或真实 D 身份接口返回 401 |
| `403` | `FORBIDDEN`：当前权限不足 |
| `404` | `RESERVATION_NOT_FOUND`：不存在/非本人；`RESOURCE_NOT_FOUND`：D 元数据缺失；`NOT_FOUND`：M1 路由资源不存在 |
| `409` | `RESERVATION_TIME_CONFLICT`、`RESERVATION_CANNOT_CANCEL`、`RESERVATION_NOT_HISTORY`、`CHECKIN_NOT_OPEN`、`CHECKIN_CLOSED`、`CHECKIN_NOT_ALLOWED` 或 `DATA_CONFLICT` |
| `422` | `RESERVATION_HOUR_BOUNDARY`、`RESERVATION_INVALID_INTERVAL`、`RESERVATION_START_IN_PAST`、`RESERVATION_DURATION_EXCEEDED`、`RESOURCE_UNAVAILABLE` 或 `CHECKIN_CODE_INVALID` |
| `500` | `INTERNAL_ERROR`：内部错误或预约/账本不一致；事务内失败回滚 |
| `503` | `PLATFORM_UNAVAILABLE`：D 故障/超时/契约无效；`DATABASE_UNAVAILABLE`：数据库不可用；`RESERVATION_BUSY`：锁/事务超时；参数防御分支可能为 `PLATFORM_INVALID_RESPONSE` |

M1 还将已知路径不支持的方法返回为 `405 METHOD_NOT_ALLOWED`。详细操作只列各自适用的业务错误；语法正确但业务时间/资源规则不符属于 `422`，占用或状态冲突属于 `409`，外部 D 失败属于 `503`。

生产跨模块调用使用真实 HTTP。测试用隔离环境启动真实 D 子进程 JAR 并配置测试环境，不将 fixture 注册为生产 Spring Bean、不以生产 mock 替代 D。静态 OpenAPI 校验只能证明契约结构和引用有效，不能证明数据库事务、权限、真实 HTTP、并发或网关已通过。业务验证须按实际执行结果记录。

## 本人签到与时间边界

`POST /api/v1/reservations/{id}/checkin` 的 JSON 仅有必填 `code`，格式为8位 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` 字符。代码属于敏感输入，仅放在请求正文；未知字段返回400。M1 不保存代码，不在 Booking、checkin、事件、错误或日志中回显；D 只向有授权的房间管理身份提供今日码，用户不能通过签到接口取得正确码。

T 是预约开始 Instant，当前窗口方案为 `[T−15分钟,T+15分钟)`：开放边界包含，截止边界不包含。CREATED 且早于开放时间为409 `CHECKIN_NOT_OPEN`，恰好到达截止或以后为409 `CHECKIN_CLOSED`；CANCELLED/NO_SHOW 等状态为409 `CHECKIN_NOT_ALLOWED`。这是一份待人工 review 的具体政策，原文没有另外规定提前或补签到窗口。

M1 先读取本人预约并检查时间，再通过真实 D `POST /internal/v1/checkin-codes/verify` 验证该预约房间当前业务日期的码。合法格式但不匹配为422 `CHECKIN_CODE_INVALID`；D 故障、超时或成功响应无效均503 `PLATFORM_UNAVAILABLE`。D 调用在事务外，随后 M1 以 room_gate→预约行的固定顺序取得锁，重新读取 Clock、状态和精确 ACTIVE 来源账本，避免等待锁跨过 T+15 后仍签到。更新 CHECKED_IN、唯一 checkin、CHECKED_IN 事件和待发提醒抑制同事务提交。

成功返回200原结构 Booking；签到时间保存在 checkin 的 checked_in_at。已 CHECKED_IN 且有一致签到记录时，格式合法的重复请求即使已过窗口仍返回200原记录，不再次验码、不重复签到或事件。当前权限与本人隔离仍每次由后端验证，管理员没有代签到覆盖。缺失、他人预约统一404。

## NO_SHOW、占用释放与本人违约

后台在 `Clock >= T+15分钟` 且仍 CREATED 时执行 NO_SHOW。签到与此转换共享房间/预约锁；截止点的签到请求会被拒绝。转换、精确 M1 来源占用释放、唯一 violation 和 NO_SHOW 事件在一个事务中完成。账本缺失或字段不匹配时整体回滚，保留真实失败，不静默生成只有一半的违约；后台不因一个损坏账本跳过同批其他房间。

最终持久状态是 NO_SHOW，含义为预约失效；不同时写 CANCELLED 或伪造用户取消时刻。自动事件 actor_user_id 为空，不冒充学生操作。取消/签到过的预约不被写成 NO_SHOW。原占用来源与释放时间保留，后续查询可观察真实可用性；不对 M2/M3 来源做代释放。NO_SHOW 已属于本人历史，即使原 endAt 尚未来到，也可按再预约规则新建未来预约；既有违约和旧来源保留。

`GET /api/v1/violations` 只返回本人最小记录，字段为 `id`、`reservationId`、`reason`（固定 NO_SHOW）、`occurredAt`。按 occurredAt DESC、id DESC 排序，limit默认50、1–100、offset非负。SYSTEM_ADMIN 同样只见本人记录。当前没有积分、封禁、处罚规则、批量导出或跨用户管理查询；保存期限、删除、申诉与可见性扩展仍待审阅。

## 提醒调度、投递与恢复

默认每30秒扫描到期业务，另一个独立任务每30秒处理投递；调度线程池为2，D 投递等待不占用 NO_SHOW 扫描任务。服务端 Clock 决定到期判断，不以精确时间相等判断。实际处理可能受扫描间隔、停机、房间锁等待与批次影响，不能声称在指定秒已经送达。每批最多100个候选，后续扫描继续处理。生产默认开启，确定性测试关闭自动调度并直接调用相同业务方法；没有公网调度或改时钟接口。

| kind | 可排队/尝试发送的窗口 | 迟到处理 |
|---|---|---|
| CHECKIN_FIRST | `[T−15分钟,T+10分钟)` | 到第二窗口后跳过过期首条 |
| CHECKIN_SECOND | `[T+10分钟,T+15分钟)` | 到截止后跳过，先执行 NO_SHOW |

只有 CREATED 预约进入提醒队列。已取消或已签到的预约不产生后续提醒。停机恢复时选择当前有效窗口，超过截止的预约直接执行 NO_SHOW，不补发陈旧提醒。持久 `(reservation_id,kind)` 唯一键避免重复排队；排队/领取都检查房间/预约状态。取消、签到和 NO_SHOW 将未发或租约已过期的提醒标为 EXPIRED。

投递状态为 PENDING→SENDING→ACCEPTED，另有 EXPIRED/FAILED。领取时持久化随机 claim_token、30秒 claimed_until 租约与 attempt_count；实际 D HTTP 在本地事务及房间锁之外执行。进程在领取后退出时，其他 worker 等租约到期才恢复；回执更新必须匹配本次 claim_token，旧 worker 不能覆盖新领取者。

每条提醒最多3次总尝试，只重试网络错误和 HTTP 5xx，前两次失败分别等待30、60秒；HTTP 4xx、重定向或格式/内容不符的成功回执进入 FAILED，不自动重试。到期仍未成功则 EXPIRED。最终尝试后回执未知保留失败原因，不能把未知写为成功。FAILED 的人工恢复操作仍未提供。

每次尝试使用同一个 `reservation-reminder:<提醒ID>`，通过 M1 独立 opaque 凭据调用真实 D `POST /internal/v1/notifications`。D 首次持久化返回201，完全相同重放返回200原 ID，不重复消息或审计；来源内容不同返回409。M1 校验回执的 ID、来源、接收人、预约、房间、kind 和各时间均匹配，才记录 notification_id/accepted_at 及 ACCEPTED。

D `GET /api/v1/notifications` 是真实本人站内收件箱，详见[平台契约](platform.md#站内提醒收件箱1-个公开操作)。ACCEPTED 只证明 D 持久接收，不代表用户已读、邮件、短信或推送送达。收件箱保留原窗口和真实接受时间，未提供任意消息正文或签到码。完整业务 Web 页面留 Phase 9。

跨服务请求已经发出后，签到/取消无法与 D 接收进行分布式原子撤回；活跃 SENDING 领取可能仍取得 ACCEPTED。系统在领取前重新检查资格、抑制可撤回队列、使用稳定来源去重，并明确保留在途事实。当前不引入分布式事务或 D→M1 循环调用来假装消除这段边界。

Phase 5 保留 CREATED、CANCELLED、CHECKED_IN、NO_SHOW 的真实转换与历史；自动 COMPLETED、完整业务 Web、审批、工单及 M2/M3 room-blocks 写接口仍留后续。第一版签到不采集 GPS、人脸、蓝牙或 NFC 数据。静态契约和调度实现不代表本轮 Docker/CI 或人工 review 已通过，结果应见实际阶段验收记录。
