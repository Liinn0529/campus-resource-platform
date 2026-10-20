# CODEX_EXECUTION_PROMPT.md

## 角色

你现在是本项目的主开发代理，负责从零实现一个可交付、可测试、可部署、可追踪的校园资源服务平台。

你必须遵循本文件，不得一次性生成整个项目，不得为了“看起来复杂”而堆叠技术，不得用 mock、假接口、假测试、假成功结果掩盖未完成内容。

项目目标不是堆功能，而是完成一套工程化、可运行、可部署、可演示的系统。

---

# 一、项目背景

项目名称：

**校园资源服务平台**

系统包含以下业务模块：

- M1：自习座位预约
- M2：整教室预约与审批
- M3：物业报修工单
- D：公共层
- Optional：自然语言业务 Agent

核心要求：

1. 模块边界清晰；
2. 核心业务可真实运行；
3. 跨模块通过 HTTP API 调用；
4. 数据库 schema 版本化管理；
5. 测试必须真实验证行为；
6. 从 Git commit 可追踪到部署版本；
7. 支持测试环境自动部署；
8. 支持生产发布与回滚；
9. 后续可部署至华为云；
10. AI 仅用于开发和可选 Agent，不得成为核心业务真值来源。

---

# 二、开发总原则

必须遵循：

```text
业务确定性 > AI生成
简单可维护 > 复杂架构
真实运行 > 演示代码
可验证 > 看起来完整
明确边界 > 过度防御
```

禁止：

- 一次性生成整个系统；
- 使用大量无意义抽象；
- 为简单 CRUD 创建过多 Manager / Helper / Factory / Adapter；
- 在每层重复 null check；
- 所有函数都 try/catch；
- catch Exception 后返回默认值；
- 无限 retry；
- mock 掉真实业务后宣称集成完成；
- 测试只断言常量；
- 测试 mock 掉被测逻辑本身；
- 直接访问其他模块数据库；
- 手工修改生产数据库 schema；
- 修改已经执行过的 Flyway migration；
- 把 token、密码、密钥提交 Git；
- LLM 直接决定审批、权限、工单状态、座位状态；
- 自动解决 Git 语义冲突；
- 为了课程项目引入 Kafka、Nacos、Consul、Service Mesh、Event Sourcing、CQRS 等非必要基础设施。

---

# 三、技术栈

## Backend

```text
Java 21
Spring Boot 3.x
Spring Security
MyBatis
PostgreSQL
Flyway
Maven
Bean Validation
OpenAPI 3
JUnit 5
Testcontainers
JaCoCo
```

## Frontend

```text
Vue 3
TypeScript
Vite
Vue Router
Pinia
Element Plus
Axios
Vitest
Playwright
```

## DevOps

```text
Docker
Docker Compose
Nginx
GitHub Actions
Huawei Cloud SWR
Huawei Cloud ECS
Huawei Cloud RDS PostgreSQL
```

后续可选：

```text
Huawei Cloud ELB
Huawei Cloud CCE
Huawei Cloud DCS Redis
```

第一版不要使用 Redis。

---

# 四、总体架构

采用：

```text
Monorepo
+
Frontend / Backend separation
+
4 backend services
+
REST API
+
PostgreSQL
```

目录结构：

```text
campus-resource-platform/
├── AGENTS.md
├── README.md
├── pom.xml
├── package.json
├── pnpm-workspace.yaml
├── docs/
│   ├── requirements/
│   ├── architecture/
│   ├── adr/
│   ├── api/
│   ├── plans/
│   ├── review/
│   ├── ethics/
│   └── runbooks/
├── contracts/
│   └── openapi/
├── services/
│   ├── platform-service/
│   ├── reservation-service/
│   ├── classroom-service/
│   └── maintenance-service/
├── apps/
│   └── web/
├── deploy/
│   ├── compose/
│   ├── nginx/
│   └── huawei/
├── scripts/
├── .github/
│   └── workflows/
└── .env.example
```

---

# 五、服务划分

## 1. platform-service

负责公共层 D：

```text
用户
登录
RBAC
角色
权限
楼宇
教室
自习室
座位主数据
系统参数
签到码
通知接口
操作审计
health
version
```

---

## 2. reservation-service

负责 M1：

