# Phase 3 公共层验收记录

执行日期：2026-10-22–25（Asia/Shanghai）。用户在 Phase 2 交付后确认继续，本轮仅交付 D 公共层。结束后等待 Phase 4，不自动合并 schema、权限政策或公共契约。

## 文件与设计

| 交付文件 | 实际变化 |
|---|---|
| `AuthenticationController/Service`、`IdentityMapper`、`PlatformSecurity`、`InternalTokenFilter`、`AccessPolicy` | BCrypt 登录/改密、15 分钟 HS256 JWT、issuer/audience/时间/签名验证；每次读取当前身份和权限；用户与内部服务身份分离 |
| `ManagementController/Service/Mapper` | 用户、固定七角色/十四权限、角色权限配置和显式房间范围；停用/改密令牌失效；并发保护最后有效系统管理员 |
| `SpaceController/Service/Mapper`、`CommonController/Service/Mapper` | 楼宇/房间/座位维护、启停、范围限制、参数、每日房间签到码、私网主数据和验码 |
| `AuditController/Service/Mapper`、API envelope/异常/请求日志类 | 修改与审计同事务、授权查询、请求 ID 关联；日志只记路由模板和必要元数据；不记凭据、哈希、签到码或请求正文 |
| `V2__platform_common_services.sql`、`V3__platform_reference_permissions.sql` | auth_version、房间范围、参数、每日码、追加审计；固定参考角色/权限；V1 保持未修改 |
| 四份新平台测试及原数据库/运行测试 | 真实 PostgreSQL/HTTP/JWT，日期/并发/撤权/越权等行为验证 |
| `compose.yaml`、`.env.example`、`init-env.sh`、Nginx、运行检查脚本 | 私有随机 JWT/内部凭据、显式业务时区；公开 D API，内部路由仍隐藏；实际运行与数据库验收 |
| `contracts/openapi/platform.v1.json`、`docs/api/platform.md`、运行手册与治理文档 | 32 个已实现操作的契约、可复现验证步骤、真实状态、未决政策和后续边界 |

SYSTEM_ADMIN 管理全局 D。ROOM_ADMIN 无默认授权，只能维护显式分配房间与座位、取得对应签到码，不能管理用户/角色/全局参数或任意移动资源。学生可查询空间与参数，但不能维护管理数据或取签到码。角色与权限从数据库实时读取，不信任旧 JWT 中的权限；密码和启用状态变化通过 auth_version 使旧令牌失效。

JWT 签名算法固定 HS256，issuer 为 `https://campus-resource.local/platform`，audience 为 `campus-web`；显式要求签发/生效/到期时间，到期边界为 `now >= exp` 即拒绝，未加入 refresh。内部三调用方使用独立随机 opaque 凭据，只有 reservation-service 可验码；用户 JWT 不授权内部接口，内部凭据不能登录为用户。生产密钥管理/跨模块完整认证仍在后续阶段审阅。

每日码按注入 Clock 和显式 ZoneId（本地 Asia/Shanghai）计算今日；8 位 SecureRandom 字符，room/date 数据库主键，同日并发返回同条记录，不支持无依据的当日轮换。学生不可取明文，内部验码只返回布尔结果，不完成预约签到。主数据采用维护/启停，尚无硬删除和有预约时的资源协调。默认最大时长为 4，D 已可读取/维护，M1 的应用规则留 Phase 4。

固定角色/权限和默认设置属于版本化应用参考数据，非 local 也存在；五个演示用户、两楼、四房、四十座仍仅 local seed。未增加生产默认用户或为了流程演示扩增用户。

## 实际命令与结果

