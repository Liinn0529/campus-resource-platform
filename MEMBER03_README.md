# 成员三交付：教室审批、物业工单与六条业务路由

基线：`cf265a7ca2ea2e6b7807a1acdc762f2beaff0e34`；分支：`feature/member-03-workflows-web`。

本次只实现任务书中成员三的代码、必要迁移、测试和交付说明。未扩展M1账本、D权限/人员接口、成员二的14个页面、CI/CD、云部署或Agent。

## 已实现

- M2：已批准申请在开始前由本人取消，排队释放；正常到期释放。只有M1原占用ID、来源、房间、时间和RELEASED全部匹配后才写CANCELLED/COMPLETED。
- M2：尚未尝试创建占用的pending可安全取消；已有尝试、未知结果或正在同步的请求保持真实状态，不能直接取消。
- M2/M3：显式人工恢复接口，重新调用真实幂等HTTP；要求当前审批人房间范围/物业主管权限及必填原因。保留自动尝试次数及历史，租约防并发，迟到回执不能覆盖新结果。
- M2/M3：业务事件与审计outbox同事务保存，经HTTP投递到D；正文、凭据不进入消息，失败与重试状态持久化。
- 六个Vue业务路由、学生详情抽屉、审批意见、指派/开始/完工/验收/返修/单次评价、升级标记、分页与错误提示。
- 两份OpenAPI、M2/M3各一份V3迁移、真实PostgreSQL流程测试与真实HTTP客户端边界测试。

## 启动与接入

在任务书要求的Git工作区、Docker/Compose、Node24.15–24.x和pnpm11.25环境中：

```sh
export COMPOSE_PROJECT_NAME=campus-member-03
./scripts/dev-up.sh
pnpm install --frozen-lockfile
pnpm --filter @campus/web exec vite --config vite.member03.config.ts
```

浏览器打开 `http://127.0.0.1:5173/tests/member03/index.html`。这是**test-only隔离验收入口**，登录和业务都请求真实D/M2/M3，不含生产mock，不创建额外账号。密码使用本人私有.env中的现有演示密码。若共享API未提供，页面显示实际错误。

成员二维护的生产App/main/router/api/session/global CSS原样保留。正式集成只需由成员二：

1. 将 `workflowRoutes` 导入唯一router，注册6条路由并沿用公共认证guard。
2. 在已有app、Pinia和会话初始化后调用：

```ts
import { installWorkflows } from './workflows/context'
import { api } from './lib/api'
import { useSessionStore } from './stores/session'
installWorkflows(app, api, useSessionStore(pinia))
```

页面通过该桥接使用成员二的同一个Axios实例和会话，没有自己的生产登录或token存储。共享api的baseURL保持`/api/v1`，AxiosResponse.data内保留后端envelope。Element Plus使用公共入口的注册与样式。

## 测试

```sh
./scripts/test-member03.sh
```

该命令用现有Docker后端测试入口跑成员三的数据库/客户端测试，再做前端lint、typecheck、25项测试和包含六页面的构建。完整原有后端回归另运行任务书中的 `./scripts/test-backend.sh`。

本次本机没有Docker，已用Java21和隔离PostgreSQL16运行选定后端测试；原有完整Docker回归、真实M1成功占用/释放以及生产共享入口的浏览器闭环尚未验收。详见 `docs/member03/VALIDATION.md`。

## 还需要其他成员交付或审核

- 成员一：M1真实创建/释放接口、服务凭据与冲突政策；审核M2批准后取消/到期及手动恢复契约。
- 成员二：最小人员选择接口、审计接收接口、共享api/session及14条路由；注册本次6路由。
- 成员四：复验两条真实浏览器业务闭环、既有预约不被误释放、完整20路由；审核接口和schema。

本分支保留PARTIAL/TODO的真实联调项，不将HTTP响应夹具和隔离入口计为全平台完成。交付细节及逐项需求在 `docs/member03/HANDOFF.md`。
