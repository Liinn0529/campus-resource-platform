# ADR-001：服务边界与数据所有权

- 状态：**Proposed / 待人工 Review**
- 日期：2026-10-20
- 阶段：Phase 0 起草；Phase 2/3/4/5/6补充实施范围
- 决策来源：[CODEX_EXECUTION_PROMPT.md](../requirements/CODEX_EXECUTION_PROMPT.md)第 4–10、16–18、24、29–37 节
- 关联：[ARCHITECTURE.md](../architecture/ARCHITECTURE.md)、[DATA_OWNERSHIP.md](../architecture/DATA_OWNERSHIP.md)、[REQUIREMENTS.md](../requirements/REQUIREMENTS.md)、[TRACEABILITY.md](../requirements/TRACEABILITY.md)

本 ADR 记录源文档指定的边界，Proposed 表示尚待人工 review，阶段执行或上传代码不自动批准架构。Phase0–5已交付并保留历史证据；本轮Phase6追加M2 V2申请审批与持久同步、D本人审批范围，36/10/6操作。130项及首轮真实运行通过，记录见[Phase6验证](../review/phase-6-validation.md)。M3、M1占用集成、完整Web、CI/CD和部署留后续。

## 背景

平台包含公共层 D、自习座位预约 M1、整教室预约审批 M2 和物业工单 M3。M2 的整房间批准与 M3 的维修会同时影响 M1 座位可用性。如果各模块直接写共享座位状态，来源、并发与失败恢复无法清晰归属。

源要求采用 Monorepo、前后端分离、4 个后端服务、REST API、单 PostgreSQL 实例及独立 schema；禁止直接访问其他模块数据库、分布式事务、Redis 锁与非必要基础设施。此 ADR 在这些既定要求内固定责任边界，保留完整目标架构；实施范围由各阶段授权与验收记录约束。

## 决策

1. **D 拥有身份与主数据。** `platform-service` 管理用户、登录、RBAC、角色权限、楼宇/房间/座位、参数、签到码、通知接口和操作审计。D 不拥有预约占用账本。
2. **M1 统一管理占用。** `reservation-service` 管理预约、签到结果、提醒、违约、历史及占用账本。`room_gate` 和 `seat_occupancy` 均归 M1；M2/M3 不得直接修改其表或 D 的座位状态来表达占用。
3. **M2/M3 保持独立业务责任。** `classroom-service` 拥有申请审批和对应事件，`maintenance-service` 拥有工单维修验收和对应事件；它们通过 M1 内部 HTTP API 创建、释放房间占用。
4. **单实例不等于共享访问。** 四服务分别使用 `platform`、`reservation`、`classroom`、`maintenance` schema，各有独立 app 与 migrator 账号，仅能访问本 schema。迁移账号拥有本 schema，应用账号无 DDL 或 Flyway 历史表访问权限。禁止跨 schema SQL、跨服务直接读写表和跨服务外键；跨服务实体仅以逻辑 ID 引用，通过 HTTP 校验和使用数据。
5. **M1 以房间为事务互斥单元。** 所有影响房间座位状态的操作，在 M1 本地事务中锁定 `room_gate` 对应行，再检查并改变占用。创建/取消座位预约、创建/释放教室占用、创建/释放维修占用都遵守同一规则，不引入 Redis 锁。
6. **跨服务采用显式同步状态与有界重试。** M2 经 `APPROVED_PENDING_SYNC` 才到 `APPROVED`；M3 经 `BLOCKING_ROOM` 才到 `IN_PROGRESS`。仅网络错误和 5xx 可重试，4xx 不重试；Phase6具体草案为最多3次总尝试、30/60秒退避，仍待人工review，耗尽进入 `SYNC_FAILED`。不用分布式事务掩盖部分成功。
7. **占用创建有幂等身份。** 唯一键为 `(source_module, source_ref_id, source_type)`，一致请求返回已有结果，冲突内容返回 409。源指定 DELETE 路径缺少 `sourceType`，须人工 Review 解决后才固化释放实现，不在本 ADR 暗自修改契约。
8. **业务判定留在确定性后端。** 权限由后端校验，认证采用密码/BCrypt/JWT；第一版不接学校统一身份认证。后续可选 Agent 只能调用公开业务 API，不直连数据库或决定审批、权限、预约、座位与工单状态。

9. **提醒采用各自持久事实。** M1 拥有 reminder/outbox 和投递尝试，D 拥有用户站内 notification；稳定 M1 来源键实现幂等接收。D 入箱与接收审计同事务，ACCEPTED 仅表示持久接收，不能冒充邮件/已读。发送不持有 room_gate，独立30秒扫描/发送、调度池2；claim/lease、多次恢复和3次总尝试形成明确中间状态，网络/5xx退避30/60秒，4xx/坏契约终止。已在途请求与签到不能跨服务原子撤回。
10. **签到与违约采用本地确定性转换。** 本人 `[T−15,T+15)` 经真实 D 验码，锁后复核 Clock；签到、唯一结果和事件同事务。T+15 仍 CREATED 写 NO_SHOW、唯一违约/系统事件并释放精确占用；正常 COMPLETED 未定，不私加处罚。违约本人可见，保存期限待审。

## 已排除的选择

