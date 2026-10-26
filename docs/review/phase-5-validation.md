# Phase 5 签到、提醒和违约验收记录

执行日期：2026-10-26（Asia/Shanghai）。用户在 Phase 4 交付后回复“继续”，本轮只交付 Phase 5，结束后等待 Phase 6。Schema、公共契约和未定业务政策仍经草稿 PR 人工 review，不自动合并。

## 修改文件与设计

| 文件组 | 变化与用途 |
|---|---|
| M1 LifecycleController/Service/Mapper/Scheduler | 本人签到与违约查询、房间锁内签到/NO_SHOW/释放/事件、持久提醒领取/有限重试；两个独立调度任务 |
| M1 PlatformClient 与认证/安全配置 | 真实 D 验码和通知 HTTP；严格布尔校验、凭据分离、回执来源/接收人/时间校验，新增违约路径授权/CORS |
| M1 V3、D V4 | 唯一 checkin/violation、提醒与租约、明确系统事件；D 持久站内收件箱和来源唯一键 |
| D NotificationController/Service/Mapper、安全配置 | M1 专属幂等内部接收、本人收件箱；新接收与追加审计同事务，未知字段与超微秒时间拒绝 |
| 真实业务/恢复/客户端测试、公共数据库测试支持、test-backend.sh | 边界、竞争、未知回执重放、租约恢复/次数上限、终止错误、schema/版本；先 clean 再 verify |
| Nginx、状态页、数据库/网关检查脚本 | 违约路由、Phase 05 标识、真实卷升级/安全/一致性验收；不添加生产调时端点 |
| OpenAPI、API指南、架构/需求/伦理/手册 | D35/M1 10操作契约、实际完成范围、可复现命令和后续欠账 |

签到窗口草案为 `[T−15min,T+15min)`，仅本人且具备原有 reservation:create 权限。真实 D 按其当前业务日期校验对应房间码；HTTP 不占 M1 房间锁，随后取得 room_gate→预约锁并重新验证时间与状态。成功原子写 CHECKED_IN、唯一签到和实际人类事件；重复签到返回原 Booking，不重复写入，不保存或记录码。

`now >= T+15min` 且仍 CREATED 时终态为 NO_SHOW，原文“取消预约”解释为终止有效性，cancelled_at 保持 null。精确释放同一个 M1 来源、唯一违约和 actor=null 的系统事件同事务；账本不一致整体回滚。NO_SHOW 属于历史，可在旧区间结束前再预约新时段，仍重新读取 D 参数/资源和判冲突。不增加封号、积分或处罚。

D 站内通知按来源幂等：首次201、相同内容200、冲突409；仅 M1 内部凭据可写，用户只能看自己的列表。ACCEPTED 只指 D 持久入箱，不是邮件送达或用户已读。M1 每预约/提醒类型唯一，第一次窗口 `[T−15,T+10)`，第二次 `[T+10,T+15)`，过期不补发。独立扫描/发送任务默认各30秒、每批100，调度线程池2，D 超时不阻塞另一任务的 NO_SHOW 扫描。

发送采用30秒持久 claim lease/令牌，HTTP 在事务外；最多3次总尝试，仅网络/5xx退避30/60秒，4xx和无效契约终止。领取前重查预约与过期，签到/取消抑制待发项；已经在途的 D 请求不能和 M1 状态变化原子撤回。D 来源去重处理接收成功但回执丢失，不伪装跨服务事务。

## 实际验证

| 命令/操作 | 结果 |
|---|---|
| `./scripts/test-backend.sh`（clean verify） | 完整 reactor 退出0，102项通过：D43/M1 49/M2和M3各5；零失败/错误/跳过，结束UTC 2026-10-26T11:11:31Z |
| `pnpm --dir apps/web lint`、`test`、`build` | 全部退出0；6项测试通过，生产构建包含vue-tsc |
| 两份 OpenAPI 正式规范校验与 Controller 对照 | D35/M1 10操作全部匹配；Phase 4原操作保留 |
| 已发布迁移逐字节比较 | D V1–V3、M1 V1–V2、M2/M3 V1未修改，新增仅D V4/M1 V3 |
| 升级前 `check-database.py --phase4 --snapshot …` | 原D3/M1 2及其他V1、八账号、精确seed与账本一致性通过，保存非敏感指纹 |

