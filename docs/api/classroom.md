# M2 整教室申请 API（Phase 6 草稿）

对应 [OpenAPI](../../contracts/openapi/classroom.v1.json) 的6个操作。用户在Phase5后确认继续，仅授权Phase6；Schema、公共契约和业务政策通过具体草稿PR人工review，不自动合并。完整Web在Phase9，真实M1封房/释放集成在Phase8。

## 身份与操作

所有操作使用D用户JWT。M2每请求通过D `/api/v1/me`取得当前身份/角色/权限；有classroom:approve时还经本人 `/api/v1/me/room-scopes`取得当前范围，拒绝不能转成空范围掩盖失败。主数据使用独立的PLATFORM_CLASSROOM_TOKEN，不共享D签名秘密或跨schema SQL。D失效身份401、范围权限撤销403、故障/坏响应503。

| 方法/路径 | 权限与结果 |
|---|---|
| POST /api/v1/classroom-requests | STUDENT和classroom:apply；201 Request、相对Location |
| GET /api/v1/classroom-requests | view=mine默认，仅本人；view=pending需当前审批角色/权限/范围和待办阶段；200 Request[] |
| GET /api/v1/classroom-requests/{id} | 本人或当前具备classroom:approve和明确房间范围的ROOM_ADMIN/DEPARTMENT_ADMIN；200 Detail，其他404 |
| POST /api/v1/classroom-requests/{id}/approve | 当前阶段角色、classroom:approve和范围；200 Request，不能自批/同一人完成两级 |
| POST /api/v1/classroom-requests/{id}/reject | 同审批权限/阶段约束；200 REJECTED Request |
| POST /api/v1/classroom-requests/{id}/cancel | 本人且尚未同步的审批阶段、截止前；200 CANCELLED Request |

SYSTEM_ADMIN不替代对应审批角色/范围，也不能无授权查看他人申请。本人读取和取消使用认证身份，不依赖客户端申请人字段。分页limit默认50、1–100；offset默认0、非负，data直接为数组。mine按submittedAt/id倒序；pending在SQL筛选当前阶段角色、范围、申请人和既有审批人后分页，按deadline/id升序。

## 请求与响应

提交只含roomId、startAt、endAt、purpose，全部必填。roomId正整数；房间必须启用且type=CLASSROOM。时间带offset、精度不细于微秒，start严格晚于注入Clock当前时刻、end晚于start。不把M1的4小时限制擅自套入整教室申请。purpose非空白且最多500字符。approve/reject需JSON对象，只有可选reason（可为空，最多500字符）；cancel无请求体。未知JSON字段400。

Request包含id、applicantId、roomId、startAt、endAt、purpose、status、submittedAt、updatedAt、approvalDueAt、syncAttempts、syncError、occupancyId。时间输出UTC Instant；同步错误只返回安全机器码，不含远端正文/凭据。内部claim token、lease和下次执行时间不出现在公开对象。Detail包含request、steps、actions、events，在repeatable-read事务中取得一致快照；步骤/真实决定/事件字段详见OpenAPI。成功为data/requestId，失败为code/message/requestId，均关联X-Request-ID。

## 状态、期限和本地事务

提交SUBMITTED，步骤1 ROOM_ADMIN为PENDING、步骤2 DEPARTMENT_ADMIN为WAITING；第一级通过进入APPROVING并激活第二级。每级激活时持久化独立期限，配置campus.classroom.approval-timeout-hours默认24小时，封顶startAt。所有决定/撤销在申请行锁后重新读取Clock，达到期限或开始时刻409 CLASSROOM_APPROVAL_EXPIRED；独立定时扫描将未完成审批改AUTO_CANCELLED。

两级都禁止申请人自批，第二级不得由第一级同一用户完成，即使持有双角色。第一/第二批准与拒绝均写真实approval_action、步骤、申请和事件，同事务；并发请求只能有一项有效决定。第二级批准只写APPROVED_PENDING_SYNC，不在该HTTP请求中假报占用成功。拒绝只需要合法审批身份/范围，不要求已停用资源重新变得可用。

取消仅SUBMITTED/APPROVING、本人、开始和当前期限前。其他状态409 CLASSROOM_STATE_CONFLICT。申请、决定和事件历史保留；不硬删除。超时只处理未完成审批，不把APPROVED_PENDING_SYNC/SYNC_FAILED改成取消，避免未知远端提交留下无主占用。

## 真实同步与Phase 8边界

同步任务调用真实M1 origin的POST /internal/v1/room-blocks，使用独立M1_CLASSROOM_TOKEN，不复用D秘密、不转发用户JWT、禁止重定向。请求为roomId/startAt/endAt/sourceModule=M2/sourceType=CLASSROOM_BOOKING/sourceRefId=classroom-request:{申请ID}，来源稳定。

当前M1未实现该Phase8接口，直连实际403，因此生产会进入SYNC_FAILED，绝不创建假APPROVED或直接改M1表。客户端拟定的成功回执要求200/201 envelope、正id、匹配房间/区间/三来源字段和status=ACTIVE；这是待Phase8审阅的接收方案，不声称现有M1已经提供。成功分支由明确标记的测试HTTP夹具验证，不能当作跨模块验收。

独立审批超时/同步任务默认各30秒、每批100、线程池2。持久30秒lease/令牌、最多3次总尝试；网络/5xx按30/60秒退避，4xx/坏回执终止。领取时开始时刻已到则SYNC_FAILED且不再创建占用；过期租约恢复保持同来源，迟到worker先核对状态和令牌。HTTP不持数据库锁，未知回执不代表M1回滚。超过次数的未知结果保留M1_ACK_UNKNOWN，不伪装安全取消。

批准后取消、正常到期释放、人工恢复和真实占用成功链留Phase8；不提前实现M1接口、BPMN、Redis或分布式事务。

## 错误与验证

400 VALIDATION_ERROR：格式/必填/未知字段/分页；401 UNAUTHORIZED：用户身份；403 FORBIDDEN：当前角色/权限/范围或两级人选隔离；404 CLASSROOM_REQUEST_NOT_FOUND/RESOURCE_NOT_FOUND；409 CLASSROOM_STATE_CONFLICT/CLASSROOM_APPROVAL_EXPIRED；422 CLASSROOM_INVALID_INTERVAL/CLASSROOM_START_IN_PAST/CLASSROOM_UNAVAILABLE；503 PLATFORM_UNAVAILABLE/DATABASE_UNAVAILABLE/CLASSROOM_BUSY；500 INTERNAL_ERROR表示事务失败。

实际验证结果见阶段报告。真实D/M1/数据库测试验证权限、状态和当前M1拒绝路径；单独外部HTTP夹具只验证同步边界。目的/意见不进入日志，保留、申诉和组织政策仍待审，不把测试数据冒充校园真实运营。
