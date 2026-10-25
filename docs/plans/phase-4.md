# Phase 4：M1 基础预约

用户在 Phase 3 交付后确认继续，本轮仅执行 Phase 4，完成后停止等待 Phase 5。分支 feature/phase-4-reservations 基于 1e11e5cffc0e4cd469de6d61a1227d7acf48aff9；每个可运行且已验证分项提交 GitHub，最终以堆叠草稿 PR 交人工 review。

## 具体方案（待人工审阅）

- M1 通过真实 HTTP 转发用户 JWT 到 D 现有 `/api/v1/me`，获得当前启用状态、auth_version 与权限；不共享签名秘密、不读 D 表。主数据与参数使用 M1 专属 opaque 凭据调用 D。D 不可用或响应不符合契约时失败关闭，不返回假数据。
- M1 使用 `/api/v1/reservation/rooms...` 查询与 `/api/v1/reservations...` 预约路径，不覆盖 D 的空间管理路径。目录包含 D 启用的房间及座位，按插座条件在 D SQL 分页前过滤，不擅自把教室座位永久排除而使未来 M2 整教室占用失去意义。
- 请求提供带 offset 时间，转换为 Instant；业务时区中分钟/秒/纳秒为 0，结束晚于开始，按 `[start,end)` 判断冲突，相邻区间可用。新建/再预约开始不得早于注入 Clock 的当前时刻，连续时长不得超过 D 当前参数快照（默认 4）；不私加未来预约天数或一人多座限制。查询可用性表示请求区间的快照，创建必须重新判冲突。
- 本人且 CREATED 的预约在开始前可取消；重复 CANCELLED 返回原结果，不重复事件，开始时及以后返回 409。历史为取消或已结束的区间，不提前写 COMPLETED/NO_SHOW。再预约仅本人历史，产生新 ID、重新读取主数据/参数并检查冲突；不复用旧占用来源。
- reservation 新 V2 仅创建 room_gate、seat_reservation、完整 11 字段 seat_occupancy 和 reservation_event。所有用户/房间/座位 ID 均为逻辑引用，不跨 schema 外键。占用状态用 ACTIVE/RELEASED，不复用预约状态；来源三元组唯一且释放后仍保留。源指定五类预约状态均保留枚举约束，当前只执行 CREATED/CANCELLED。
- D 校验后，M1 本地事务内懒创建 room_gate（ON CONFLICT DO NOTHING），再 SELECT FOR UPDATE；首次并发也必须取得锁，锁后重新检查 Clock。冲突覆盖所有 ACTIVE 来源，同座位或 seat_id=null 的整房间占用，并正确处理 end_at=null。创建与取消的预约/账本/事件同事务，取消只释放对应 M1 来源。
- M2/M3 的占用数据模型与查询兼容性用真实隔离数据库夹具验证，本阶段不开放其占用创建/释放接口，不宣称跨模块业务已经集成。checkin/violation/提醒/实际签到留 Phase 5，审批/工单及内部 room-blocks 留对应后续阶段。
- D 主数据校验是 HTTP 快照；不宣称它与 M1 提交具有跨服务原子性。停用/移动资源与已有预约的协调政策仍待后续审阅，不引入分布式事务或 D→M1 循环依赖来掩盖边界。

OQ-01/02/07/10/12/13/14 记录本阶段具体选择；签到窗口、自动完成/违约、来源释放契约、业务重试和组织/生命周期政策仍保留。schema/公共契约以真实代码与测试草稿 PR review，不自动合并。

## 分项与验证

1. D 内部目录与 M1 真实身份/主数据 HTTP、版本迁移、查询/创建/取消/历史/再预约；真实 PostgreSQL/HTTP/并发验证后提交。
2. 运行脚本、OpenAPI、需求/架构/伦理/手册和 Compose 升级；最终 GitHub SHA 重建并核对镜像、版本与真实业务流程后提交/交付。

关键验收：并发同座位仅一个成功，默认 4 小时且当前 D 设置有效，真实插座筛选，取消释放、本人历史可再预约，学生/其他用户越权被拒绝，D 故障不假成功；保留原 seed 与已发布迁移。

代码分项已实际验证：全 reactor 69 项通过后，加入 6 项 HTTP 边界测试并重跑 M1 最终子集 28 项，当前 75 个后端测试均通过（0 失败/错误/跳过）。真实 D JAR 子进程和 Testcontainers PostgreSQL 验证业务与首次并发锁；客户端错误响应夹具只验证边界失败，不替代真实 D 集成。OpenAPI 对应 D 33 操作和 M1 8 操作，正式规范校验通过。Nginx/Compose 配置验证通过；前端 lint、6 项测试及生产构建（含类型检查）通过。6 项客户端边界覆盖外部 D HTTP 状态、契约错误和必需布尔字段缺失/显式 null，失败关闭；首次实际原卷升级/网关业务验收已通过；最终干净 GitHub 版本仍待重建核对，不预填。

首个可运行代码分项已上传 GitHub：[6d98a875](https://github.com/Liinn0529/campus-resource-platform/commit/6d98a8752f9f5babb4e1f28b178d3dacaf798bf9)，[堆叠草稿 PR #5](https://github.com/Liinn0529/campus-resource-platform/pull/5) 基于 `feature/phase-3-platform`。后续发现的外部响应显式 null 布尔校验已修复，M1 最终子集 28 项通过；此修复及运行/文档分项继续按实际验证提交，不把尚未上传的改动写为已上传。

## 首次运行验收（2026-10-25）

五个应用镜像实际构建：`6d98a8752f9f5babb4e1f28b178d3dacaf798bf9-dirty` / `2026-10-25T14:30:16Z`。保留原 Phase 3 数据卷强制重建容器后，七容器 healthy，D 保持 V3、M1 从 V1 升至 V2、M2/M3 保持 V1；未执行 down -v。实际 Nginx 配置检查、Phase 3 公共层回归与 Phase 4 网关 smoke 全部通过，目录为4房间/40座位，验证默认4小时、插座、创建/409冲突、权限/本人隔离、取消释放、历史新ID再预约；最后取消本次新建预约并保留真实历史/事件。数据库脚本验证八账号/四schema边界、审计保护与账本/事件一致性；升级前后原演示实体计数与五用户密码指纹相同。

本次是首次联调构建，最终干净 GitHub 提交仍需重建镜像、重跑实际验证并核对四后端/Web元数据。完整实际命令、已发现并修复的测试问题、最终构建证据和未完成项由[本轮验证记录](../review/phase-4-validation.md)登记；不把首次 dirty 构建写成最终版本，不宣称 GitHub CI 已运行。
