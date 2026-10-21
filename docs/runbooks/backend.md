# 后端骨架验证与独立运行

需要 Docker 和 Git。在仓库根目录执行：

```sh
./scripts/test-backend.sh
```

脚本使用 Maven 3.9.11 / Java 21 容器，注入当前实际 Git SHA、dirty 标记和 UTC 时间，执行四模块 `mvn verify`。16 个测试启动真实随机端口 Tomcat 并发送 HTTP，不 mock 被测逻辑。每模块生成可执行 JAR、Surefire 和 JaCoCo 报告，均被 .gitignore 排除。

有 Java 21 时，可在构建后直接运行其中一个服务：

```sh
java -jar services/platform-service/target/platform-service-0.1.0-SNAPSHOT.jar --server.port=18081
curl http://127.0.0.1:18081/actuator/health
curl http://127.0.0.1:18081/actuator/info
```

其他服务使用对应 JAR；同时独立运行时应指定不同端口。接口契约见 [runtime.md](../api/runtime.md)。Docker Compose 镜像与整体启动由 Phase 1 容器分项验收。

当前服务不连接数据库，不存在业务 API、默认登录用户或默认密码。MyBatis、PostgreSQL 驱动和 Flyway 只在父 POM dependencyManagement 中管理版本，Phase 2 再接入。
