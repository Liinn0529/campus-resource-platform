# 项目协作规则

## 当前阶段与依据

Phase 0–4 已交付，用户在 Phase 4 交付后回复“继续”，现已授权 Phase 5：M1 签到、提醒和违约。Phase 5 完成后必须停止，等待用户明确确认才能进入 Phase 6。一次只完成一个 Phase；上传 GitHub、创建 PR 或审核文档均不等于批准下一阶段。

需求依据是 [原始执行文档](docs/requirements/CODEX_EXECUTION_PROMPT.md)，规范化需求见 [REQUIREMENTS](docs/requirements/REQUIREMENTS.md)，状态见 [TRACEABILITY](docs/requirements/TRACEABILITY.md)。保留未完成需求，不以删除条目消除欠账。发现冲突或歧义，记录并交由人工确认，不悄悄改写需求。

Phase 0–4 的治理、工程、数据库、D 公共层与 M1 基础预约保留。当前 Phase 5 允许真实房间码签到、T−15/T+10 站内提醒、T+15 NO_SHOW、违约记录、对应占用释放、持久调度/去重、D 真实站内收件箱及相关测试和契约。禁止提前实现审批、工单、M2/M3 占用接口、完整业务 Web 或 CI/CD。不私加封号/积分，不把站内持久接收称为邮件送达或用户已读，不擅自增加 COMPLETED 自动转换。schema 与公共契约以具体可运行草稿 PR 人工 review；未定业务政策明确记录方案，不将后续阶段标为完成。

## 工程原则

业务确定性优先；简单可维护；真实运行；可验证；边界明确。不得以 mock、假接口、假测试、默认成功或虚构演示数据掩盖未完成工作。不堆叠 Manager、Helper、Factory、Adapter 等无必要抽象。

只在 HTTP、JWT、权限、数据库约束、外部响应、状态转换和环境变量边界严格校验。避免每层重复 null check、重复 Bean Validation、无意义 Optional/fallback、所有方法 try/catch、catch Exception 后返回默认值、无限重试和大量日志。注释只解释业务规则、架构原因、并发逻辑和跨模块契约。

## 模块和数据边界

| 模块 | 服务 | 所有权 |
|---|---|---|
| D | platform-service | 身份、RBAC、空间主数据、系统参数、签到码、通知接口、审计 |
| M1 | reservation-service | 座位预约、占用账本、签到记录、违约、提醒、房间互斥锁 |
| M2 | classroom-service | 整教室申请、多级审批、教室业务事件 |
| M3 | maintenance-service | 物业工单、派单、维修、验收、评价、升级事件 |

四服务共享一个 PostgreSQL 实例，各自拥有独立 schema 和数据库账号。禁止跨 schema SQL、跨服务直接读写表；跨模块只通过 HTTP API。详见 [数据所有权](docs/architecture/DATA_OWNERSHIP.md)。

M1 是座位占用真值来源，不得只用 seat.status 表达全部状态。所有影响房间占用的创建/释放均在事务内锁定 room_gate 行。M2/M3 用显式同步中间状态与幂等 HTTP 调用；只重试网络错误和 5xx，最多 3 次，4xx 不重试。次数口径的未决项见架构文档。

## 选型与禁止事项

遵循 [架构基线](docs/architecture/ARCHITECTURE.md) 的完整技术栈。第一版不用 Redis、Redis lock、BPMN 引擎、分布式事务，不引入 Kafka、Nacos、Consul、Service Mesh、Event Sourcing、CQRS。CCE 仅在 ECS + Compose 稳定后考虑。

AI 只能用于开发和最后的可选自然语言 Agent；不得决定权限、审批、预约、座位或工单状态。Agent 不连接数据库。禁止 AI 推荐座位、智能维修、自动审批、自动生成公告、运营结论、虚构数据和扩充 seed。Seed 上限按原文最小集合，不提前生成。

## 安全、迁移和验证

不得提交 token、密码、密钥、私有环境文件或敏感日志。密码使用 BCrypt；JWT 有有效期；生产 HTTPS；后端 RBAC；参数化 SQL；CORS 白名单；内部 API 不暴露公网；生产 Swagger 受限；数据库只走私网；生产无默认密码。日志不得包含密码、JWT、refresh token、签到码明文或敏感个人信息。

Flyway migration 只向前、发布后不可修改；修复用新 migration；禁止手工改生产 schema；seed 与 schema 分离；空库可重放。时间规则使用可注入 Clock。测试必须验证真实行为，不测试 getter/setter、常量或框架本身，不 mock 被测逻辑；生产跨模块使用真实 HTTP。按阶段验证并诚实记录未运行、不适用和失败，不将文档检查称为业务测试。

## Git 与审阅

使用 main、feature/*、fix/*。每个需求或阶段独立分支，main 保持可构建；禁止直接大规模修改 main。提交采用 `type: summary`，关联需求 ID，提交前检查差异、敏感内容和相关验证。PR 必须有变更范围、验证证据、未完成项；本阶段使用草稿 PR 等待人工确认。

用户要求每完成一个可运行部分即提交 GitHub。按可验证的分项保留独立提交，完成实际验证后立即同步当前阶段分支；阶段未全部验收时如实保持 PARTIAL，不等待整个项目完成才上传。

schema、公共契约、跨模块变更必须人工 review。Git 语义冲突必须人工处理，不自动猜测解决。不要删除已有有效代码。分支保护与 required checks 的实际配置必须如实记录，不能仅凭本文件宣称 GitHub 已强制执行。

## 完成判定与阶段报告

业务任务完成必须同时满足原文 Definition of Done：关联需求 ID、真实逻辑、无生产 mock、必要 migration、测试通过、API 文档、RBAC 验证、无敏感日志、Docker 可用、CI 通过、追踪表更新、人工验收可复现、Git 历史可追踪。

Phase 0 以文档和目录的专属验收为准；业务逻辑、迁移、RBAC、Docker 和 CI 尚未实现且不适用本阶段，不能标为通过。每个 Phase 报告修改文件、关键设计、实际命令、测试结果、未完成内容和建议 commit message，随后停止等待人工确认。

Phase 1 历史验收为七容器 healthy、health/info 真实、数据库为空实例。Phase 2 必须以真实 PostgreSQL 验证各账号仅访问自身 schema、应用无 DDL/迁移历史修改权限、Flyway 空库自动重放、精确 seed 数量与幂等；按原文对本项目开发数据库执行 down -v / up -d 验收前先确认没有需保留的业务数据，不操作其他项目。保留历史报告，不将 Phase 0/1 的专属范围检查用于新阶段。
