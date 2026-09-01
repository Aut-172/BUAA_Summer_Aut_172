# 生活服务微服务项目

当前版本已移除旧单体后端源码，运行态由 `services/` 下的微服务实现。旧单体版本已经通过 Git tag 保留，不再放在主干目录中。

## 目录结构

```text
life-service/
├─ services/
│  ├─ api-gateway/          # API 网关，统一前端入口
│  ├─ common-lib/           # 微服务公共依赖
│  ├─ merchant-service/     # 商家、商品、分类、搜索、推荐
│  ├─ user-service/         # 用户、管理员账号、地址、购物车、收藏
│  ├─ order-service/        # 订单主链路与订单内部接口
│  ├─ settlement-service/   # 支付、优惠券锁定与核销
│  ├─ fulfillment-service/  # 骑手、配送履约
│  └─ engagement-service/   # 评价、消息
├─ db/microservices/        # 多 schema 初始化 SQL
├─ frontend/                # Vite + React 前端
├─ k8s/                     # Kubernetes 示例部署清单
├─ scripts/                 # 初始化、启动、验证脚本
└─ docs/                    # 微服务边界与公共包说明
```

## 技术栈

- Java 21
- Spring Boot 3.4
- Spring Cloud Gateway
- Nacos Discovery
- OpenFeign
- Spring Cloud LoadBalancer
- MyBatis-Plus
- MySQL 8
- Redis 7
- React 18 + Vite

## 服务与端口

| 服务 | 容器内端口 | 默认宿主端口 | 说明 |
| --- | ---: | ---: | --- |
| api-gateway | 8080 | 8080 | 前端统一访问入口 |
| merchant-service | 8081 | 8081 | 商家商品域 |
| user-service | 8082 | 8082 | 用户域 |
| order-service | 8083 | 8083 | 订单域 |
| settlement-service | 8084 | 8084 | 结算域 |
| fulfillment-service | 8085 | 8085 | 履约域 |
| engagement-service | 8086 | 8086 | 互动域 |
| frontend | 80 | 5173 | 静态前端，反向代理到 Gateway |
| nacos | 8848/9848 | 8848/9848 | 注册中心 |
| mysql | 3306 | 3306 | 多库数据服务器 |
| redis | 6379 | 6379 | 验证码等公共缓存 |

## 本地启动

推荐使用 Docker Compose：

```powershell
copy .env.example .env
$env:COMPOSE_PARALLEL_LIMIT=1
docker compose up -d mysql redis nacos
docker compose run --rm db-init
docker compose up --build
```

首次构建会优先使用根目录下与 Gradle wrapper 版本匹配的 `gradle-*-bin.zip`，未找到时才回退到 wrapper 原始网络地址。`COMPOSE_PARALLEL_LIMIT=1` 用于避免 Windows Docker Desktop 在多个服务并发解压 Gradle 发行包时出现 I/O 错误。

`db-init` 会把完整初始化脚本转换为非破坏性执行：补齐缺失的 database/table/初始化数据，但不会 drop 已有表。已有旧 MySQL 卷缺少 `engagement_db` 时，单独执行 `docker compose run --rm db-init` 即可修复。

运行时变量见 `.env.example`，至少要关注 `DB_HOST`、`DB_PORT`、`NACOS_SERVER_ADDR`、`REDIS_HOST` 和 `REDIS_PORT`。本地直连默认是 `localhost`，容器里要改成对应服务名或宿主机地址。
Feign 服务间调用默认使用 `FEIGN_CONNECT_TIMEOUT=1000`、`FEIGN_READ_TIMEOUT=3000` 和 `FEIGN_LOGGER_LEVEL=basic`，详细治理约定见 [docs/feign-basic-governance.md](docs/feign-basic-governance.md)。

启动后访问：

```text
前端：http://localhost:5173
Gateway 健康检查：http://localhost:8080/actuator/health
Nacos 控制台：http://localhost:8848/nacos
```

各服务的 `/api/health` 还会返回 `application`、`version` 和数据库状态，便于现场核对当前部署版本。

停止服务：

```powershell
docker compose down
```

如需清空本地数据库卷：

```powershell
docker compose down -v
```

也可以执行 Windows 启动脚本：

```powershell
scripts\start.bat
```

## 数据库初始化

微服务版使用同一 MySQL 服务器上的多个 database/schema：

- `user_db`
- `merchant_db`
- `order_db`
- `settlement_db`
- `fulfillment_db`
- `engagement_db`

Docker Compose 首次创建 MySQL 卷时会自动执行：

```text
db/microservices/init-microservice-schemas.sql
```

如果需要手动重建本地数据：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1
```

注意：初始化 SQL 会重建业务表，重复执行会清空已有本地数据。

## 鉴权与服务间调用

- 前端请求进入 `api-gateway`，Gateway 根据路径转发到业务服务。
- 用户鉴权由业务服务通过 `common-lib` 中的 JWT 拦截器完成。
- Gateway 和前端 Nginx 会保留并转发 `Authorization` 请求头。
- 服务间调用只使用 OpenFeign + Nacos 服务名，例如 `lb://merchant-service` 或 `@FeignClient(name = ServiceNames.MERCHANT_SERVICE)`。
- 服务不能直接读取其他服务数据库表，跨服务数据访问必须走内部接口。