```text
自习室查询
座位查询
可用时间查询
座位预约
预约取消
签到
提醒
违约
预约历史
重新预约
座位占用账本
```

---

## 3. classroom-service

负责 M2：

```text
整教室申请
多级审批
批准
驳回
申请撤销
审批超时撤销
审批状态查询
调用 M1 占用教室
调用 M1 释放教室
```

---

## 4. maintenance-service

负责 M3：

```text
故障上报
派单
开始维修
维修中
超时升级
完工
验收
评价
取消
调用 M1 下架教室
调用 M1 恢复教室
```

---

# 六、数据库规则

使用一个 PostgreSQL 实例，但每个服务独立 schema：

```text
platform
reservation
classroom
maintenance
```

每个服务：

```text
独立数据库账号
只能访问自己的 schema
```

禁止：

```text
跨 schema SQL
跨服务直接读表
跨服务直接写表
```

跨模块访问必须使用 HTTP API。

---

# 七、M1 核心数据设计

不要只用一个 `seat.status` 表示所有状态。

必须建立：

```text
room_gate
seat_reservation
seat_occupancy
checkin
violation
reservation_event
```

`seat_occupancy` 必须包含：

```text
id
room_id
seat_id nullable
start_at
end_at nullable
source_module
source_type
source_ref_id
status
created_at
released_at
```

`source_module`：

```text
M1
M2
M3
```

`source_type`：

```text
RESERVATION
CLASSROOM_BOOKING
MAINTENANCE
```

这样可以追踪座位不可用来源。

---

# 八、并发控制

不要引入 Redis lock。

建立：

```text
room_gate(room_id PRIMARY KEY)
```

所有影响房间座位状态的操作：

```text
BEGIN
SELECT room_gate FOR UPDATE
检查占用
创建/释放占用
COMMIT
```

包括：

```text
M1 创建预约
M1 取消预约
M2 创建教室占用
M2 释放教室占用
M3 创建维修占用
M3 释放维修占用
```

锁粒度：

```text
room
```

---

# 九、跨模块 API

## M2 / M3 -> M1

创建房间占用：

```http
POST /internal/v1/room-blocks
```

请求示例：

```json
{
  "roomId": 1001,
  "startAt": "2026-11-10T14:00:00+08:00",
  "endAt": "2026-11-10T17:00:00+08:00",
  "sourceModule": "M2",
  "sourceType": "CLASSROOM_BOOKING",
  "sourceRefId": "CB-20261110-001"
}
```

维修：

```json
{
  "roomId": 1001,
  "startAt": "2026-11-10T14:00:00+08:00",
  "endAt": null,
  "sourceModule": "M3",
  "sourceType": "MAINTENANCE",
  "sourceRefId": "WO-20261110-008"
}
```

释放：

```http
DELETE /internal/v1/room-blocks/{sourceModule}/{sourceRefId}
```

唯一约束：

```text
(source_module, source_ref_id, source_type)
```

重复请求：

```text
内容一致 -> 返回已有结果
内容冲突 -> 409 Conflict
```

---

# 十、跨服务一致性

不要使用分布式事务。

M2：

```text
APPROVING
-> APPROVED_PENDING_SYNC
-> 调 M1
-> APPROVED
```

失败：

```text
APPROVED_PENDING_SYNC
-> retry
-> SYNC_FAILED
```

M3：

```text
ASSIGNED
-> BLOCKING_ROOM
-> 调 M1
-> IN_PROGRESS
```

重试规则：

```text
只重试网络错误和 5xx
不重试 4xx
最多 3 次
超出后 -> SYNC_FAILED
```

---

# 十一、M1 业务规则

预约状态：

```text
CREATED
CANCELLED
CHECKED_IN
NO_SHOW
COMPLETED
```

规则：

1. 预约时间按整点小时；
2. 默认最多连续预约 4 小时；
3. 最大预约时间为系统参数；
4. 创建预约前检查所有 M1/M2/M3 占用；
5. 已占用则返回 409；
6. 支持插座座位过滤；
7. 支持历史预约；
8. 支持再次预约历史座位。

提醒：

```text
T-15min -> 第一次提醒
T+10min -> 第二次提醒
T+15min -> NO_SHOW
```

NO_SHOW 时：

```text
取消预约
释放 occupancy
新增 violation
新增 audit/event
```

