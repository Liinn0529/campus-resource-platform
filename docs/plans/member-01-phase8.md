# 成员一 Phase8 实施与交接

基线：`cf265a7ca2ea2e6b7807a1acdc762f2beaff0e34`。开发分支：`feature/member-01-occupancy`。
本次用户已授权成员一开发、环境配置，以及验证通过后的 Git 上传；不把历史 Phase7 停止点当成本次禁止开发。公共契约/schema/未定政策仍需非作者审核，不替成员四或项目负责人签字。

## 本人实现

1. `RoomBlockController` / `RoomBlockMapper`：真实内部创建/释放；M2/M3独立凭据；三元来源唯一；共享房间行锁；开放维修区间；重复释放；已释放来源不能复活。所有D HTTP在房间锁外执行。
2. `LifecycleService`：仅CHECKED_IN在`now >= endAt`完成，验证签到及精确ACTIVE账本后原子写COMPLETED、释放和系统事件。正常完成默认关闭，审核后配置`M1_COMPLETION_ENABLED=true`。CREATED仍按原签到截止规则进入NO_SHOW，不当作正常完成。
3. V4仅向前迁移：扩充COMPLETED事件约束；事件触发器同事务建立`audit_delivery`；`audit_attempt`保留每次投递结果。没有修改V1–V3，也不回填历史事件冒充新操作。
4. `AuditDeliveryService`：通过D HTTP提交最小事件，保留源事件ID和requestId；先持久claim，再在事务外发HTTP；30/60秒退避，最多三次总尝试；过期claim可恢复，第三次回执未知直接终止，不清零重试次数。
5. M2/M3只更新测试环境凭据和真实联调断言，不改其生产业务；M3真实开始/完工/拒绝验收/再次完工/接受验收覆盖占用生命周期。M2批准后真实占用已接入，批准取消和到期释放仍由成员三补齐。

## 契约决定（待成员三/四审核）

- DELETE两段路径不变：M2只允许CLASSROOM_BOOKING，M3只允许MAINTENANCE，因此可唯一映射既有三元来源键。
- 相同来源同载荷：首次201，重复200，包含当前ACTIVE或RELEASED；已释放来源重复POST不会重新激活。不同载荷409；不存在DELETE返回404/OCCUPANCY_SOURCE_NOT_FOUND。
- 区间为半开`[start,end)`，相邻时段不冲突，M3的null结束表示无限。M2需有限结束，M3固定开放结束，与现有客户端一致。时间带offset，比较Instant，限制最多微秒精度以避免PostgreSQL舍入破坏重放匹配。
- 新M2占用必须是D启用的CLASSROOM；维修允许启用的两类房间。既有来源重放/释放不依赖D存活或资源继续启用，以便故障恢复。
- 默认维修遇既有占用返回409，不取消任何用户预约。已释放的记录保留；释放不改变D的seat.status。
- D审计草案：sourceModule=M1，sourceEventId=`reservation-event:<id>`，entityType=RESERVATION，entityId为预约ID字符串；action保留CREATED/REBOOKED/CANCELLED/CHECKED_IN/NO_SHOW/COMPLETED。actor来自认证或系统事件，不接受前端指定。D确认白名单后才能启用。
- 正常完成只作用M1座位预约，不替M2业务悄悄释放整房占用；M2负责其取消/到期状态与释放一致性。

## 尚需他人交付或决定

| 项目 | 负责人 | 本次处理 |
|---|---|---|
| D审计接收、白名单及去重 | 成员二 | 已实现M1生产客户端及持久投递；默认`M1_AUDIT_DISPATCH_ENABLED=false`保留PENDING，避免对未实现端点耗尽次数；不能记为真实审计联通 |
| M2批准取消、正常到期释放、人工恢复入口 | 成员三 | M1 DELETE已提供；不越界更改M2状态机 |
| 正常完成规则 | 成员四审核、项目负责人确认 | 可运行实现和边界测试待审，默认关闭自动触发 |
| D空间停用/移动与历史引用 | 成员二牵头、一/三配合 | 当前沿用D快照读取，新占用检查enabled；不能宣称跨服务原子停用。D提供正式规则后补M1守卫 |
| 违约/事件保留、删除与申诉 | 成员四组织、项目负责人确认 | 不擅定保存天数、不删除历史；清理机制待规则确认 |
| 非作者验收、CI与云环境 | 成员四/五 | 本地测试不能替代组内签字、CI或云验收 |

## 给成员三的接入步骤

M1同时配置两套独立`M1_CLASSROOM_TOKEN`、`M1_MAINTENANCE_TOKEN`，分别与M2/M3客户端一致，不能复用PLATFORM_*凭据。Compose已补M1环境映射。先将本分支经审核集成到共同基线，再使用真实客户端联调。

释放时校验返回的id、roomId、startAt、endAt、完整来源和RELEASED；404不能当作成功。回执丢失后原载荷POST只用于核实原来源当前状态，不创造第二个来源，不清零计数。已释放来源要新业务重新创建必须使用新业务ID。服务秘密不交给前端。

## 验证入口

```sh
./scripts/test-backend.sh
```

该入口运行真实PostgreSQL/Testcontainers、D/M1进程和M2/M3客户端。新增`RoomBlockTest`覆盖身份隔离、区间冲突、来源重放、首次房间锁及不同房间同来源并发；`ReservationLifecycleTest`补完成边界与损坏账本回滚；`AuditDeliveryTest`使用明确HTTP边界夹具验证投递故障/重启，不代表D已实现审计接收。最终实际结果见`docs/review/member-01-validation.md`。

本次V4会使历史Phase7数据库版本检查不再适用。保留历史脚本含义，不通过删除旧断言假装Phase7和Phase8相同。

新增容器验收入口：`python3.9 scripts/smoke-member-01.py --expected-commit <实际版本> --restart`。它验证网关隔离、实际重启、来源重放与精确释放，结束释放自身测试来源并保留历史。实际通过记录见验收文档。
