# Phase 6 实际验收记录

范围：用户在Phase5交付后授权继续Phase6，本轮实现M2整教室申请、审批、批准、拒绝、前置撤销和超时；完成后停止等待Phase7。原文允许测试替代M1响应，生产必须真实HTTP。M3、M1封房/释放接口、完整角色Web及CI/CD分别属于Phase7/8/9/11，未提前实施。

## 文件与设计

- D：ManagementService允许ROOM_ADMIN/DEPARTMENT_ADMIN显式房间范围，任一角色保留范围；SelfScopeController只返回当前本人审批范围。原空间写权限不扩大，五个seed用户不扩充，D维持V4。
- M2：ClassroomController/Service/Mapper及V2四表保存申请、两级步骤、动作、事件；事务锁内复核状态/Clock。真实PlatformClient获取当前身份/权限/范围和启用教室，不读D schema、不共享JWT签名秘密。
- 两级顺序ROOM_ADMIN→DEPARTMENT_ADMIN，各需当前角色/权限/范围；禁止自批、同人两级和SYSTEM_ADMIN越级。申请目的/意见限500，不输出正文或内部claim凭据。每级激活期限默认24小时并封顶开始时刻，为待人工review草案。
- 第二级只到APPROVED_PENDING_SYNC；真实OccupancyClient使用独立M1凭据，固定origin/禁重定向。最多3次总尝试、网络/5xx退避30/60秒、4xx/坏回执终止；30秒持久租约/令牌防迟到覆盖。HTTP不持本地数据库锁，独立超时/同步任务每30秒、池2。
- 仅本人在未过期SUBMITTED/APPROVING可撤销。同步后可能存在远端已提交但回执未知，未经确认释放不能假取消。真实封房、批准后取消/到期释放和未知提交补偿留Phase8，不能称已交付。
- 运行：Compose/Nginx新增M2路由与独立凭据，init-env保留旧值；smoke-phase-6.py验证真实网关并恢复临时授权；check-database.py --phase6核对schema隔离、两级/动作/事件一致性。前端只更新运行检查页范围提示，未实现完整业务UI。

## 实际命令与结果

在仓库根目录执行，Docker使用本机现有引擎；私有.env不上传、不输出。以下结果来自实际日志和Surefire，不代表GitHub CI。

```sh
./scripts/test-backend.sh -pl services/platform-service
./scripts/test-backend.sh
pnpm lint
pnpm test
pnpm build
./scripts/build.sh
docker compose up -d --force-recreate --wait --wait-timeout 180
python3 scripts/smoke-phase-6.py --expected-commit <实际构建SHA> --evidence <证据路径>
python3 scripts/check-database.py --phase6 --snapshot <证据路径>
docker compose exec -T nginx nginx -t
```

D分项clean verify48项通过，UTC结束2026-10-26T15:36:05Z。最终完整clean reactor130项通过（D48/M1 49/M2 28/M3 5），0失败/错误/跳过，UTC结束2026-10-26T15:59:03Z。M2其中14项真实D/M1 JAR+PostgreSQL工作流、5项D客户端响应边界、4项明确标记的M1响应边界、原5项运行/隔离基线。覆盖顺序/权限/本人隔离、自批/同人两级、即时撤权、并发唯一决定、锁等待跨期限、每级超时、损坏第二步原子回滚、开始后拒绝同步、租约恢复/耗尽和回执核对。精确时间用注入Clock，没有生产调时端点。

前端6项通过，ESLint及vue-tsc/Vite构建通过。D36/M1 10/M2 6个OpenAPI操作通过正式openapi-spec-validator校验并与Controller方法逐一匹配。186个需求ID和15个OQ保留；原文归档SHA256不变，所有已发布migration（含首次上传的M2 V2）逐字节不变。

首轮Compose基于b56f96ce-dirty、UTC构建2026-10-26T16:03:13Z，七容器healthy；保留原卷将M2 V1升V2，D4/M1 V3/M3 V1不变，原五用户密码指纹及最小集合不变。没有down -v，没有操作其他项目容器。实际提交/本人详情/范围待办、两级审批、拒绝、前置撤销和即时撤权通过；真实M1调用403后保存SYNC_FAILED、attempt1、无假APPROVED或M2占用，M1座位仍可用。smoke仅临时为原第二学生赋部门角色与范围，finally独立恢复角色/范围，结束仍5用户/5用户角色/0范围。

首轮保留4申请、8步骤、3审批动作、11事件，状态为2 CANCELLED/1 REJECTED/1 SYNC_FAILED；保留原预约并增加本次回归的真实取消历史，总13预约/13释放占用/26事件，0活跃。数据库账号隔离/DDL历史保护、审计不可改写、M1账本及M2一致性检查通过。M2提交/批准及D本人范围的可信CORS预检200且回显白名单origin，不可信origin403无allow-origin。实际Nginx配置测试通过。运行日志按私有环境值、当日码、JWT和BCrypt模式扫描，未发现泄漏；申请目的/意见不入日志。

最终分项提交后需从实际GitHub SHA重建，保留卷重建后的完整SHA、树、构建时间、七容器健康、第二轮smoke/数据库快照与截图写入仓库外交付报告和phase-6-runtime-evidence.json，以避免在提交内容中循环自引用SHA。首轮结果不冒充最终干净版本证据。

## 真实失败与修复

早期完整测试有一处断言把SQL Long与Integer直接比较，修为Number.longValue后129项通过。代码复核发现审批预检在事务外读取pending step可能遇并发关闭产生500，改为按当前申请状态做初步角色检查，实际步骤仍在行锁内验证，并增加损坏第二步回滚测试。随后宿主同步目录产生重复class和不可删除目录，Maven clean出现I/O错误；未跳过clean或断言。test-backend.sh改为仅归档Git非忽略公共源码到容器内部Linux工作区，独立clean verify，导出JAR/Surefire/JaCoCo XML；最终130项完整通过。私有.env与宿主target不进入源码归档。

## Git与未完成项

本轮分支feature/phase-6-classroom基于Phase5最终c6948c955f79bfaa3d6e96a24c7fd1edfbdf9a9a。[草稿PR #7](https://github.com/Liinn0529/campus-resource-platform/pull/7)基于Phase5分支，已上传D范围06a13a7d、M2功能b56f96ce；运行脚本/治理/交付第三分项验证后上传，最终SHA在仓库外交付报告记录。不自动合并，不声称分支保护或CI已配置。

追踪57 DONE/91 TODO/38 PARTIAL。本地M2审批功能对应项及Phase6标为阶段适用DONE；M2-006/008继续PARTIAL、M2-010继续TODO，真实M1封房和释放属于Phase8。外部响应夹具不是M1跨模块成功。正常COMPLETED、数据保留/申诉、组织政策、批准后补偿、完整Web/E2E、CI/CD和部署仍保留。schema/公共契约、每级24小时等政策待具体草稿人工review；测试/提交不替代批准。Phase6结束停止等待Phase7。
