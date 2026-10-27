# 后端运行、预约与签到提醒验证

需要 Docker、Git 和 OpenSSL；API 检查另需 Python 3.9+。在仓库根目录执行：

```sh
./scripts/test-backend.sh
```

脚本使用 Maven 3.9.11 / Java 21 容器，注入真实 Git SHA、dirty 标记与 UTC 时间，执行四模块 `mvn clean verify`。测试通过 Testcontainers PostgreSQL、真实 bootstrap/Flyway 和随机端口 HTTP，不 mock 被测业务；Docker 不可用即失败。M1集成启动已打包真实D JAR，M2集成启动真实D和M1 JAR；单独测试业务模块前应先完整reactor生成依赖JAR。当前Phase7完整clean reactor153项（D51/M1 49/M2 28/M3 25），零失败/错误/跳过；首轮真实运行通过，最终干净SHA证据另见仓库外交付报告。数量、失败和修复见[Phase7验收](../review/phase-7-validation.md)及实际Surefire。

Docker socket 默认挂载 `/var/run/docker.sock`；自定义安装可设置 `TESTCONTAINERS_SOCKET_PATH`。测试覆盖数据库账号隔离、迁移历史与审计保护、精确 local seed、非 local 无演示数据，以及登录/JWT、实时权限、房间范围、最后系统管理员保护、主数据修改和每日签到码；M1 新增整点/参数、同座位首次并发、相邻区间、多来源/开放结束冲突、本人隔离、取消/再预约和账本回滚。已发布迁移不改写：公共层保持V4、预约服务保持V3，M2保持V2四表，M3本轮追加V2；所有旧迁移不改写。

## 启动服务

推荐通过 Compose 提供私有凭据和内部数据库网络。只重建并启动公共层时：

```sh
./scripts/init-env.sh
./scripts/build.sh platform-service
docker compose up -d --wait --wait-timeout 180 platform-service
```

