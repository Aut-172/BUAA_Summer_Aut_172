# 生活服务项目

这是一个前后端分离的校园生活服务/外卖演示项目，当前版本已经可以跑通从找店、下单、支付到履约完成的完整主流程。

- `frontend/`：基于 Vite + React 的电脑端前端
- `backend/`：基于 Spring Boot + MyBatis-Plus 的后端
- `scripts/`：数据库初始化、接口联调测试、演示数据生成脚本

目前已经支持的核心功能：

- 用户登录、注册
- 浏览商家和商品
- 加入购物车
- 提交订单
- 订单支付
- 商家查看订单
- 骑手接单与配送
- 用户查看配送状态并确认完成
- 管理员查看平台数据

## 1. 技术栈

- Java 21
- Spring Boot 3
- MyBatis-Plus
- MySQL 8
- Redis 7
- Node.js 18+
- Vite
- React 18

## 2. 本地运行环境

请先确保本机已安装以下软件：

- Java 21
- Node.js 18 或更高版本
- npm
- MySQL 8.0 和 Redis 7，或 Docker / Docker Compose
- Windows PowerShell

## 3. 项目目录结构

```text
life-service/
├─ backend/
├─ frontend/
├─ scripts/
└─ README.md
```

## 4. 环境变量配置

后端配置文件位于 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)。

项目默认使用环境变量读取运行配置。没有设置环境变量时，会使用适合本地开发的默认值：

- 地址：`127.0.0.1`
- MySQL 端口：`3306`
- 数据库名：`life_assistant`
- 用户名：`root`
- 密码：`123456`
- Redis 地址：`localhost`
- Redis 端口：`6379`

如果你的本地 MySQL 账号密码不同，可以在启动后端前先设置环境变量：

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/life_assistant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true"
$env:SPRING_DATASOURCE_USERNAME="root"
$env:SPRING_DATASOURCE_PASSWORD="你的MySQL密码"
```

也可以复制 [.env.example](.env.example) 为 `.env`，按自己的机器修改其中的密码和端口。`.env` 属于本机私有配置，不应提交到仓库。

## 5. 初始化数据库

第一次启动前，请先执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1
```

如果使用项目提供的 Docker Compose 启动 MySQL，初始化脚本会自动使用 `life-assistant-mysql` 容器。若你的容器名不同，可以这样指定：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1 -DockerContainer 你的MySQL容器名
```

该脚本会自动完成：

- 创建数据库（如果不存在）
- 重建数据表
- 插入基础演示数据

注意：`init.sql` 会重建数据表，重新执行会清空已有演示数据。

## 6. Docker Compose 一键启动

推荐在新机器上优先使用 Docker Compose，前端、后端、MySQL、Redis 会分别运行在容器中：

```powershell
copy .env.example .env
docker compose up --build
```

启动完成后访问：

```text
前端：http://localhost:5173
后端健康检查：http://localhost:8081/api/health
Redis：localhost:6379
```

停止服务：

```powershell
docker compose down
```

如果需要连同数据库数据一起清理：

```powershell
docker compose down -v
```

## 7. Kubernetes 部署

项目提供了 Kubernetes 部署清单：

- [k8s/configmap.yaml](k8s/configmap.yaml)：非敏感运行配置
- [k8s/secret.example.yaml](k8s/secret.example.yaml)：Secret 示例，不要把真实密码写入仓库
- [k8s/mysql.yaml](k8s/mysql.yaml)：MySQL Deployment 和 Service
- [k8s/backend.yaml](k8s/backend.yaml)：后端 Deployment 和 Service
- [k8s/frontend.yaml](k8s/frontend.yaml)：前端 Deployment 和 Service

本地使用 Kind 或 Docker Desktop Kubernetes 时，可以先构建镜像：

```powershell
$env:APP_VERSION=(git rev-parse --short HEAD)
docker build -t life-assistant-backend:$env:APP_VERSION .\backend
docker build -t life-assistant-frontend:$env:APP_VERSION .\frontend
```

如果使用 Kind，需要把镜像加载到 Kind 集群：

```powershell
kind load docker-image life-assistant-backend:$env:APP_VERSION
kind load docker-image life-assistant-frontend:$env:APP_VERSION
```

然后部署：

```powershell
$env:IMAGE_TAG=$env:APP_VERSION
bash scripts/deploy-kind.sh
```

部署脚本会创建数据库初始化 ConfigMap、Secret，依次部署 MySQL、后端和前端，并等待每个 Deployment 滚动完成。

前端 NodePort 默认端口为：

```text
http://localhost:30080
```

## 8. CI/CD 流水线

GitHub Actions 配置位于 [.github/workflows/ci.yml](.github/workflows/ci.yml)。向 `main` push 或向 `main` 发起 PR 时会自动执行：

1. 后端单元/集成测试
2. 前端单元测试
3. 前端 E2E 测试
4. 前端构建
5. 前后端 Docker 镜像构建
6. Kind Kubernetes 部署
7. 前后端健康检查

镜像版本号使用 Git commit SHA，例如：

```text
aquared/life-service-assistant:backend-<commit-sha>
aquared/life-service-assistant:frontend-<commit-sha>
```

流水线使用 job 依赖关系控制顺序。任一测试或构建步骤失败，后续镜像构建、镜像推送、Kubernetes 部署和健康检查都会停止。测试报告和 Kubernetes 诊断信息会作为 GitHub Actions artifacts 保留。

镜像推送到 Docker Hub 前，需要在 GitHub 仓库 Settings 中配置：

- Actions secret：`DOCKERHUB_USERNAME`
- Actions secret：`DOCKERHUB_TOKEN`

不要把 Docker Hub token 写入 README、脚本、workflow 或提交记录。

## 9. 补充演示商家与商品数据

如果希望首页、商家列表、商品展示更饱满一些，可以执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-showcase-merchants.ps1
```

