# VSCode 配置环境指南

## 1. 适用对象

本指南面向第一次接手项目的同学，目标是能在 VSCode 中完成前端开发、后端联调和提交前验证。

当前项目根目录是 `life-service/`，前端代码位于 `frontend/`，后端微服务位于 `services/`。

## 2. 基础环境要求

建议准备以下环境：

- Node.js 18 及以上，CI 使用 Node 22。
- JDK 21。
- Docker Desktop，推荐用于本地启动 MySQL、Redis、Nacos 和各微服务容器。
- VSCode 或 IntelliJ IDEA。VSCode 建议安装 ESLint、Prettier、Java Extension Pack。

检查命令：

```bash
node -v
npm -v
java -version
docker version
```

## 3. 打开项目

在 VSCode 中打开仓库根目录：

```text
life-service/
```

不要只打开 `frontend/` 或 `services/`，否则根目录脚本、Docker Compose、K8s 清单和文档相互引用会不完整。

## 4. 前端依赖安装

在 VSCode 终端执行：

```powershell
cd frontend
npm install
```

安装成功后会生成或更新 `frontend/node_modules/`。

## 5. 前端开发模式

只开发页面或跑前端 mock E2E 时，可以直接启动前端：

```powershell
cd frontend
npm run dev
```

默认访问：

```text
http://127.0.0.1:5173/
```

前端默认 API 基址是 `/api`。如果没有启动 Gateway，请求真实接口会失败；Playwright E2E 中的 mock 只在测试运行时接管接口，不是日常 dev 服务的一部分。

## 6. 完整本地联调

需要验证真实后端时，在仓库根目录启动 Docker Compose：

```powershell
copy .env.example .env
$env:COMPOSE_PARALLEL_LIMIT=1
docker compose up -d mysql redis nacos
docker compose run --rm db-init
docker compose up --build
```

常用访问地址：

```text
前端：http://localhost:5173
Gateway：http://localhost:8080
Gateway 健康检查：http://localhost:8080/actuator/health
Nacos：http://localhost:8848/nacos
```

若只想让本地 Vite 前端直连某个远端 Gateway，可在启动前设置：

```powershell
$env:VITE_API_BASE_URL="http://<server-ip>:30081/api"
npm run dev
```

## 7. 构建与测试

前端提交前建议执行：

```powershell
cd frontend
npm.cmd run build
npm.cmd run test:ci
```

前端 E2E：

```powershell
cd frontend
npm.cmd run e2e
```

说明：`npm.cmd run e2e` 默认使用 Playwright 和 `frontend/e2e/support/mockApi.js` 覆盖前端场景，适合验证页面流程。真实 Gateway 冒烟需要先启动完整后端，并使用接口冒烟脚本或专门的真实环境测试命令。

后端全量测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-microservices.ps1
```

B 侧专项测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-b-side-services.ps1
```

## 8. 测试账号

初始化数据中常用账号：

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 普通用户 | `demo` | `123456` |
| 商家 | `merchant01` | `123456` |
| 骑手 | `rider01` | `123456` |
| 管理员 | `admin` | `admin123` |

骑手和商家账号可能受审核、冻结状态影响；若登录成功但访问业务页被拒绝，请先用管理员端或数据库检查状态。

## 9. 关键文件

前端优先阅读：

- `frontend/src/main.jsx`：React 应用入口、路由、导航和角色保护。
- `frontend/src/utils/api.js`：Axios API 客户端和接口方法。
- `frontend/src/utils/ApiProvider.jsx`：登录态、token、角色和全局提示。
- `frontend/src/pages/`：页面组件和单测。
- `frontend/e2e/support/mockApi.js`：Playwright E2E 的 mock 接口。
- `frontend/nginx.conf`：前端容器内 `/api` 和 `/uploads` 反向代理。

后端优先阅读：

- `services/api-gateway/`：Gateway 路由。
- `services/common-lib/`：公共 JWT、Result、异常和 Feign 常量。
- `services/*-service/src/main/java/**/controller/`：对外接口和内部接口。
- `db/microservices/init-microservice-schemas.sql`：初始化数据和多 schema 表结构。

## 10. 常见问题

### 10.1 前端页面能打开但接口失败

确认 Gateway 是否运行：

```text
http://localhost:8080/actuator/health
```

如果前端部署在 K8s NodePort `30080`，页面会自动把 API 基址切到同主机 `30081/api`。域名、HTTPS 或自定义端口场景请显式配置 `VITE_API_BASE_URL`。

### 10.2 Docker Compose 构建很慢或失败

首次构建会解压 Gradle 发行包。Windows Docker Desktop 下建议保留：

```powershell
$env:COMPOSE_PARALLEL_LIMIT=1
```

### 10.3 Playwright 本地启动浏览器失败

历史报告中出现过 `browserType.launch: spawn EPERM`，通常与本机权限、杀毒软件、浏览器安装或沙箱限制有关。先尝试：

```powershell
cd frontend
npx playwright install chromium
npm.cmd run e2e:direct
```

若仍失败，以 CI 的 `frontend-e2e` 结果或可用机器上的 Playwright 结果作为准入依据。

### 10.4 文档和代码不一致

当前实现口径优先级：

1. 代码和自动化测试。
2. 根目录 `README.md`。
3. `frontend/接口说明文档.md` 和 `frontend/项目交付说明.md`。
4. `frontend/参考文档/参考文档/` 下的课程参考材料。

## 11. 协作建议

- 新增或修改接口时，同步更新 `frontend/src/utils/api.js`、对应后端 Controller 测试和 `frontend/接口说明文档.md`。
- 修改角色链路时，同步检查 `frontend/src/main.jsx`、目标页面、`ApiProvider.jsx` 和角色流程文档。
- 修改部署、配置或治理能力时，同步更新 `README.md`、`docs/` 下对应文档和 `reports/` 下最新报告入口。
