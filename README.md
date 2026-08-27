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
docker compose up --build
```

启动后访问：

```text
前端：http://localhost:5173
Gateway 健康检查：http://localhost:8080/actuator/health
Nacos 控制台：http://localhost:8848/nacos
```

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

验证全部微服务：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-microservices.ps1
```

验证 B 侧边界与服务构建：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-b-side-services.ps1
```

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
```

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