时间逻辑使用可注入 `Clock`，便于测试。

---

# 十二、签到

建立：

```text
room_checkin_code
```

字段：

```text
room_id
date
code
created_at
```

规则：

```text
每天每个 room 一个 code
6-8 位随机字符
数据库唯一约束
可生成 QR Code
```

Web：

```text
输入签到码
-> 校验 room
-> 校验 reservation
-> 完成签到
```

第一版不做：

```text
GPS
人脸识别
蓝牙
NFC
```

---

# 十三、M2 状态机

```text
SUBMITTED
APPROVING
APPROVED_PENDING_SYNC
APPROVED
REJECTED
CANCELLED
AUTO_CANCELLED
SYNC_FAILED
```

表：

```text
classroom_request
approval_step
approval_action
classroom_event
```

第一版审批链：

```text
普通学生
-> ROOM_ADMIN
-> DEPARTMENT_ADMIN
```

不要实现 BPMN 引擎。

---

# 十四、M3 状态机

```text
REPORTED
ASSIGNED
BLOCKING_ROOM
IN_PROGRESS
WAITING_ACCEPTANCE
COMPLETED
CANCELLED
SYNC_FAILED
```

操作：

```text
report
assign
start
finish
accept
rejectAcceptance
cancel
review
```

超时：

```text
due_at
定时扫描
写 escalation event
UI 标记
```

---

# 十五、RBAC

表：

```text
user
role
permission
user_role
role_permission
```

角色：

```text
STUDENT
ROOM_ADMIN
DEPARTMENT_ADMIN
PROPERTY_STAFF
PROPERTY_MANAGER
OPERATOR
SYSTEM_ADMIN
```

权限示例：

```text
reservation:create
reservation:view:self
classroom:apply
classroom:approve
maintenance:report
maintenance:assign
space:manage
user:manage
audit:view
```

权限必须在后端校验。

前端隐藏按钮不能作为权限控制。

---

# 十六、认证

第一版：

```text
用户名 / 学号
+
密码
+
JWT
```

密码：

```text
BCrypt
```

生产：

```text
HTTPS only
```

暂不接学校统一身份认证。

---

# 十七、API 风格

版本：

```text
/api/v1
/internal/v1
```

成功：

```json
{
  "data": {},
  "requestId": "..."
}
```

失败：

```json
{
  "code": "RESERVATION_TIME_CONFLICT",
  "message": "所选时段已不可预约",
  "requestId": "..."
}
```

HTTP Status：

```text
200
201
204
400
401
403
404
409
422
500
503
```

禁止所有接口都返回 HTTP 200。

---

# 十八、减少防御性编程

只在边界做严格验证：

```text
HTTP request
JWT
权限
DB constraint
外部 HTTP response
状态机转换
环境变量
```

内部代码保持简洁。

禁止：

```text
每层重复校验
所有方法 try/catch
无意义 Optional
无意义 fallback
默认成功
内部对象反复 Bean Validation
无限重试
大量日志
```

---

# 十九、减少 AI 生成型内容

系统 UI 和业务内容必须保持真实。

禁止：

```text
AI推荐座位
AI智能维修
AI自动审批
AI自动生成公告
AI生成运营结论
AI虚构数据
AI自动填满 seed data
```

Seed 仅创建最小演示数据：

```text
1 system admin
2 students
1 room admin
1 property staff
2 buildings
4 rooms
40 seats
```

代码注释只解释：

```text
业务规则
架构原因
并发逻辑
跨模块契约
```

不要生成大段教学式注释。

---

# 二十、前端页面

## Student

```text
/login
/student/home
/student/rooms
/student/rooms/:id
/student/reservations
/student/reservations/:id
/student/history
/student/classroom-applications
/student/repairs
```

## Approval

```text
/approvals
/approvals/:id
```

## Maintenance

```text
/maintenance
/maintenance/:id
```

## Admin

```text
/admin/spaces
/admin/rooms
/admin/seats
/admin/users
/admin/roles
/admin/settings
/admin/audit
```

---

# 二十一、UI 要求

```text
清晰
克制
业务化
响应式
```

避免：

```text
大量渐变
无意义动画
AI 风格装饰
Fake Dashboard
无真实数据来源图表
```

学生端：

```text
Mobile friendly
```

