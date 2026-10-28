# 成员一 Windows / WSL 开发

项目位于`C:\Users\DELL\campus-member-01`，WSL为Ubuntu-20.04。Docker Engine和Compose安装在WSL中，Windows终端直接运行docker可能仍找不到命令。

从PowerShell进入项目并执行测试：

```powershell
wsl -d Ubuntu-20.04
```

然后在WSL终端运行：

```sh
cd /mnt/c/Users/DELL/campus-member-01
docker version
docker compose version
./scripts/test-backend.sh
```

测试脚本用Maven 3.9.11/Java21容器，与Windows全局Java无关；WSL另安装Java21用于排查。Docker Hub直连超时时，本机使用镜像代理下载对应原标签，再标记为脚本所需标签。此为本机网络设置，不修改项目镜像来源。Maven缓存卷中的本地settings将依赖仓库映射到Maven Central，避免依赖声明中的额外snapshot仓库拖慢解析。

启动本地环境时保持隔离项目名：

```sh
export COMPOSE_PROJECT_NAME=campus-member-01
./scripts/dev-up.sh
docker compose ps
```

首次init-env生成私有`.env`，不要上传或截图其中密码/token；默认访问`http://127.0.0.1:18080/`。现有前端仅状态页，成员一功能主要通过API和测试演示，不能称为完整业务Web。不要使用`down -v`清理保留业务历史的卷。

正常完成/审计投递开关默认false；审核与依赖满足后在私有`.env`设置对应true，并重建容器配置。只改.env而不重建容器不会更新已有进程。关闭审计发送时事件仍持久积累，不能宣称已送达D。

Git提交上传需要本人GitHub登录和该仓库写权限；本机身份未配置时应使用本人真实身份，不复制仓库作者身份。


## 本机已经构建的环境

当前七个容器已启动，地址`http://127.0.0.1:18080/`。WSL后台保活进程PID记在本机`.local/wsl-keepalive.pid`；停止演示时先在WSL执行`COMPOSE_PROJECT_NAME=campus-member-01 docker compose stop`，再核对并关闭该PID的wsl进程。不要关闭其他人的WSL任务或清空数据库卷。

本机前端Dockerfile下载pnpm受网络影响；已用独立Node24.22.1完成锁定安装、lint、类型检查、测试与build，再用该dist和原Nginx配置构建前端运行镜像。此路径的本机文件在`.local/web-image/`，不上传依赖或产物。已有镜像可用`COMPOSE_PROJECT_NAME=campus-member-01 docker compose up -d --no-build --wait`再次启动。网络正常时仍可使用原dev-up流程完整重建；BuildKit需`export DOCKER_BUILDKIT=1`。

使用Python3.9运行新增容器验收（系统python3仍为3.8，不修改系统解释器）：

```sh
export COMPOSE_PROJECT_NAME=campus-member-01
python3.9 scripts/smoke-member-01.py --expected-commit <health/info中的实际构建版本> --restart
```

`--restart`只重启当前Compose项目M1，验证持久来源；不在多人共用运行环境随意执行。本脚本保留测试来源的已释放历史，不清库。当前构建版本为`cf265a7ca2ea2e6b7807a1acdc762f2beaff0e34-dirty`；正式提交后重建应使用新版本，不能把dirty称为已发布commit。