| 命令/操作 | 结果 |
|---|---|
| `./scripts/test-backend.sh` | Maven verify 退出 0，49 项通过，0 失败/错误/跳过；真实 PostgreSQL、四个 JAR 和 JaCoCo 报告 |
| `pnpm --filter @campus/web lint`、`test`、`build` | 分别退出 0；6 项测试通过，生产构建包含 vue-tsc 类型检查 |
| 实际容器 `nginx -t`、`docker compose config --quiet` | 均通过；不输出私有环境值 |
| `./scripts/build.sh` | 五个应用镜像从源码构建成功；首轮元数据 `c5fd067012ea40c8a9163261e0bb9b68f4c71a19-dirty`，UTC `2026-10-24T13:03:02Z` |
| 升级前 `python3 scripts/check-database.py --snapshot <临时路径>` | 确认既有 Phase 2 四个 V1、账号隔离与最小数据，保存非敏感指纹 |
| `docker compose up -d --force-recreate --wait --wait-timeout 180` | 保留已有卷升级，D 自动迁移至 V3，其他仍 V1；七容器全部 healthy；未执行 down -v |
| `python3 scripts/check-database.py --phase3 --snapshot <临时路径>` | 八账号 TCP、四 schema、版本、app DDL/history 限制、审计 UPDATE/DELETE/TRUNCATE 拒绝和精确最小集合均通过 |
| 升级前后独立指纹/实体数量比较 | 五个既有密码哈希指纹不变；用户、角色、用户角色、楼宇、房间和座位数量保持不变；权限参考数据按 V3 增加 |
| `python3 scripts/smoke-phase-3.py --expected-commit c5fd067012ea40c8a9163261e0bb9b68f4c71a19-dirty --evidence <临时路径>` | 全部 PASS；实际网关登录、维护、RBAC、房间范围、今日码、审计及内部隔离验证通过 |
| 实际跨源 OPTIONS 请求 | 明确的本地来源返回 200 和匹配 ACAO；未知来源返回 403 且无 ACAO |
| 实际 D 容器日志检查 | 包含 requestId/userId/method/路由模板/status/latencyMs；没有当前私有密码、JWT/内部秘密或数据库中的签到码值 |
| OpenAPI 3 规范校验、Controller 对照和治理检查 | 规范校验通过，32 操作与真实 Controller 一致；原始归档/V1 未变，186 ID 与 15 OQ 保留，格式/链接/私有值排除检查通过 |

smoke 使用实际私有环境登录五个演示身份，不打印密码、令牌或签到码。通过 API 临时改房间名、插座、设置和房间范围，验证后逐项恢复；额外保留的只有真实操作审计与今日码，没有新增用户或资源。撤去范围后，房间管理员原 JWT 立即收到 403。内部路径即使携带内部凭据通过公共网关也返回 404。

49 项包含 D 运行/seed 7、非 local 1、认证 12、资源/签到码 7、管理/审计 7，以及其他三个服务各 5。覆盖真实 BCrypt、错误/停用登录、缺失/篡改/无签名/过期/缺少 audience/错误版本 JWT、严格到期边界、改密和撤权、最后管理员并发保护、管理权限、时区午夜切换、签到码并发幂等、内部身份与事务审计。测试不 mock 被测逻辑；可调测试时钟只控制时间边界。非 local 不生成演示用户/空间。

JaCoCo 当前 D 模块行覆盖率 91.30%，指令 91.65%，分支 71.03%；这是实际报告值，未声称已配置 GitHub 覆盖率门禁。

## 发现并修复的问题

- 当前会话未获 Docker socket 访问时，测试无法启动；取得所需沙箱权限后运行，不把权限失败视为测试通过。
- MyBatis INSERT RETURNING 的 Select 注解缺少命名 value，首次编译失败，修正后通过实际编译。
- 原 issuer 字符串不符合 Spring claim URL 转换要求，首轮平台测试 32 通过/1 错误；改为明确 HTTPS issuer 后全部通过。
- 审阅发现缺少 audience 的令牌可能触发空值异常，且框架零偏差时间校验不拒绝恰好等于 exp；增加明确验证和真实签名回归测试，均通过。
- 最后管理员并发测试允许安全拒绝为 409 或 401（请求可能在另一请求停用用户后才认证），同时强制只有一个成功、数据库始终保留一名有效管理员；未用客户端时序冒充服务端并发保障。

## 交付与未完成范围

第一批功能代码已提交 `c5fd067012ea40c8a9163261e0bb9b68f4c71a19`，草稿 [PR #4](https://github.com/Liinn0529/campus-resource-platform/pull/4) 基于未合并 Phase 2 分支。第二分项使用 `test(platform): verify compose upgrade and phase 3 handoff`，包含运行验收脚本、页面标识及最终文档。交付前按最终 GitHub SHA 再构建并检查，最终 SHA/时间/镜像及再次验收结果记录于仓库外报告，避免预填自身提交。

Phase 3 完成后停止。预约/占用/房间锁、预约签到、审批、工单、完整角色前端、通知投递、跨服务业务审计关联、CI/CD 和云部署未交付。OQ-04/10/12/14 的本阶段方案仍待人工 review，未删除 15 项问题或把未来组织/业务/运维政策称为已解决。原始归档、186 个稳定需求 ID、历史 Phase 0–2 报告和已发布 V1 均保留。

追踪表当前 28 DONE / 130 TODO / 28 PARTIAL / 0 BLOCKED。阶段适用功能验收完成不表示后续模块、完整 UI、GitHub CI 或人工审阅已完成。
