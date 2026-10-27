# 物业工单API（Phase7草稿）

机器契约见[maintenance.v1.json](../../contracts/openapi/maintenance.v1.json)，当前10个操作。所有公开操作使用D用户JWT，M3每次真实HTTP读取当前角色/权限；无D签名秘密或跨schemaSQL。请求/响应统一envelope与requestId；未知请求字段400，非法身份401，权限403，不可见工单404，状态冲突409，非法维修人员/停用房间422，D不可用503。

| 操作 | 路径 | 条件与结果 |
|---|---|---|
| POST | /api/v1/maintenance-tickets | STUDENT且maintenance:report，roomId/description≤500非空，启用房间；201 REPORTED，Location |
| GET | 同上 | view=mine/assigned/managed；默认50、1–100、offset≥0；SQL先按可见范围筛选 |
| GET | /{id} | 一致快照ticket/events/review；本人、当前被指派PROPERTY_STAFF或PROPERTY_MANAGER+maintenance:assign |
| POST | /{id}/assign | 当前PROPERTY_MANAGER+maintenance:assign，REPORTED，assigneeId经D实时资格检查且不同于报修人；ASSIGNED |
| POST | /{id}/start | 当前被指派PROPERTY_STAFF，ASSIGNED且房间启用；BLOCKING_ROOM，排入BLOCK任务 |
| POST | /{id}/finish | 当前被指派PROPERTY_STAFF，已确认占用的IN_PROGRESS；note必填≤500，WAITING_ACCEPTANCE |
| POST | /{id}/accept | 报修人，未排释放的WAITING_ACCEPTANCE；note必填；排入RELEASE，确认释放前不COMPLETED |
| POST | /{id}/reject-acceptance | 报修人，未排释放的WAITING_ACCEPTANCE；note必填，回IN_PROGRESS/原占用不变，新维修周期/期限 |
| POST | /{id}/cancel | 报修人，REPORTED/ASSIGNED；CANCELLED。开始同步后需确认释放，当前禁止直接取消 |
| POST | /{id}/review | 报修人，COMPLETED，一次rating1–5，comment可空≤500；重复409 |

SYSTEM_ADMIN无默认派单、代维修、代验收或查看他人工单覆盖。PROPERTY_MANAGER为显式全局派单角色，不虚构组织/区域模型。角色与权限撤回对现有JWT立即生效；D响应为本次HTTP快照，不承诺跨服务角色修改与M3事务原子。D仅M3凭据可读/internal/v1/maintenance-staff/{id}，只返回id/eligible。

指派时dueAt默认为24小时（MAINTENANCE_REPAIR_TIMEOUT_HOURS，必须正整数）；返修重开期限并增加repairCycle。每30秒扫描ASSIGNED/BLOCKING_ROOM/IN_PROGRESS/SYNC_FAILED，达到dueAt则标escalated并写一次ESCALATED系统事件，每周期唯一；取消/未派单/等待验收/完成不继续升级。字段和事件已可供前端标记，完整角色UI属于Phase9。该时限与角色/评价/取消政策是具体待review草案。

M1客户端与D使用不同opaque凭据。POST /internal/v1/room-blocks：roomId、startAt=持久blockStartAt、endAt=null、sourceModule=M3、sourceType=MAINTENANCE、sourceRefId=maintenance-ticket:ID。DELETE保留源路径/internal/v1/room-blocks/M3/maintenance-ticket:ID；本客户端只有一个M3类型，不修改源路径来解决未来M1三元来源歧义。

BLOCK要求200/201的ACTIVE回执，RELEASE要求200的RELEASED回执；envelope与来源/房间/startAt/endAt/id全部匹配才接受。释放回执必须匹配原occupancyId。M1真实契约仍属Phase8，当前真实403正确保存SYNC_FAILED，不能假报IN_PROGRESS/COMPLETED或改D座位状态。

任务持久operation/attempts/nextAt/30秒claim lease，来源固定，HTTP不占本地锁，迟到回执必须匹配令牌和操作；最多3次总尝试，仅网络/5xx按30/60秒退避，4xx/坏回执终止，耗尽租约为M1_ACK_UNKNOWN。同步失败保留BLOCK/RELEASE及已知占用，不自动丢弃或重建来源；人工恢复/未知提交补偿与真实M1房间冲突处理留Phase8。调度独立扫描/同步、池2、每批100。

返回View包含状态、dueAt/repairCycle/escalated、占用ID和同步operation/attempts/error，不包含claim token/lease/nextAt；详情含当前授权范围内的事件及评价。描述/维修说明/验收意见/评价正文不写日志，保存/删除/申诉期限未定，不把无清理解释为允许永久保存。仅本地五用户seed；隔离测试另建主管，本地smoke临时授权原用户后恢复。
