# Phase 1 后端分项验证

2026-10-21：执行 `./scripts/test-backend.sh`，Maven 3.9.11 / Eclipse Temurin Java 21.0.9 容器中 `mvn verify` 退出 0，Reactor 五个模块全部 SUCCESS。四服务 JAR 实际完成 Spring Boot repackage，四份 JaCoCo 报告已生成。

| 服务 | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| platform-service | 4 | 0 | 0 | 0 |
| reservation-service | 4 | 0 | 0 | 0 |
| classroom-service | 4 | 0 | 0 | 0 |
| maintenance-service | 4 | 0 | 0 | 0 |

每个 RuntimeEndpointsTest 以真实随机端口验证：health 的 UP 与无细节泄露、info 顶层四字段及打包信息、未实现/敏感路径 403、POST health/info 403。构建校验要求 Java 21、Maven 3.9.x 和完整 Git SHA。

本次源码未提交，因此实际打包元数据为 `gitCommit=0f99e2d6005f0d4acdc1eae5ff1de5e5eb210235-dirty`、`buildTime=2026-10-21T14:14:39Z`。已直接读取生成的 build-info.properties 核对，并由测试请求真实 info 响应核对；最终阶段容器会按最终 GitHub 提交重新构建并 smoke 核验。

编译提示测试中的 Map 原始类型有 unchecked 使用；测试运行出现 Spring Boot 测试依赖 Mockito/Byte Buddy 动态 agent 提示。这些不是失败，当前没有将“无 warning”写为结果。没有声称完整业务覆盖率、Testcontainers、RBAC/JWT 或 GitHub CI 已完成。

关联 REQ-D-008、REQ-ENG-007；REQ-PH-001 的七容器验收仍待完成。