| 选择 | 排除依据 |
|---|---|
| 四模块共享账号、表或跨 schema JOIN | 违反源指定服务所有权和 HTTP 边界 |
| D 集中持有所有占用，或 M2/M3 各自修改座位状态 | 混淆主数据、业务状态及 M1 占用真值，不符合源设计 |
| 只用 `seat.status` 表达全部可用性 | 无法满足按来源、时段记录的占用账本要求 |
| 分布式事务或无限重试 | 源禁止；不符合显式同步失败与有界恢复要求 |
| Redis lock、Kafka、服务发现/Service Mesh、BPMN 等 | 源禁止首版 Redis 及非必要基础设施，并已指定 PostgreSQL 房间行锁和固定审批链 |
| 通过生产 mock、固定成功响应宣称集成完成 | 违反真实运行与真实验证要求 |

这些选择并非本次进行了性能实验后得出的结论；排除依据为源文件明确约束。

## 影响与代价

- 边界明确：D 提供主数据，M1 是占用唯一所有者，M2/M3 是自身工作流唯一所有者，避免多个模块同时维护相互矛盾的座位真值。
- 部署规模受控：四个服务共用一个 PostgreSQL 实例，首版以 Docker Compose 运行，后续部署至华为 ECS/RDS/SWR；同实例仍有共享故障域，不据此声称实现高可用。
- 房间内写操作串行化：同房间不同座位的写入也受同一锁约束。该取舍来自源要求，应以真实并发测试验证，不能提前宣称吞吐指标。
- HTTP 引入部分成功：调用超时可能发生在 M1 已提交之后。幂等、同步状态、错误分类和恢复流程必须真实验证，不能只 mock 被测逻辑。
- 数据库不提供跨服务参照完整性：逻辑 ID 的有效性、主数据停用/删除和历史可追踪必须由服务契约和业务政策共同处理。
- 状态机和接口须独立演进：schema、公共契约和跨模块变更都需人工 Review；Flyway 只向前，发布后的迁移不得修改。

## 待人工确认及验收

| 待确认项 | 必须明确的内容 |
|---|---|
| 释放契约 | DELETE 两段标识如何唯一对应三元键；重复释放、内容冲突、引用重用与释放失败状态 |
| 重试语义 | “最多 3 次”包含首次调用与否、调度间隔、持久化恢复、4xx 的最终处理及人工恢复入口 |
| 资源生命周期 | D 资源创建/停用/删除与 M1 `room_gate`、已存在预约和占用之间的协调 |
| 时间与业务政策 | Phase 4 的显式时区/整点、半开区间、当前参数、开始前取消/历史再预约已有待审方案；Phase 5 签到窗口、NO_SHOW、站内通知已有具体草案；Phase6审批每级默认24小时并封顶开始形成待审草案；正常完成/维修超时仍未定 |
| 安全和数据最小化 | Phase 3 的 D JWT/独立内部凭据/房间范围已有具体待审方案；其他模块接入、部门范围、站内通知已有持久接收/有界失败恢复草案；通用跨服务审计与保留/删除周期仍待明确 |

Phase 0 的历史验收为文档存在、需求可追踪、边界有文字说明、未提前实现业务；Phase 1 已有运行端点和容器验收记录。Phase 2 使用真实 PostgreSQL/Testcontainers 验证迁移、schema 权限隔离、最小 seed 及幂等。Phase 3 的 49 项后端测试和实际 Compose/HTTP 验收覆盖 D 身份权限、范围、日码及审计；Phase 4 真实 D JAR/HTTP 与 PostgreSQL 已验证 M1 首次锁行竞争、同座位仅一成功、取消/再预约、权限和三来源/开放结束冲突；不替代 M2/M3 到 M1 的占用写接口/幂等业务 E2E 或生产部署验证。

人工 Review 通过后方可记录 ADR 接受状态；本轮Phase6完成后须停止，等待用户明确确认再进入Phase7。文档的 Proposed 状态不能被阶段执行授权、测试通过、Git 提交或上传仓库自动替代。

## Phase 6 当前实现与明确依赖

M2追加V2四表classroom_request、approval_step、approval_action、classroom_event；D仍V4/M1 V3/M3 V1，旧迁移不改写。D36/M1 10/M2 6操作。D本人room-scopes要求当前classroom:approve，ROOM_ADMIN/DEPARTMENT_ADMIN可显式分配房间；两角色均移除则清除范围，D空间维护仍按原ROOM_ADMIN/系统管理员规则，部门范围不扩大空间或取码权限。不新增部门模型或seed用户。

M2通过真实D HTTP验证身份/权限/范围和启用CLASSROOM，无D签名秘密/跨schema SQL。SUBMITTED等待ROOM_ADMIN，第一次批准APPROVING激活DEPARTMENT_ADMIN，第二次仅APPROVED_PENDING_SYNC。两级要求当前对应角色/权限/范围，禁止自批及同一人两级，无SYSTEM_ADMIN隐式覆盖。每级激活默认24小时并封顶startAt，记录锁后复核Clock；未完成审批到期AUTO_CANCELLED。只允许本人取消SUBMITTED/APPROVING且期限前。

同步通过独立M1_CLASSROOM_TOKEN真实POST M1 room-blocks，来源classroom-request:ID；30秒持久lease/令牌、3次总尝试、网络/5xx退避30/60秒、4xx/坏回执终止。两个30秒任务/池2，HTTP不持本地锁，迟到回执核对状态/令牌。当前真实M1接口属于Phase8尚未提供，直接403正确记SYNC_FAILED，不制造APPROVED或M1占用。客户端成功回执分支仅由明确外部响应夹具验证；批准后取消/到期释放/未知提交补偿与真实封房仍留Phase8。

完整clean reactor130项通过，运行升级与最终SHA证据见[Phase6验收](../review/phase-6-validation.md)。早期Phase0–5文字保留历史，不能把旧阶段未实现描述当作当前M2不存在，也不能把当前M2审批当作Phase8已完成。Phase6结束等待Phase7，完整Web在Phase9。
