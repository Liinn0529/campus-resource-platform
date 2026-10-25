# M1 基础预约 API（Phase 4 草稿）

本页对应 [OpenAPI 3.0.3](../../contracts/openapi/reservation.v1.json) 中的 8 个操作，依据实际 `ReservationController`、`ReservationService`、`TimeRules` 和 `PlatformClient`。schema、公共契约和跨模块选择等待人工 review；文档与静态校验不能替代业务测试、Compose 重建或 GitHub SHA 验收。实际运行结果以阶段验收记录为准，具体选择见 [Phase 4 方案](../plans/phase-4.md)。

## 地址、身份与响应

API 使用网关 origin 下的根路径 `/api/v1/...`，OpenAPI server 为相对地址 `/`。本地预期 origin 是 `http://127.0.0.1:18080`；Nginx 根路径转发与最终端到端验收须以实际运行记录确认。服务内部地址与健康接口见 [运行 API](runtime.md)。

所有操作需要 `Authorization: Bearer <D 签发的用户 JWT>`。M1 每次通过真实 HTTP 将用户凭证转发到 D 已有 `GET /api/v1/me`：D 验证签名、有效期、authVersion 和启用状态，并返回当前权限。D 中的禁用、权限变化和凭证失效会作用于下一次 M1 请求。M1 不接收 D 的 JWT 签名密钥、不直接读取 D 数据表。

M1 使用另一个专属 opaque 服务凭据调用 D 内部房间、座位和参数接口，该凭据不能登录 M1 用户 API。D 故障、超时或响应不符合契约时返回 `503`，不返回假成功或空目录掩盖失败。D 公共及内部契约见 [D API](platform.md)。

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

路径 ID 和可选 `buildingId` 均为正整数。目录查询的 `limit` 默认 `100`，本人预约列表默认 `50`，范围均为 `1..100`；`offset` 默认 `0`，不得为负数。列表的 `data` 直接为数组，不提供总条数或分页外层对象。

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
| `status` | 数据约束保留 `CREATED`、`CANCELLED`、`CHECKED_IN`、`NO_SHOW`、`COMPLETED`；Phase 4 只产生前两种 |
| `createdAt` | 创建的 UTC Instant |
| `cancelledAt` | 取消时刻，未取消为 `null` |
| `rebookOf` | 原历史预约 ID；普通新建为 `null` |

本人列表支持 `view=current|history`，默认 `current`，按 `startAt DESC, id DESC` 排序。`current` 是状态为 `CREATED/CHECKED_IN` 且 `endAt > Clock` 当前时刻；`history` 是状态为 `CANCELLED/NO_SHOW/COMPLETED` 或 `endAt <= Clock` 当前时刻。时间过去不会自动将状态写成 `COMPLETED`。

本人列表、详情、取消和再预约始终按认证用户隔离，`SYSTEM_ADMIN` 也没有跨用户覆盖。详情、取消、再预约中，不存在和属于其他用户的预约统一为 `404 RESERVATION_NOT_FOUND`，不泄露他人预约是否存在。

## 时间、取消与再预约规则

请求的 `OffsetDateTime` 转为 Instant，以显式配置的业务时区检查整点；本地配置为 `Asia/Shanghai`。开始与结束的分钟、秒、纳秒均须为 `0`，结束晚于开始，持续时间为完整小时。

区间统一为 `[start,end)`。例如 `09:00–10:00` 与 `10:00–11:00` 可相邻；重叠判断为既有 `start_at < 请求 end` 且既有 `end_at IS NULL OR end_at > 请求 start`。所有 ACTIVE 来源均参与判断，同座位或整个房间的占用会冲突。

新建和再预约要求 `startAt >= Clock` 当前时刻，不能超过本次从 D 获取的 `maxReservationHours` 正整数快照，初始参数为 `4` 小时；每次创建重新读取，获得房间锁后再次校验当前时刻。不会另外增加未来预约天数或一人同时只能约一座的限制。可用性查询只校验区间形状，不能替代创建时校验。

取消没有请求体，只允许本人 `CREATED` 且当前时刻严格早于 `startAt`。开始时刻或以后、其他状态均返回 `409 RESERVATION_CANNOT_CANCEL`。已经 `CANCELLED` 的记录再次取消返回 `200` 原记录，即使已过开始时间，也不重复释放占用、不重复写事件。

再预约只允许本人记录为 `CANCELLED` 或原 `endAt <= Clock` 当前时刻；否则返回 `409 RESERVATION_NOT_HISTORY`。JSON 仅有必填 `startAt`、`endAt`。它沿用原房间和座位，重新校验当前 D 元数据、参数、时间及占用，产生新 ID，`rebookOf` 指向原 ID，建立新占用来源并写 `REBOOKED` 事件。原记录保留，旧占用来源不会复用。

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
| `409` | `RESERVATION_TIME_CONFLICT`、`RESERVATION_CANNOT_CANCEL`、`RESERVATION_NOT_HISTORY` 或 `DATA_CONFLICT` |
| `422` | `RESERVATION_HOUR_BOUNDARY`、`RESERVATION_INVALID_INTERVAL`、`RESERVATION_START_IN_PAST`、`RESERVATION_DURATION_EXCEEDED`、`RESOURCE_UNAVAILABLE` |
| `500` | `INTERNAL_ERROR`：内部错误或预约/账本不一致；事务内失败回滚 |
| `503` | `PLATFORM_UNAVAILABLE`：D 故障/超时/契约无效；`DATABASE_UNAVAILABLE`：数据库不可用；`RESERVATION_BUSY`：锁/事务超时；参数防御分支可能为 `PLATFORM_INVALID_RESPONSE` |

M1 还将已知路径不支持的方法返回为 `405 METHOD_NOT_ALLOWED`。详细操作只列各自适用的业务错误；语法正确但业务时间/资源规则不符属于 `422`，占用或状态冲突属于 `409`，外部 D 失败属于 `503`。

生产跨模块调用使用真实 HTTP。测试用隔离环境启动真实 D 子进程 JAR 并配置测试环境，不将 fixture 注册为生产 Spring Bean、不以生产 mock 替代 D。静态 OpenAPI 校验只能证明契约结构和引用有效，不能证明数据库事务、权限、真实 HTTP、并发或网关已通过。业务验证须按实际执行结果记录。

Phase 4 不开放签到、违约、提醒、实际签到码流程、审批、工单或 M2/M3 `room-blocks` 写接口；这些归属后续阶段。本页与 OpenAPI 不代表 Phase 5 或 Phase 8 已开始或完成。