管理端：

```text
Desktop first
```

---

# 二十二、版本管理

分支：

```text
main
feature/*
fix/*
```

规则：

```text
main 始终可构建
禁止直接大规模修改 main
每个需求一个分支
PR 必须测试通过
schema 变更人工 review
公共契约人工 review
跨模块变更人工 review
Git 冲突人工处理
```

---

# 二十三、需求追踪

创建：

```text
docs/requirements/TRACEABILITY.md
```

格式：

```markdown
| ID | Requirement | Module | API/Page | Test | Status |
|---|---|---|---|---|---|
| REQ-M1-001 | 查询可用座位 | M1 | GET /api/v1/availability | ReservationAvailabilityIT | DONE |
```

状态：

```text
TODO
PARTIAL
BLOCKED
DONE
```

禁止删除未完成需求。

---

# 二十四、Flyway

路径：

```text
src/main/resources/db/migration/
```

例如：

```text
V1__init.sql
V2__add_room_gate.sql
V3__add_source_to_occupancy.sql
```

要求：

```text
migration 只向前
已经发布的不修改
修复创建新 migration
从空库可以完整重放
seed 和 schema migration 分离
```

---

# 二十五、测试

## Unit Test

重点：

```text
状态机
时间规则
权限
预约最大时间
取消规则
超时逻辑
```

不要测试：

```text
getter/setter
框架本身
常量
```

---

## Integration Test

使用：

```text
Testcontainers PostgreSQL
```

测试：

```text
并发预约
room_gate lock
Flyway
unique constraint
M2->M1
M3->M1
idempotency
```

---

## API Test

测试：

```text
401
403
正常请求
409
非法参数
角色隔离
```

---

## E2E

Playwright：

```text
学生登录 -> 搜索 -> 预约
签到
整教室申请 -> 审批 -> M1 座位不可约
报修 -> 开始维修 -> M1 座位下架
维修验收 -> 座位恢复
```

---

# 二十六、测试有效性验证

至少执行一次 mutation-style 人工验证：

例如：

```text
把最大预约时长从4小时临时改成8小时
运行测试
确认测试失败
恢复代码
```

记录：

```text
docs/review/test-effectiveness.md
```

---

# 二十七、日志

至少：

```text
timestamp
level
service
requestId
userId
method
path
status
latencyMs
```

不得记录：

```text
password
JWT
refresh token
签到码明文
敏感个人信息
```

---

# 二十八、健康检查和版本

每服务：

```text
GET /actuator/health
GET /actuator/info
```

info：

```json
{
  "service": "...",
  "version": "...",
  "gitCommit": "...",
  "buildTime": "..."
}
```

---

# 二十九、安全要求

必须：

```text
HTTPS
BCrypt
JWT expiration
Server-side RBAC
Parameterized SQL
CORS whitelist
No default production password
No secret in Git
DB private network only
Internal API not public
Swagger production restricted
```

---

# 三十、本地 Docker

开发 compose：

```text
postgres
platform-service
reservation-service
classroom-service
maintenance-service
web
nginx
```

可选：

```text
mailhog
```

生产数据库不放应用 compose 中。

---

# 三十一、CI

PR：

```text
checkout
backend lint
backend test
frontend lint
frontend unit test
integration test
build
docker build
```

任何阶段失败：

```text
stop
```

---

# 三十二、CD

## Test

main merge：

```text
build images
tag commit SHA
push registry
deploy test server
run Flyway
docker compose pull
docker compose up -d
health check
smoke test
```

---

## Production

Git tag：

```text
v0.1.0
```

人工批准后：

```text
deploy production
```

保存：

```text
tag
commit SHA
image tag
migration version
deploy time
operator
```

---

# 三十三、回滚

生产环境变量：

```text
RELEASE_TAG=v0.2.0
```

回滚：

```text
RELEASE_TAG=v0.1.0
docker compose pull
docker compose up -d
```

数据库 schema 优先保证向前兼容。

不要依赖 migration downgrade。

---

# 三十四、华为云目标架构

```text
Internet
   |
HTTPS
   |
EIP / ELB
   |
Nginx
   |
Huawei ECS
   |
Docker Compose
   |
+----------------------+
| platform-service     |
| reservation-service  |
| classroom-service    |
| maintenance-service  |
+----------------------+
   |
Huawei RDS PostgreSQL
```