五个应用镜像首轮构建成功：18b245e94378c33224b61b01a14b25b1b2ffc9af-dirty，构建UTC 2026-10-26T14:01:35Z。保留原卷强制重建后七容器healthy，D从V3升至V4、M1从V2升至V3，M2/M3保持V1；未执行down -v。实际nginx -t、Phase3/4回归与Phase5网关smoke全部通过。check-database.py --phase5验证八账号/四schema边界、审计、精确seed及签到/违约/提醒/回执一致性通过；原演示计数与密码指纹不变。

实际新签到/违约/通知路径CORS：明确本地来源200且匹配ACAO，未知来源403且无ACAO。实际D/M1日志未含私有密码、JWT/内部秘密、每日码值或BCrypt哈希，保留请求ID与路由模板。

短时开发卷smoke只读取本人列表并验证缺失/非本人权限、未知输入、尚未开放和取消后签到拒绝，最终取消本次创建预约。它不改真实Clock或制造checkin/violation/reminder行，不把空列表读取写成完整生命周期验收；成功签到/两次提醒/NO_SHOW和恢复的真实HTTP/PostgreSQL证据来自上述注入Clock测试。最终干净GitHub SHA仍待重建核对，不预填。

102项为本轮一次完整 clean reactor 的真实结果。M1新增16项生命周期、2项恢复及3项客户端边界；D新增6项通知业务。测试启动真实 D JAR、M1 HTTP服务和 PostgreSQL，不 mock 被测业务。M1可调Clock控制时间，D子进程仍按真实日期验码并签发JWT，测试通过真实D生成/取得当日码；不提供生产调时/触发器API。

覆盖 T−15包含/T+15排除、错房间/错误码、等待房间锁跨截止、重复/并发签到、签到与NO_SHOW竞争、两提醒阈值和多worker去重、实际D通知超时/未知回执后相同通知ID、4xx终止、过期租约恢复与第4次尝试禁止、NO_SHOW重复/并发、违约本人隔离、释放/异常账本回滚及NO_SHOW历史再预约。客户端有意错误响应只用于外部边界，不替代真实D业务集成。

## 失败与修复

- 首轮D43项中2项失败：非local版本断言仍为3、新通知未知字段被忽略。更新版本断言，并在新DTO局部拒绝未知字段，不改变全部D接口反序列化行为。
- 第二轮D通过，M1唯一业务失败为NO_SHOW已显示历史却仍被旧再预约守卫拒绝。统一终态历史守卫并重新完整验证。
- 独立审阅发现布尔响应可被Jackson从字符串/数字强制转换；改为JsonNode严格布尔校验。另拒绝数据库微秒精度以外的通知时间，避免同载荷重试产生精度冲突。
- 第三轮受到本地挂载生成目录中重复class文件及Input/output error影响，Spring上下文无法加载；未将其算作业务通过。测试脚本改为clean verify，清理旧产物后重新生成全部报告。
- 后续两次启动因会话Docker socket权限被拒绝，未执行测试；取得本机socket通信/网络权限后完整102项通过。无测试跳过或生产mock。

## 交付边界

已验证功能分项[18b245e9](https://github.com/Liinn0529/campus-resource-platform/commit/18b245e94378c33224b61b01a14b25b1b2ffc9af)上传[草稿PR #6](https://github.com/Liinn0529/campus-resource-platform/pull/6)，基于Phase4分支。功能分项 `feat(reservation): add check-in reminders and no-show handling`；运行与文档分项 `test(reservation): verify phase 5 runtime and handoff`。最终提交重建后的SHA/镜像/时间与证据记于仓库外交付报告，不预填本提交自身SHA。

违约仅本人查询预约/原因/时间，不增加管理员批量导出或处罚。保存/删除期限与申诉仍待审，ETH002保持PARTIAL，不能把缺少清理任务称作批准永久保存。正常COMPLETED触发、补签到、完整Web输入界面与E2E、M2/M3、通用远程审计、邮件/短信、CI/CD和云部署未交付。NO_SHOW的实际预约事件已记录，不冒充通用D远程审计已经实现。

保留原始文档、186稳定需求ID、15项OQ及Phase0–4历史报告。当前无GitHub Actions工作流，不把本地102项称为CI通过。追踪表48 DONE/104 TODO/34 PARTIAL。Phase 5完成后停止，等待明确确认再进入Phase 6。
