# Phase7：物业工单

本轮授权Phase7，基于Phase6 fa960e6，完成后停止等待Phase8。每个验证通过的可运行分项上传GitHub。

## 待审阅具体方案

1. D新增仅maintenance-service凭据可读的维修人员资格接口，只返回id/eligible，实时检查enabled与PROPERTY_STAFF，其他调用者拒绝。不新增角色/权限或seed。
2. M3报修需maintenance:report与STUDENT；物业主管需PROPERTY_MANAGER及maintenance:assign，按当前身份全局派单；被派人员需当前PROPERTY_STAFF，不能指派报修人自己。查询分mine/assigned/managed，在SQL内先限制可见范围，系统管理员无默认覆盖。
3. REPORTED→ASSIGNED→BLOCKING_ROOM→真实M1确认→IN_PROGRESS→WAITING_ACCEPTANCE；报修人拒绝验收返回IN_PROGRESS并保留原占用。报修人接受后持久排入RELEASE，确认释放才COMPLETED；失败SYNC_FAILED不假恢复。完成后本人可评价一次1–5分/500字。仅REPORTED/ASSIGNED可取消；开始同步后不无确认释放就取消。
4. 指派时due_at默认24小时；返修重新计时。活跃指派/维修/同步失败按due_at扫描，每轮期限只写一次ESCALATED系统事件，公开escalated字段；完整角色UI标记留Phase9。完成/取消/等待验收不继续维修超时升级。
5. M1客户端独立M1_MAINTENANCE_TOKEN，真实POST/DELETE，来源M3/MAINTENANCE/maintenance-ticket:ID、开放endAt=null；有限3次总尝试/30、60秒退避、4xx终止、30秒租约/令牌，HTTP出事务。当前真实M1仍403；成功流程通过明确外部响应夹具验证，不冒称Phase8成功。保留源DELETE两段路径，M3单一来源类型，不提前实现M1契约。
6. M3新增V2工单、事件、评价表；外部ID逻辑引用，无跨schemaSQL。状态保留源八项，以独立sync_operation区分BLOCK/RELEASE；接受时等待验收状态加RELEASE任务，确认前不可再拒绝或重复接受。

## 分项

- D资格读取、最小契约与真实测试。
- M3本地工作流、同步客户端、调度、契约与真实测试。
- 实际Compose保留卷升级、smoke、治理/状态页、最终GitHub SHA重建交付。

测试和运行结果执行后登记，不预填。政策、释放回执契约、既有预约冲突、未知回执人工补偿仍需后续review；不取消既有M1预约或改D座位状态冒充维修下架。

D分项已执行两轮真实clean verify。首轮新路径未加入内部安全白名单，3个新测试正确失败403；加入仅M3的SERVICE_MAINTENANCE授权后，51项全部通过，0失败/错误/跳过，UTC结束2026-10-26T23:58:30Z。此结果仅代表D分项，不预填M3通过。

M3功能分项已完成一次完整clean reactor，153项通过（D51/M1 49/M2 28/M3 25），0失败/错误/跳过，UTC结束2026-10-27T05:03:47Z。M3含10项真实D/M1 JAR/PostgreSQL工作流、5项D边界、5项明确M1响应边界、5项运行/隔离基线。正式OpenAPI校验及Controller逐一匹配为D37/M1 10/M2 6/M3 10。真实运行保留卷升级与最终SHA重建仍待完成，不以夹具证明M1封房成功。