镜像：

```text
GitHub Actions
-> Huawei SWR
-> ECS
```

---

# 三十五、华为云部署文件

创建：

```text
deploy/huawei/
├── README.md
├── bootstrap-ecs.sh
├── deploy.sh
├── rollback.sh
├── healthcheck.sh
├── docker-compose.prod.yml
├── nginx.conf
└── env.example
```

`bootstrap-ecs.sh`：

```text
安装 Docker
安装 Compose plugin
创建目录
设置运行用户
启用 Docker
```

禁止硬编码凭据。

---

# 三十六、可选 CCE

ECS + Compose 稳定后才允许新增：

```text
deploy/helm/
```

保持：

```text
image unchanged
env unchanged
health endpoint unchanged
database unchanged
```

映射：

```text
Docker Compose service
-> Kubernetes Deployment + Service
```

---

# 三十七、自然语言 Agent

只能最后实现。

Agent 不得直接访问数据库。

只能通过公开 API：

```text
searchSeats
createReservation
listReservationHistory

createClassroomApplication
getClassroomApplication
cancelClassroomApplication

createRepairTicket
getRepairTicket
cancelRepairTicket
```

用户：

```text
我要预约一间教室
```

参数不足必须追问：

```text
日期
开始时间
结束时间
区域
用途
```

LLM 只做：

```text
自然语言
-> intent
-> tool call
-> template result
```

LLM 不决定：

```text
权限
审批结果
预约结果
座位状态
工单状态
```

---

# 三十八、工程伦理

持续维护：

```text
docs/ethics/ETHICS_LOG.md
```

记录实际问题：

```text
违约记录保存周期
管理员权限边界
个人信息最小化
日志数据
Agent越权风险
自动撤销风险
维修下架误伤
```

不要最后补写空泛文章。

---

# 三十九、Definition of Done

每个任务只有满足全部条件才能 DONE：

```text
[ ] Requirement ID linked
[ ] Real business logic implemented
[ ] No production mock
[ ] Migration added if needed
[ ] Tests passed
[ ] API docs updated
[ ] RBAC verified
[ ] No sensitive logs
[ ] Docker works
[ ] CI passes
[ ] TRACEABILITY updated
[ ] Manual acceptance reproducible
[ ] Git history traceable
```

---

# 四十、执行方式

你必须严格按 Phase 顺序执行。

每次只完成一个 Phase。

每完成一个 Phase：

```text
1. 列出修改文件
2. 说明关键设计
3. 列出实际执行命令
4. 列出测试结果
5. 列出未完成内容
6. 给出建议 commit message
7. 等待人工确认后再进入下一阶段
```

禁止自动跳到下一阶段。

---

# Phase 0：项目治理和需求基线

任务：

```text
创建 AGENTS.md
创建 README.md
创建 docs 目录
创建 REQUIREMENTS.md
创建 TRACEABILITY.md
创建 ARCHITECTURE.md
创建 DATA_OWNERSHIP.md
创建 ADR-001-service-boundary.md
创建 phase-0.md
```

要求：

```text
为每条需求分配 ID
明确 M1/M2/M3/D
明确跨模块依赖
明确技术栈
明确数据库所有权
明确禁止事项
```

验收：

```text
仓库结构存在
规则文件存在
需求可追踪
架构有文字说明
没有提前编写业务代码
```

建议 commit：

```text
chore: bootstrap project governance and requirements
```

---

# Phase 1：工程骨架

创建：

```text
4 Spring Boot services
1 Vue app
Dockerfile
docker-compose
Nginx
```

每服务：

```text
/actuator/health
/actuator/info
```

验收：

```bash
docker compose up -d
```

全部 healthy。

建议 commit：

```text
feat: scaffold platform services and web application
```

---

# Phase 2：数据库

创建：

```text
platform schema
reservation schema
classroom schema
maintenance schema
```

独立数据库账号。

加入：

```text
Flyway
minimum seed
```

验收：

```bash
docker compose down -v
docker compose up -d
```

全新数据库自动完成 migration。

建议 commit：

```text
feat: add versioned database schemas and seed data
```

---

# Phase 3：D 公共层

实现：

```text
login
RBAC
user
role
permission
building
room
seat
settings
checkin code
audit
```

