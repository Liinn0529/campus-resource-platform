# Phase7实际验收记录

用户在Phase6交付后授权Phase7，后续补充要求“先做完Phase7，再整理五人分工文档”。本阶段实现M3工单；五份文档属于后续交接，不授权提前编写Phase8业务。分支feature/phase-7-maintenance基于fa960e6，草稿[PR #8](https://github.com/Liinn0529/campus-resource-platform/pull/8)基于Phase6分支，未合并。

## 文件和设计

D新增MaintenanceStaffController/Mapper，内部安全白名单及SERVICE_MAINTENANCE，仅返回id/eligible；当前enabled且PROPERTY_STAFF才可派，用户或其他服务无查询授权。不新增权限/角色/seed，D仍V4。M3使用真实PlatformClient读取当前用户、房间和人员资格；无D签名秘密、跨schema查询或客户端指定报修人。

M3新增MaintenanceController/Service/Mapper、V2三表、真实OccupancyClient、远程认证/模板日志和两类独立调度。十操作包括拒绝验收，八状态全保留。本人报修/取消/验收/评价，当前主管派单，当前被派物业人员开始/完工；SYSTEM_ADMIN无默认覆盖。验收后先排RELEASE，匹配RELEASED才完成；拒绝验收回IN_PROGRESS并保留占用。每次指派/返修期限默认24小时，每周期一次升级事件、公开escalated；完整角色UI留Phase9。

BLOCK/RELEASE使用独立M1凭据、稳定来源、开放endAt，最多3次总尝试/30、60秒退避、4xx/坏回执终止、30秒持久租约/令牌；HTTP出事务，响应核对来源/房间/时段/占用ID/状态。M1真实接口在Phase8尚未提供，当前403应SYNC_FAILED，不能冒称维修下架、恢复或完成。同步后的取消与未知回执人工恢复仍待Phase8。

Compose/Nginx新增M3业务路由、D/M1依赖与独立凭据；init-env保留原值。check-database.py --phase7与smoke-phase-7.py追加真实运行检查，前端仅更新运行页范围。契约四份及架构/需求/伦理/手册同步；原文、原REQ ID/15OQ和已发布迁移保留。

## 实际命令

```sh
./scripts/test-backend.sh -pl services/platform-service
./scripts/test-backend.sh -pl services/maintenance-service
./scripts/test-backend.sh
pnpm lint
pnpm test
pnpm build
./scripts/init-env.sh
./scripts/build.sh
docker compose up -d --force-recreate --wait --wait-timeout 180
python3 scripts/smoke-phase-7.py --expected-commit <实际构建SHA> --evidence <证据路径>
python3 scripts/check-database.py --phase7 --snapshot <快照路径>
docker compose exec -T nginx nginx -t
```

D子集51项通过，UTC结束2026-10-26T23:58:30Z。M3首轮15项通过后加入额外边界测试。完整clean reactor153项通过（D51/M1 49/M2 28/M3 25），0失败/错误/跳过，UTC结束2026-10-27T05:03:47Z。M3含10真实D/M1 JAR/PostgreSQL工作流、5 D响应边界、5明确M1响应边界、5运行/数据库基线。覆盖报修可见范围、当前主管/人员权限、即时撤权、真实M1拒绝、非法跳步、并发start/cancel唯一赢家、due_at严格边界和重复升级、返修新周期、释放失败不假完成、一次评价、30/60退避、回执错误/重定向、租约恢复/耗尽无第四次。

成功封房/释放由明确外部HTTP响应夹具驱动真实M3库和D身份，只证明客户端与本地后续逻辑，不能声称实际M1已集成。实际开发卷不改时钟、旧业务时间或插入假维修状态；所有严格时间边界由可注入Clock测试。前端6项、ESLint、vue-tsc/Vite构建通过。正式OpenAPI校验及Controller逐一匹配为D37/M1 10/M2 6/M3 10。

首轮保留原卷，构建9aada88d-dirty/UTC2026-10-27T05:06:01Z，M3 V1→V2，D4/M1 V3/M2 V2不变；七容器healthy，原seed及五用户密码指纹不变。真实网关先回归Phase6，随后四次报修、当前主管派单/人员资格、前置取消、即时撤权及真实定时M1 403→SYNC_FAILED通过。临时为原房间管理员加主管、临时撤销原物业人员角色，finally恢复；结束5用户/5用户角色/0范围。首轮4工单/13事件/0评价，3 CANCELLED/1 SYNC_FAILED，无假IN_PROGRESS/COMPLETED/M3占用；M1保留19取消/38事件，M2 12申请/33事件。

数据库八账号/四schema、DDL/历史权限和审计保护、M1/M2/M3账本及事件一致性检查通过，Nginx配置与M3可信CORS200/不可信403通过。最终干净GitHub SHA、再次保留卷重建、实际日志秘密/正文扫描、运行证据与截图由仓库外交付报告记录，避免提交内容循环引用自身SHA；首轮结果不冒充最终版本。

## 失败与修复

D首轮3个新测试因内部白名单遗漏均403，加入仅M3的SERVICE_MAINTENANCE路径授权后51项完整通过。升级前数据库快照首次被Compose拒绝，原因是新M1_MAINTENANCE_TOKEN尚未生成；执行更新后的init-env补齐，保留旧秘密后快照通过。M3首轮15项及最终完整153项均通过，无跳过失败测试。测试运行在容器Linux源码工作区，公共Git源码不含.env/target，沿用Phase6对宿主同步目录I/O问题的修复。

## 交付与剩余

已验证D分项e241e10d和M3功能9aada88d分别上传；运行/治理第三分项验证后上传，最终SHA记录于仓库外报告。追踪66 DONE/78 TODO/42 PARTIAL，186REQ及15OQ保留。M3-003/005/009与ETH007继续PARTIAL，M3-012仍TODO：真实维修封房/恢复、未知回执补偿和冲突告知在Phase8，完整UI标记在Phase9，CI在Phase11。本地验证不等于GitHub CI，分支保护和required checks未在本轮配置。

政策如24小时期限、区域组织、保留/申诉及释放回执是具体草稿，人工review后才能记录获批。既有预约不静默取消，维修失败不改D座位状态。Phase7完成后停止，按用户要求另交五份分工文档；不执行Phase8业务。
