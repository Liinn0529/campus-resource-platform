# 成员一验证与交付记录

日期：2026-10-27（Asia/Shanghai）。基线`cf265a7ca2ea2e6b7807a1acdc762f2beaff0e34`；分支`feature/member-01-occupancy`。记录只证明明确运行过的本地检查，不是非作者审核、GitHub CI或云验收。

## 环境与过程

- 在现有Ubuntu-20.04 WSL2安装Docker Engine 26.1.3、Compose 2.27.1、Buildx 0.14.1、Python3.9及Java21；后端实际使用项目Maven 3.9.11 / Temurin21容器和真实PostgreSQL16 Testcontainers。
- Docker Hub直连超时，使用本机镜像代理取得原镜像标签。Maven额外snapshot仓库和一次DNS失败影响首次依赖下载，调整本机仓库配置并重试网络请求；没有为通过测试删掉业务断言。
- 前端使用独立Node24.22.1及pnpm11.25.0，没有改Windows全局Node20或Java配置。
- 私有.env由现有init-env生成；Compose配置校验通过；密码和服务token不写入本记录。

## 验证结果

第一轮完整`./scripts/test-backend.sh -U -Daether.connector.http.requestTimeout=30000 -Daether.connector.http.connectTimeout=10000`构建成功，报告合计173项：D51、M1 67、M2 29、M3 26；失败/错误/跳过均0。补充审计201回执测试和最终配置后，最终`./scripts/test-backend.sh`再次BUILD SUCCESS，**174项全部通过：D51、M1 68、M2 29、M3 26；失败0、错误0、跳过0**。最终Maven运行6分17秒，日志结束于2026-10-27 23:26:04（Asia/Shanghai）。

前端`pnpm install --frozen-lockfile`、`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`已运行通过，6项测试通过。原始前端日志位于本机`.local/frontend-validation.log`，后端最终日志位于WSL`/root/campus-backend-final.log`，XML报告由脚本复制到各服务`target/surefire-reports/`。

四份`contracts/openapi/*.json`通过openapi-spec-validator校验；`git diff --check`通过；交付文件未检出本机.env的真实秘密值。V1–V3没有被改写。

四个后端镜像通过项目原`services/Dockerfile`构建。原前端Dockerfile在下载pnpm时发生网络失败，两次未通过；本机使用已经通过上述检查的同源dist和原Nginx配置构建等价运行阶段镜像（本机`.local/web-image`），没有修改成员二的前端源代码或把这次绕行算成原Dockerfile通过。

`COMPOSE_PROJECT_NAME=campus-member-01 docker compose up -d --no-build --wait --wait-timeout 180`退出0，七容器healthy，只有网关发布127.0.0.1端口。本机独立数据卷未清空。

```sh
COMPOSE_PROJECT_NAME=campus-member-01 python3.9 scripts/smoke-member-01.py \
  --expected-commit cf265a7ca2ea2e6b7807a1acdc762f2beaff0e34-dirty --restart
```

上述命令实际退出0：七服务health/info与真实dirty构建版本、网关只读、内部路由隐藏、Web静态产物/路由检查均通过；内部身份、全房座位不可约、M1实际重启后同ID重放、已释放来源不复活、M2旧释放不误恢复M3、维修精确释放均通过。smoke仅在本机演示环境通过真实接口创建带`member01-smoke:`来源的测试占用，结束已释放本次全部来源，保留历史；这与上文真实M2/M3业务流程测试分开记录。原始输出在`.local/member01-smoke.log`。

系统自带Python3.8不满足项目要求，曾因缺zoneinfo失败，安装Python3.9后按上面命令通过。WSL后台会话保持本机演示环境运行，访问http://127.0.0.1:18080/（仍为既有状态页）。

## 哪些是实链路，哪些是边界测试

- D身份/角色/房间、M1账本、M2两级批准和M3维修流程：真实服务进程、HTTP、PostgreSQL。M2批准确认占用，第二个冲突来源409且不重试；M3开始确认占用，完工/拒绝验收保持占用，接受验收确认同一ID已释放才COMPLETED；另一个工单冲突不误释放。
- M1并发：首次房间锁、三种占用竞争、同来源重复、跨房间冒用同来源；真实数据库唯一键与HTTP断言，不mock被测服务。
- 正常完成：真实D签到与M1数据库，注入Clock验证endAt边界、重复扫描、未签到不误完成、账本损坏回滚。生产开关默认false，政策未审定不能声明已正式启用。
- 审计：真实M1事件/触发器/投递状态和HTTP客户端，D响应是明确的测试边界夹具，覆盖30/60秒、最多3次、4xx/坏回执终止、网络故障、过期claim恢复和尝试历史。**D生产审计接收尚未交付，因此不能称为真实D审计联通。**

## 待审核及交接

待成员三/四复核来源生命周期与DELETE路径契约；待项目负责人确认正常完成、违约/事件保存与申诉规则；待成员二提供D审计接收与空间停用/移动政策；待成员三完成M2批准取消/到期释放及人工恢复。详情见[实施说明](../plans/member-01-phase8.md)。没有替这些人填写通过结论。

Git上传预检查使用`git push --dry-run`，当前因没有GitHub登录凭据失败（未修改远端）。本地也没有配置提交姓名/邮箱，不能擅用仓库作者身份。代码及可运行证据准备好后，需要使用本人的GitHub登录和提交署名再提交上传；当前不宣称已推送、已建PR或已合并。

## 建议审核顺序

1. RoomBlockController/Mapper及InternalAuthenticationFilter：来源身份、锁、唯一键、释放范围。
2. V4、LifecycleService、AuditDeliveryService：向前迁移、完成守卫、事务/HTTP边界和有限重试。
3. 新增/调整测试与OpenAPI：真实成功/失败、已释放重放、微秒精度、D未接入边界。
4. 依赖与开关：明确未交付部分，确认策略后再启用，不把PARTIAL提前改DONE。