验收：

```text
admin 可以维护空间
student 无管理员权限
后端 RBAC 生效
```

建议 commit：

```text
feat: implement platform identity and resource management
```

---

# Phase 4：M1 基础预约

实现：

```text
search room
search seat
availability
create reservation
cancel
history
rebook
room_gate
seat_occupancy
```

验收：

```text
不能重复预约
默认最多4小时
插座过滤有效
历史预约可重新预约
```

建议 commit：

```text
feat: implement seat reservation workflow
```

---

# Phase 5：M1 签到和违约

实现：

```text
checkin
T-15 reminder
T+10 reminder
T+15 no-show
violation
occupancy release
```

使用可注入 Clock。

建议 commit：

```text
feat: add reservation check-in and no-show handling
```

---

# Phase 6：M2

实现：

```text
submit
approval
approve
reject
cancel
timeout
```

单元测试中可以 mock M1。

生产代码必须使用真实 HTTP client。

建议 commit：

```text
feat: implement classroom booking approval workflow
```

---

# Phase 7：M3

实现：

```text
report
assign
start
finish
accept
cancel
review
escalate
```

建议 commit：

```text
feat: implement maintenance work order workflow
```

---

# Phase 8：真实跨模块集成

连接：

```text
M2 -> M1
M3 -> M1
```

删除生产 mock。

E2E：

```text
M2审批成功
-> HTTP M1
-> 教室座位全部不可预约
```

```text
M3开始维修
-> HTTP M1
-> 房间座位下架
-> 验收完成
-> M1恢复
```

建议 commit：

```text
feat: integrate classroom and maintenance state with reservations
```

---

# Phase 9：前端完整流程

完成：

```text
student
approval
maintenance
admin
```

验收：

```text
主要业务无需 Postman 即可完成
```

建议 commit：

```text
feat: complete role-based web workflows
```

---

# Phase 10：测试体系

加入：

```text
Testcontainers
Playwright
cross-service integration
scripts/test-all.sh
```

验收：

```bash
./scripts/test-all.sh
```

必须退出码 0。

建议 commit：

```text
test: add cross-module integration and end-to-end coverage
```

---

# Phase 11：CI

创建：

```text
.github/workflows/ci.yml
```

验收：

```text
lint失败不能合并
test失败不能合并
build失败不能合并
```

建议 commit：

```text
ci: add automated quality gates
```

---

# Phase 12：测试环境部署

创建：

```text
deploy-test.yml
```

流程：

```text
main merge
-> build
-> push
-> deploy
-> migration
-> health
-> smoke test
```

建议 commit：

```text
ci: add automated test environment deployment
```

---

# Phase 13：生产发布和回滚

创建：

```text
deploy.sh
rollback.sh
RELEASE.md
```

验收：

```text
v0.1.0 -> v0.2.0 -> rollback v0.1.0
```

建议 commit：

```text
ops: add production release and rollback workflow
```

---

# Phase 14：华为云

部署：

```text
SWR
ECS
RDS PostgreSQL
Nginx
HTTPS
```

验收：

```text
公网只开放HTTPS
DB不开放公网
SWR可拉取
CI/CD可部署
health正常
版本可追踪
```

建议 commit：

```text
ops: add Huawei Cloud deployment configuration
```

---

# Phase 15：可选 Agent

只有前面全部完成才开始。

实现：

```text
Natural language
-> intent
-> API tool
-> structured result
```

验收：

```text
模糊需求必须追问
Agent调用真实API
Agent不能直接连接数据库
```

建议 commit：

```text
feat: add natural-language business agent plugin
```

---

# 四十一、第一次执行命令

现在只执行：

```text
Phase 0
```

执行前：

1. 扫描当前仓库；
2. 不删除已有有效代码；
3. 如果仓库为空，创建基础目录；
4. 如果已有部分结构，尽量复用；
5. 不开始 Phase 1；
6. 不创建 Spring Boot 服务；
7. 不生成业务代码。

完成 Phase 0 后，输出：

```text
## Phase 0 Result

### Files Created
...

### Files Modified
...

### Requirement IDs
...

### Architecture Decisions
...

### Validation Performed
...

### Remaining Issues
...

### Recommended Commit
...
```

完成后停止，等待人工确认。