该脚本会通过项目接口自动注册一批演示商家，并为每个商家生成展示商品。

## 10. 启动后端

开发模式启动：

```powershell
cd backend
.\gradlew.bat bootRun
```

或者先打包再启动：

```powershell
cd backend
.\gradlew.bat bootJar
java -jar build\libs\demo-0.0.1-SNAPSHOT.jar --server.port=8081
```

后端健康检查地址：

```text
http://localhost:8081/api/health
```

## 11. 启动前端

```powershell
cd frontend
npm install
npm.cmd run dev
```

浏览器访问：

```text
http://localhost:5173
```

如需让 Vite 开发代理转发到其他后端地址，可以设置：

```powershell
$env:VITE_BACKEND_URL="http://localhost:8081"
npm.cmd run dev
```

## 12. 推荐启动顺序

建议按下面顺序启动：

1. 启动 MySQL
2. 执行 `scripts\init-db.ps1`
3. 如需更多展示数据，执行 `scripts\seed-showcase-merchants.ps1`
4. 启动后端：`backend\gradlew.bat bootRun`
5. 启动前端：`frontend\npm.cmd run dev`
6. 打开 `http://localhost:5173`

如果使用 Docker Compose，只需执行 `docker compose up --build`。

## 13. 演示账号

项目内置测试账号如下：

- 普通用户：`demo / 123456`
- 商家：`merchant1 / 123456`
- 骑手：`rider01 / 123456`
- 管理员：`gl1 / gl1gl1gl1`

前端也支持直接注册新的普通用户、商家和骑手账号，用于实际测试。

## 14. 如何验证业务流程

### 浏览器手动验证

1. 使用普通用户登录
2. 打开任意商家
3. 添加商品到购物车
4. 进入购物车并提交订单
5. 完成支付
6. 切换为骑手账号登录
7. 接单并更新配送状态
8. 切回普通用户账号
9. 打开配送页面并确认完成

### 后端接口联调测试

执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e-backend-test.ps1
```

这个脚本会自动验证以下关键流程：

- 登录
- 加入购物车
- 提交订单
- 支付
- 商家订单查询
- 骑手任务状态更新
- 配送单查询
- 订单完成
- 支付记录查询
- 管理员用户查询

## 15. 构建与测试

### 后端测试

```powershell
cd backend
.\gradlew.bat test
```

### 前端打包

```powershell
cd frontend
npm.cmd run build
```

### 前端单元测试

```powershell
cd frontend
npm.cmd test
```

### 前端 E2E 测试

```powershell
cd frontend
npm.cmd run e2e:direct
```

## 16. 常见问题

### 前端无法请求后端

请检查：

- 后端是否实际运行在 `8081` 端口
- 前端是否通过 `npm run dev` 启动
- [frontend/vite.config.js](frontend/vite.config.js) 中的 `VITE_BACKEND_URL` 是否指向正确后端地址

### 数据库连接失败

请检查：

- MySQL 是否已启动
- 用户名和密码是否正确
- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 是否设置正确

### 想重置演示数据

重新执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1
```

如需补充展示数据，再执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-showcase-merchants.ps1
```

### PowerShell 中骑手状态文字乱码

这通常是终端编码问题，不是后端逻辑错误。

项目内的接口联调脚本已经使用稳定状态值进行验证，不影响实际功能测试。

## 17. 提交到 GitHub 前建议检查

建议至少执行以下命令：

```powershell
cd backend
.\gradlew.bat test
```

```powershell
cd frontend
npm.cmd run build
```

```powershell
cd ..
powershell -ExecutionPolicy Bypass -File scripts\e2e-backend-test.ps1
```

如果还需要给组内同学演示前端页面效果，建议额外执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-showcase-merchants.ps1
```
