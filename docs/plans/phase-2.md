# Phase 2：数据库

Phase 1 交付后用户确认继续，本轮仅执行 Phase 2，完成后停止等待 Phase 3 确认。每完成一个可运行且已验证的分项即提交 GitHub。分支 feature/phase-2-database 基于最终 Phase 1 提交 ecf9af24591a369501df3aa464ce6b370aae24ef；前置 PR 未合并时使用堆叠草稿 PR。

## 范围与设计

1. PostgreSQL 创建 platform、reservation、classroom、maintenance 四个 schema；每个 schema 由独立 migrator 拥有，应用使用独立 app 账号。应用无 DDL、无账号成员身份、无跨 schema 访问权限。密码只从本地私有环境注入。
2. 四服务接入 PostgreSQL/MyBatis/Flyway，Flyway 使用对应 migrator，普通连接使用 app。每个服务执行自己的版本迁移，迁移历史对 app 只读权限也不授予。schema/bootstrap 与版本迁移界限明确。
3. D 创建最小 seed 必需的 user、role、permission、user_role、role_permission、building、room、seat 表。seed 与结构迁移分离，仅显式 local profile 执行；五个用户、两栋楼、四个房间、四十座位，不创建预约、审批或工单。密码使用本地生成秘密并 BCrypt 哈希，重复启动不重设已有用户密码。
4. 非 D 服务只迁移 schema 权限/版本基线，不用假的业务表填充。room_gate/预约/占用/签到/违约在 Phase 4/5，M2 业务表在 Phase 6，M3 在 Phase 7；相关需求继续 TODO/PARTIAL。
5. 运行真实 PostgreSQL 测试和 Compose 验证，核对账号隔离、DDL 拒绝、迁移历史保护、seed 幂等、空库重放及七容器健康。执行本项目 down -v 前只读确认当前开发库为空或仅含本阶段可重建演示数据。

## 未决项处理

OQ-04 的签到码时区/唯一范围、OQ-07 的释放幂等契约、OQ-11 的额外演示角色账号仍留对应业务阶段，不提前固定规则或增加用户。OQ-13 本阶段解决账号与 D 表类型/可空性，其他业务状态枚举、表名、锁行初始化仍保留。schema 变更以可运行代码和草稿 PR 交人工 review，不自动合并。

## 执行结果

第一分项：数据库基线及真实 PostgreSQL 测试已完成。bootstrap 位于 deploy/postgres，四服务的 V1 位于各自 db/migration，D 的六份 seed 位于 db/seed/local。scripts/test-backend.sh 使用生产 bootstrap 创建 Testcontainers PostgreSQL 16，23 个测试全部通过（0 失败、0 错误、0 跳过），验证迁移、账号与 DDL/历史隔离、精确 seed、幂等及非 local 无 seed。

Compose 空卷启动、持久卷重启及最终运行版本验证尚在执行，不能将本分项测试视为阶段全部验收完成。完整命令、结果、剩余内容与提交记录将在本阶段验证报告中补齐。
