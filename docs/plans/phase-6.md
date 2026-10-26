# Phase 6：M2 整教室申请与两级审批

用户在 Phase 5 交付后回复“继续”，本轮授权 Phase 6，完成后停止等待 Phase 7。分支 feature/phase-6-classroom 基于 c6948c955f79bfaa3d6e96a24c7fd1edfbdf9a9a。已验证的可运行分项分别上传 GitHub，使用堆叠草稿 PR 人工 review，不自动合并。

## 具体方案（待审阅）

- D 增加当前本人 `/api/v1/me/room-scopes` 读取，要求当前 classroom:approve。原 user_room_scope 允许 ROOM_ADMIN 或 DEPARTMENT_ADMIN 显式获配房间；失去两角色时清除范围。D 原空间维护规则不变，部门审批范围不授予空间维护/签到码权限；不虚构部门组织模型或新增 seed 用户。
- M2 每次通过真实 D HTTP 获取当前身份、权限和本人范围，主数据使用 M2 专属 D 凭据。不共享 D JWT 签名秘密，不读 D 表，不信任客户端提交申请人或审批阶段。
- STUDENT 且 classroom:apply 可申请启用 CLASSROOM。时间带 offset、精度不细于微秒、未来开始且结束晚于开始；不把 M1 的4小时最大值擅自应用到整教室申请。目的和审批意见限制长度，日志不收正文。
- 提交为 SUBMITTED，第一级 ROOM_ADMIN 待审批；第一级批准后为 APPROVING 并激活第二级 DEPARTMENT_ADMIN。每级要求当前对应角色、classroom:approve 与显式房间范围。禁止本人审批、同一个人完成两级；SYSTEM_ADMIN 无隐式越级覆盖。
- 每级激活时持久化期限，配置默认24小时，并封顶预约开始时刻。审批、拒绝、撤销和超时在本地申请行锁后重新读取 Clock；达到期限的用户操作409，独立扫描将未完成审批置 AUTO_CANCELLED，事件标明系统来源。
- 申请人只可在开始和当前审批期限之前撤销 SUBMITTED/APPROVING。一旦第二级批准并排入同步，APPROVED_PENDING_SYNC/SYNC_FAILED 可能存在已提交但回执未知的远端占用，本轮不无确认释放就改成取消。批准后取消、正常到期释放和人工恢复留 Phase 8。
- 第二级批准只写 APPROVED_PENDING_SYNC，再由持久领取任务通过真实 HTTP 调 M1。M2→M1 使用独立 M1_CLASSROOM_TOKEN，与 D 的 PLATFORM_CLASSROOM_TOKEN 不复用；固定 origin、禁重定向，不转发用户JWT给M1。
- 来源键稳定，三次总尝试、网络/5xx退避30/60秒、4xx/坏契约终止。30秒持久租约/令牌保护重启和迟到 worker，HTTP 不持数据库锁，回执必须匹配来源、房间、区间与 ACTIVE 状态；不能将任意200当作占用成功。默认超时/同步独立30秒任务、每批100、池2。
- 当前 M1 没有 Phase 8 的 `/internal/v1/room-blocks`，直连实际返回403。生产调用应诚实进入 SYNC_FAILED，不能添加生产假回执或提前实现 Phase 8 来得到 APPROVED。成功回执分支可通过明确标记的 HTTP 边界夹具测试，不能称为实际 M1 集成完成。
- M2 追加 V2 四表 classroom_request、approval_step、approval_action、classroom_event，状态保留全部八项；已发布迁移不改写。所有业务引用为逻辑ID，审批动作与步骤/申请/事件同事务；不引入BPMN、Redis或分布式事务。

## 分项及验收

1. D 本人审批范围读取及部门管理员显式范围，独立真实测试通过后先提交。
2. M2 身份/空间 HTTP、申请/两级审批/拒绝/撤销/超时/持久同步、契约及真实测试，验证后提交。
3. 运行脚本、治理/伦理/手册和状态页；保留原卷升级，按最终 GitHub SHA 重建并核验后交付。

重点：角色/当前权限/范围隔离，无自批/越级/两级同人；并发审批/撤销/超时只有一组一致结果；锁后时间守卫；未知回执不假取消；真实 M1 失败不能显示 APPROVED；重启保留同步尝试与事件。完整角色 Web/E2E 留 Phase 9/10，M3 属 Phase 7，M1 占用集成属于 Phase 8。

目前实现与测试进行中，未执行结果不预填；REQ-M2-006/008/010 等真实封房/释放验收继续保留欠账。

D 范围分项已实际执行 `./scripts/test-backend.sh -pl services/platform-service`：clean verify退出0、48项通过，0失败/错误/跳过，UTC结束2026-10-26T15:36:05Z。该结果仅代表D分项，不代表尚在实现的M2或整个Phase6通过。

M2功能分项已通过一次容器内部Linux工作区的完整clean reactor：130项（D48/M1 49/M2 28/M3 5），0失败/错误/跳过，UTC结束2026-10-26T15:59:03Z。14项工作流测试使用真实D/M1/PostgreSQL，其中真实M1拒绝同步返回403；另4项明确标记的M1响应边界夹具只验证回执/重试/租约，不代表Phase8集成。5项D客户端边界及原5项运行/数据库基线保留。原卷升级和最终版本重建仍待完成。