核心配置项见 [.env.example](.env.example)。

## 构建与测试

验证全部微服务（编译 `classes/testClasses`，再用仓库内临时 JUnit Runner 执行各服务测试）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-microservices.ps1
```

验证 B 侧边界与服务构建（同时检查 B 侧服务没有复制公共包、没有直接引用外部服务 Mapper/Service、没有固定 `localhost:808x` 的跨服务调用）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-b-side-services.ps1
```

该脚本默认生成 `reports/testing/b-side-test-report.md`。最近一次仓库内报告显示 `merchant-service`、`user-service`、`fulfillment-service`、`engagement-service` 合计 61 个服务级用例通过。若需要重新生成报告，可传入 `-ReportPath` 指定输出位置。

单个服务也可以独立测试：

```powershell
cd services\merchant-service
.\gradlew.bat test
```

前端：

```powershell
cd frontend
npm install
npm.cmd run build
npm.cmd run test:ci
npm.cmd run e2e
```

前端单测覆盖 `Home`、`Search`、`Login`、`MerchantDetail`、`Cart`、`Checkout`、`Orders`、`Profile`、`Messages`、`MerchantConsole`、`RiderConsole`、`AdminConsole` 等页面；Playwright E2E 覆盖 UC01-UC21 的多角色注册登录、消费者下单闭环、商家经营入口、骑手履约入口和管理员主体管理场景。`reports/testing/b-side-e2e-report.md` 是直连 Gateway 的接口冒烟报告，历史失败原因为本机 `localhost:8080` Gateway 未启动；`reports/testing/b-side-e2e-script-smoke.md` 是无 token 参数时的脚本跳过报告，不代表真实链路通过。

## CI/CD

GitHub Actions 定义在 [.github/workflows/ci.yml](.github/workflows/ci.yml)，触发条件为 `pull_request` 到 `main` 和 `push` 到 `main`。

- `microservice-test`：使用 Ubuntu runner、Temurin JDK 21 和 Gradle cache，按矩阵分别进入 `api-gateway`、六个业务服务目录执行 `./gradlew --no-daemon test`，并上传各服务 `build/reports/tests/test/` 和 `build/test-results/test/`。
- `frontend-test`：使用 Node 22 和 npm cache，在 `frontend/` 执行 `npm ci`、`npm run test:ci`，上传 `frontend/test-results/`。
- `frontend-e2e`：依赖前端单测，通过 `npx playwright install --with-deps chromium` 安装浏览器后执行 `npm run e2e:direct`，上传 Playwright report 和 test-results。
- `frontend-build`：依赖后端矩阵测试、前端单测和 E2E，执行 `npm run build`。
- `container-build-and-k8s-deploy`：仅在 `push` 到 `main` 时运行，构建并推送 Gateway、六个业务服务和前端镜像到 Docker Hub，加载镜像到 kind 集群，执行 `bash scripts/deploy-kind.sh`，随后通过 Gateway `/actuator/health` 和前端首页做健康检查。
- 诊断归档：部署 job 无论成功失败都会收集 `kubectl get all -o wide`、`kubectl describe pods`、各 Deployment 最近 200 行日志，以及端口转发日志并上传为 `k8s-diagnostics`。

本地脚本 `scripts/test-microservices.ps1`、`scripts/test-b-side-services.ps1` 和 `scripts/deploy-kind.sh` 与 CI 流程互为补充：前者便于 Windows 本地收尾验证，后者是 GitHub Actions 在 Linux runner 上的正式门禁和发布路径。

## 性能对比

性能对比脚本位于 [scripts/performance/compare-performance.mjs](scripts/performance/compare-performance.mjs)，会按同一套请求、同一并发和同一轮次分别压测单体版与微服务版，并自动生成详细报告：

```powershell
node scripts/performance/compare-performance.mjs
```

默认会输出以下文件到 `reports/performance/`：

- `performance-comparison.md`
- `performance-comparison.raw.json`
- `performance-comparison.raw.csv`

脚本默认对比的接口是 `GET /api/merchants`、`GET /api/products/30001` 和 `GET /api/orders/70001`。如果要换基址、并发数、轮次或容器名，可以通过命令行参数覆盖。

## Kubernetes

K8s 示例清单位于 `k8s/`：

- `configmap.yaml`
- `secret.example.yaml`
- `mysql.yaml`
- `redis.yaml`
- `nacos.yaml`
- `business-services.yaml`
- `api-gateway.yaml`
- `frontend.yaml`

Kind 部署脚本：

```bash
bash scripts/deploy-kind.sh
```

脚本会创建多库初始化 ConfigMap、Secret，依次部署 MySQL、Redis、Nacos、六个业务服务、Gateway 和前端。