已有 Phase 2 数据库在公共层启动时自动执行新的 V2/V3，不需要删除数据卷。Phase 1 旧卷缺少服务账号，应先阅读[本地手册](local-development.md#已有-volume-与空库重放)。其他服务使用对应 Compose 服务名；没有宿主机后端或数据库端口，公开 API 通过网关访问。

宿主机直接运行 JAR 需要 Java 21，并自行提供可达的私网 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`FLYWAY_USERNAME` 和 `FLYWAY_PASSWORD`；JDBC URL 指定对应 `currentSchema`。不能把管理员或迁移身份用于业务数据源，不能将 Compose 内部 `postgres` 主机名直接当作宿主机地址。

公共层在所有 profile 中还要求 `PLATFORM_JWT_SECRET`、`PLATFORM_RESERVATION_TOKEN`、`PLATFORM_CLASSROOM_TOKEN`、`PLATFORM_MAINTENANCE_TOKEN` 和 `CAMPUS_BUSINESS_ZONE`。JWT 秘密至少 32 个 UTF-8 字节，三个内部秘密各至少 32 字节且彼此不同；本地生成器分别生成随机值。秘密仅放在私有运行环境，不写入命令行、源码或日志。非 local 启动没有默认管理员；应通过审阅过的初始化/运维流程配置真实管理员，不能为方便登录开启演示 seed。

## 登录与平台 API

公开入口为 `http://127.0.0.1:18080/api/v1`。`POST /auth/login` 接收用户名和密码，返回短期 access JWT；`GET /me` 返回当前身份。默认 issuer 为 `https://campus-resource.local/platform`，audience 为 `campus-web`，固定 HS256、15 分钟有效期，无 refresh token。issuer 是校验标识，不要求访问该域名发现密钥。每次请求重新读取数据库中的启用状态、auth_version 和权限；停用、改密和撤权不会等待旧 JWT 自然过期。

本地五个演示用户名为 `demo_admin`、`demo_student_01`、`demo_student_02`、`demo_room_admin`、`demo_property_staff`，初始密码来自私有 `.env` 的 `DEV_SEED_PASSWORD`。修改这个环境值不会重设已有用户密码。使用下列脚本完成登录和 API 验证，不把 token、密码或签到码输出到终端：

```sh
python3 scripts/smoke-phase-3.py --expected-commit "$(git rev-parse HEAD)"
```

如构建时有未提交修改，应传构建日志中的 `<SHA>-dirty`。端口修改后另加 `--url http://127.0.0.1:<port>`。脚本读取私有 `.env`；不要用带明文密码的 shell 历史、`curl -v` 或整段响应日志代替这一步。

管理和读取的具体方法、请求字段、错误码见[平台 API](../api/platform.md)与 [OpenAPI JSON](../../contracts/openapi/platform.v1.json)。系统管理员管理用户/角色、全局空间、参数和审计；房间管理员只能维护明确授权房间及其座位、读取对应签到码，无范围默认拒绝。学生可以读取启用空间和本人身份，不能通过直接调用 API 绕过管理限制。当前没有业务管理页面，不以隐藏前端按钮作为权限控制。

不提供用户或空间硬删除。用户可停用，空间可修改启用状态；涉及未来预约和历史引用的删除/停用协调属于后续阶段。Phase 5 的真实签到与站内提醒见后文；M2申请审批本轮已实现，M3工单本轮已实现，邮件渠道和完整业务Web尚未实现。

## M1 基础预约 API

M1 公开目录为 `/api/v1/reservation/rooms`，预约为 `/api/v1/reservations`，基础8操作及本轮签到/本人违约2操作的字段见[预约契约](../api/reservation.md)和 [OpenAPI](../../contracts/openapi/reservation.v1.json)。请求使用 D 登录签发的用户 JWT；M1 每次转发到 D `/api/v1/me` 读取当前身份及权限，目录/参数另用专属服务凭据读取。M1 只持有 `PLATFORM_BASE_URL`、`PLATFORM_RESERVATION_TOKEN` 与 `CAMPUS_BUSINESS_ZONE`，不共享 D 的 JWT 签名秘密。Compose 自动配置内部 D origin 和健康依赖；D 超时、故障或无效响应返回 503。

请求时间必须带 offset，在配置业务时区符合整点，区间使用 `[start,end)`。新建和再预约读取本次 D 最大时长（默认 4 小时），开始不能在过去；本人 CREATED 预约仅开始前可取消，重复取消返回原结果。其他人的预约对任何角色均为 404，系统管理员没有代订/代取消覆盖。历史再预约保留原记录并新建来源；正常自动完成仍未定，本轮签到和违约见下节。

```sh
python3 scripts/smoke-phase-4.py --expected-commit "$(git rev-parse HEAD)"
```

该脚本先执行 Phase 3 回归，再通过真实网关验证目录/插座、创建、409 冲突、5 小时拒绝、学生/管理角色隔离、取消释放和新 ID 再预约。它最后取消本次创建的全部预约，保留真实 CANCELLED 历史、占用释放和事件，不插入额外 seed 或删除业务历史。`--evidence <path>` 可保存不含凭据的证据 JSON；失败退出非零。M2/M3 来源冲突目前仅由真实隔离数据库夹具验证，不能据此称 room-blocks HTTP 集成已完成。

## Phase 5 签到、提醒与违约

`POST /api/v1/reservations/{id}/checkin` 输入8位房间当日码，使用本人现有 `reservation:create` 权限。窗口 `[T−15min,T+15min)`，真实 D HTTP 验码后在 room_gate→预约锁内复核时间/状态；已签到幂等返回，码不保存到 M1 或日志。到 T+15 仍 CREATED 的预约由扫描写 NO_SHOW、唯一违约/系统事件并精确释放 M1 来源；失败同事务回滚。`GET /api/v1/violations` 与 D `GET /api/v1/notifications` 均仅本人，分别使用 `reservation:view:self`，管理员不能借此查看其他用户。

M1 持久提醒/outbox 状态为 PENDING/SENDING/ACCEPTED/EXPIRED/FAILED，D 幂等站内接收成功后保存通知ID与时间。ACCEPTED 只表示持久入箱，不是邮件送达或已读。默认独立扫描/发送任务各30秒、批量100，调度池2；30秒claim lease，最多3次总尝试，网络/5xx退避30/60秒，4xx/无效响应不重试。首轮有效 `[T−15,T+10)`、次轮 `[T+10,T+15)`；停机恢复跳过失效提醒，先处理已截止 NO_SHOW。签到/取消抑制待发项，已在途请求不能跨服务原子撤回。

调度默认启用。隔离测试以 `campus.reservation.scheduler-enabled=false` 禁用后台抢跑，直接调用注入 Clock 的真实生命周期服务验证全部节点；无生产 HTTP 调时/强制扫描端点。运行频率由 `campus.reservation.scan-delay-ms` 配置，默认30000；调度线程池为2。生产系统时钟不因测试需要被修改。

```sh
python3 scripts/smoke-phase-6.py --expected-commit "$(git rev-parse HEAD)"
```

当前实现与首次运行脚本验收通过，实际执行结果由[Phase 5 记录](../review/phase-5-validation.md)登记。固定时点和恢复/并发测试使用可注入 Clock 与真实 D/PostgreSQL，不依赖等候15分钟；实际开发卷 smoke 只验证当前真实时刻适用流程，不通过手改生产预约时间冒充调度验收。正常 COMPLETED、补签到、处罚和保留期限不擅自补全；完整 Web/E2E 留 Phase 9/10。

## 数据库与运行端点检查

```sh
python3 scripts/check-database.py --phase6
python3 scripts/smoke-phase-1.py --platform-api --expected-commit "$(git rev-parse HEAD)"
```

数据库脚本针对当前 Compose 库检查八个 TCP 身份、schema owner、D V4/M1 V3/其他 V1、DDL/历史限制、演示基线及预约/占用/事件一致性；具体参数见脚本 `--help`。运行脚本核对七容器健康、真实 SHA/时间、只读运行接口、隐藏路径及 Web 资源，不等同于全部业务验收。结果另记于阶段验证报告，不由运行手册预先宣称。

## Phase6接口验证记录（历史）

M2六操作见[教室API](../api/classroom.md)，D新增本人审批范围。M2使用D和M1两种独立opaque凭据，Compose配置PLATFORM_BASE_URL/PLATFORM_CLASSROOM_TOKEN及M1_BASE_URL/M1_CLASSROOM_TOKEN；init-env保留旧凭据并补齐新值，不输出秘密。现有D4/M1 V3保留，M2从V1升V2、M3为V2，保留卷升级，无需删除数据。

运行 `python3 scripts/smoke-phase-6.py --expected-commit <实际构建SHA>` 先回归Phase5，再验证真实D范围、提交/两级审批/拒绝/撤销/撤权与实际M1 403→SYNC_FAILED；临时为现有第二学生赋部门角色/范围后恢复，不扩seed。不会声称真实封房成功。定时超时/成功回执/重试边界另由真实HTTP/PostgreSQL+Clock或明确外部响应夹具验证。`check-database.py --phase6`核对M2四表一致性和没有假APPROVED/M2占用的当前阶段基线。

本地后端测试使用容器内部Linux工作区，公共Git源码归档不含.env；导出JAR、Surefire和JaCoCo XML，避免宿主同步class文件错误。完整130项通过，运行升级和最终SHA实际证据见[Phase6验收](../review/phase-6-validation.md)。完整角色Web/E2E留Phase9/10，M1占用/释放/恢复留Phase8；完成Phase6后停止等待Phase7。

## Phase7工单运行验收

当前D4/M1 V3/M2 V2/M3 V2；原卷自动追加M3 V2，无需删除数据。运行前执行init-env补齐独立M1_MAINTENANCE_TOKEN并保留旧值。D身份/资格与M1调用使用不同凭据，内部接口不经网关公开。工单公开路径/api/v1/maintenance-tickets，具体角色、请求和状态见[工单API](../api/maintenance.md)。

运行 `python3 scripts/smoke-phase-7.py --expected-commit <实际构建SHA>`，先回归Phase6，再验证实际报修/派单/取消/即时撤权和真实M1拒绝后的SYNC_FAILED。只临时为原房间管理员增加主管角色，结束恢复原角色和空范围；不扩充seed。`python3 scripts/check-database.py --phase7`检查schema/账号、seed、账本、M2与M3事件/同步一致性。当前基线禁止假IN_PROGRESS/COMPLETED或M3占用，真实成功封房/释放留Phase8。

后端完整153项通过，前端6项/lint/类型检查/构建通过；注入Clock验证超时，明确HTTP响应夹具验证完成/验收/返修/评价/释放，不冒充真实M1联调。运行结果见[Phase7验收](../review/phase-7-validation.md)。此页仍是运行检查，完整角色业务Web和升级标记属于Phase9。本轮完成后停止等待Phase8。
