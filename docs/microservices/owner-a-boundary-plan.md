# 负责人 A 微服务边界与主链路收尾说明

## 1. 工作范围

负责人 A 负责微服务拆分中的核心边界和主链路落地，重点不是按用例数量机械拆分，而是保证服务职责、数据库所有权和跨服务调用规则一致。

负责人 A 的主要职责：

| 工作项 | 本阶段交付 |
| --- | --- |
| 架构与边界 | 维护服务划分、数据归属和跨服务访问规则，避免服务互相直接访问数据表 |
| 数据库拆分 | 已按服务 Owner 拆出 `user_db`、`merchant_db`、`order_db`、`settlement_db`、`fulfillment_db`、`engagement_db` 初始化脚本 |
| 服务拆分 | 当前已有六个业务服务：`merchant-service`、`user-service`、`order-service`、`settlement-service`、`fulfillment-service`、`engagement-service` |
| 接口联调 | 已形成公共契约 DTO、OpenFeign Client、内部接口、幂等键和本地补偿记录的主链路约束 |
| 部署 | 已提供 docker-compose、Kubernetes 清单、`scripts/deploy-kind.sh` 和 `.github/workflows/ci.yml` GitHub Actions 流水线 |
| 测试 | 已有服务级 JUnit 测试、B 侧边界扫描脚本、前端 Vitest 和 Playwright E2E |

## 2. 改造前后代码版本

| 版本 | 代码形态 | 说明 |
| --- | --- | --- |
| V1 单体基线 | 旧 `backend/` 单体已从当前主干移除，按根 README 说明由 Git tag 保留 | 所有业务包共享一个进程、一个数据源、一个 `life_assistant` 数据库 |
| V2 当前微服务版 | 当前目录为 `services/*-service` 加 `api-gateway`、`common-lib` | 每个业务服务独立构建、测试、部署，只连接自己的 database/schema；跨服务通过 OpenFeign + Nacos 服务名调用 |

## 3. 目标业务服务划分

本方案采用 6 个业务微服务。Gateway、Nacos、前端和数据库均不计入业务微服务数量。

| 服务 | 负责业务 | 当前可迁移包 | 拆分理由 |
| --- | --- | --- | --- |
| `user-service` | 消费者、管理员账号，用户资料，地址，购物车，收藏 | `auth` 的用户/管理员部分、`user` | 用户资料和购物车是用户侧高频状态，购物车保存商品快照，但不拥有商品事实 |
| `merchant-service` | 商家账号，门店档案，分类，商品，规格，库存，搜索，推荐 | `auth` 的商家部分、`merchant`、`search`、`recommend` | 商家商品是下单前事实源，库存和商品状态必须由该服务统一管理 |
| `order-service` | 下单，订单状态机，订单明细快照，团购券码，商家订单视图 | `order` | 订单是交易聚合根，负责状态推进和订单快照，不直接拥有商品、优惠券、支付、骑手资料 |
| `settlement-service` | 优惠券模板，用户券，锁券/释放/核销，支付流水 | `coupon`、`payment` | 支付和优惠券需要独立幂等、一致性和补偿逻辑 |
| `fulfillment-service` | 骑手账号，骑手资料，接单，配送任务视图，送达 | `auth` 的骑手部分、`rider`、`delivery` | 骑手和履约状态独立于订单存储，但通过订单服务推进订单状态 |
| `engagement-service` | 评价，评价图片，订单会话，消息线程，未读数 | `review`、`message` | 互动内容不应阻塞交易主链路，评分和已评价状态通过同步接口与本地补偿记录处理 |

## 4. 服务划分图

```mermaid
flowchart LR
    FE[React 前端] --> GW[Gateway]
    GW -.服务发现.-> NACOS[Nacos]

    GW --> USER[user-service]
    GW --> MERCHANT[merchant-service]
    GW --> ORDER[order-service]
    GW --> SETTLEMENT[settlement-service]
    GW --> FULFILL[fulfillment-service]
    GW --> ENGAGE[engagement-service]

    USER --> USERDB[(user_db)]
    MERCHANT --> MERCHANTDB[(merchant_db)]
    ORDER --> ORDERDB[(order_db)]
    SETTLEMENT --> SETTLEDB[(settlement_db)]
    FULFILL --> FULFILLDB[(fulfillment_db)]
    ENGAGE --> ENGAGEDB[(engagement_db)]

    ORDER -->|OpenFeign/LoadBalancer 商品快照/库存预占/释放/状态查询| MERCHANT
    ORDER -->|OpenFeign/LoadBalancer 锁券/释放/确认优惠券| SETTLEMENT
    SETTLEMENT -->|OpenFeign/LoadBalancer 支付成功推进| ORDER
    FULFILL -->|OpenFeign/LoadBalancer 接单/送达状态变更| ORDER
    ENGAGE -->|OpenFeign/LoadBalancer 校验订单参与人/标记已评价| ORDER
    ENGAGE -->|OpenFeign/LoadBalancer 用户展示信息| USER
    ENGAGE -->|OpenFeign/LoadBalancer 商家/商品展示信息| MERCHANT
    USER -->|OpenFeign/LoadBalancer 购物车商品校验| MERCHANT
```

## 5. 数据表归属表

| 当前表 | Owner 服务 | 目标 schema | 其他服务访问方式 |
| --- | --- | --- | --- |
| `admin` | `user-service` | `user_db` | 其他服务不直接访问；Gateway 校验 token 后透传身份 |
| `user` | `user-service` | `user_db` | 内部用户快照接口 |
| `address` | `user-service` | `user_db` | 订单服务调用地址快照接口，下单后保存地址快照 |
| `cart` | `user-service` | `user_db` | 下单成功后通过内部接口清理购物车 |
| `user_favorite_merchant` | `user-service` | `user_db` | 收藏列表展示时依赖商家状态查询，不做跨服务状态同步 |
| `merchant` | `merchant-service` | `merchant_db` | 内部商家快照接口 |
| `category` | `merchant-service` | `merchant_db` | 公开分类接口 |
| `product` | `merchant-service` | `merchant_db` | 商品报价、详情、库存接口 |
| `spec_group` | `merchant-service` | `merchant_db` | 商品详情或报价接口 |
| `product_spec` | `merchant-service` | `merchant_db` | 商品报价和库存预占接口 |
| `merchant_stock_change` | `merchant-service` | `merchant_db` | 库存预占/释放请求状态和幂等记录 |
| `orders` | `order-service` | `order_db` | 订单内部状态/权限/金额校验接口 |
| `order_compensation` | `order-service` | `order_db` | 订单跨服务失败补偿记录 |
| `order_item` | `order-service` | `order_db` | 订单详情接口或评价资格校验接口 |
| `group_coupon` | `order-service` | `order_db` | 订单详情接口 |
| `coupon` | `settlement-service` | `settlement_db` | 优惠券公开接口和内部锁券接口 |
| `user_coupon` | `settlement-service` | `settlement_db` | 锁券、释放、核销接口 |
| `payment` | `settlement-service` | `settlement_db` | 支付查询接口和支付确认回调 |
| `rider` | `fulfillment-service` | `fulfillment_db` | 骑手快照和状态校验接口 |
| `review` | `engagement-service` | `engagement_db` | 评价公开查询接口；评分投影通过同步接口更新 |
| `message` | `engagement-service` | `engagement_db` | 消息公开接口；发送前调各 Owner 校验参与人 |

## 6. 负责人 A 主链路内部接口

### 6.1 merchant-service

| 接口 | 方法 | 调用方 | 用途 | 失败处理 |
| --- | --- | --- | --- | --- |
| `/internal/merchants/{merchantId}` | `GET` | `order-service`、`fulfillment-service`、`engagement-service` | 获取商家状态、名称、地址、配送费、起送价等快照 | 不可用时调用方终止当前写操作；查询场景可降级展示缺失字段 |
| `/internal/products/quote` | `POST` | `user-service`、`order-service` | 按商品 ID、规格、数量返回价格、库存、商品状态、所属商家 | 商品无效或库存不足返回 `available=false` 和明细原因；服务不可用返回可重试错误 |
| `/internal/products/reserve` | `POST` | `order-service` | 使用 `requestId` 幂等键预占库存 | 预占失败则订单不创建 |
| `/internal/products/release` | `POST` | `order-service` | 订单创建失败或取消时释放库存 | 接口保持幂等；失败进入订单服务本地补偿记录 |
| `/internal/products/changes/{requestId}` | `GET` | `order-service` | 查询库存变更请求状态 | 供补偿和人工排障核对 |

DTO 草案：

```json
{
  "requestId": "NO202608270001",
  "merchantId": 20001,
  "items": [
    {
      "productId": 30001,
      "specLabel": "Large",
      "quantity": 1
    }
  ]
}
```

### 6.2 order-service

| 接口 | 方法 | 调用方 | 用途 | 失败处理 |
| --- | --- | --- | --- | --- |
| `/internal/orders/{orderId}` | `GET` | `settlement-service`、`fulfillment-service`、`engagement-service` | 查询订单状态、金额、参与人、商品明细快照 | 不可用时调用方返回可重试错误 |
| `/internal/orders/{orderId}/mark-paid` | `POST` | `settlement-service` | 支付成功后幂等推进为待接单 | 按 `transactionId` 幂等处理；失败由支付服务写入本地补偿记录后重试 |
| `/internal/fulfillment/tasks/available` | `GET` | `fulfillment-service` | 查询可接单任务 | 不可用时骑手端返回可重试错误 |
| `/internal/fulfillment/tasks/assigned?riderId=` | `GET` | `fulfillment-service` | 查询骑手进行中任务 | 不可用时骑手端返回可重试错误 |
| `/internal/fulfillment/tasks/completed?riderId=` | `GET` | `fulfillment-service` | 查询骑手已完成任务 | 不可用时骑手端返回可重试错误 |
| `/internal/fulfillment/orders/{orderId}/assign-rider` | `POST` | `fulfillment-service` | 骑手接单，订单服务保证并发安全 | 冲突返回业务错误，骑手端刷新任务 |
| `/internal/fulfillment/orders/{orderId}/delivered` | `POST` | `fulfillment-service` | 骑手送达后推进订单状态 | 状态不匹配返回业务错误，可重试 |
| `/internal/orders/{orderId}/reviewed-items` | `POST` | `engagement-service` | 评价成功后标记明细已评价 | 失败由互动服务写入本地补偿记录后重试 |

### 6.3 settlement-service

| 接口 | 类型 | 调用方/订阅方 | 用途 | 失败处理 |
| --- | --- | --- | --- | --- |
| `/internal/coupon-locks` | `POST` | `order-service` | 下单时锁定用户券并返回优惠金额 | 锁券失败则订单服务释放库存并终止下单 |
| `/internal/coupon-locks/{orderId}/release` | `POST` | `order-service` | 取消订单或下单失败时释放用户券 | 幂等；失败进入补偿重试 |
| `/internal/coupon-locks/{orderId}/confirm` | `POST` | `order-service` | 订单完成后确认核销优惠券 | 幂等；失败进入补偿重试 |
| `/internal/orders/{orderId}/mark-paid` | OpenFeign 调用 | `order-service` | 支付成功后推进订单为待接单 | 支付服务本地保留成功流水和补偿状态，订单服务按 `transactionId` 幂等 |

## 7. 主链路跨服务流程

### 7.1 下单结算

1. `order-service` 接收下单请求。
2. 调 `user-service` 校验用户状态和地址，复制地址快照。
3. 调 `merchant-service` 获取商家和商品报价，校验 active、起送价、规格、价格和库存。
4. 调 `merchant-service` 创建库存预占，幂等键使用 `requestId`。
5. 调 `settlement-service` 锁定优惠券。
6. `order-service` 本地事务写入 `orders`、`order_item`、`group_coupon`。
7. 调 `user-service` 清理对应商家购物车；失败时订单仍成立，订单服务记录补偿任务。

失败处理：

| 失败点 | 处理 |
| --- | --- |
| 用户/地址校验失败 | 不创建订单，直接返回错误 |
| 商品报价或库存不足 | 不创建订单，返回商品状态或库存错误 |
| 库存预占成功但锁券失败 | 调库存释放接口，释放失败进入补偿重试 |
| 锁券成功但订单写入失败 | 调库存释放和优惠券释放，失败进入补偿重试 |
| 购物车清理失败 | 不影响订单，订单服务记录补偿任务并重试清理接口 |

### 7.2 支付

1. `settlement-service` 校验订单状态和金额。
2. 写入 `payment`，用 `orderId + payMethod + transactionId` 保证幂等。
3. 调用 `order-service /mark-paid`。
4. `order-service` 幂等推进 `pending_payment -> pending_accept`。

失败处理：

| 失败点 | 处理 |
| --- | --- |
| 订单金额或状态不匹配 | 拒绝支付，不写成功流水 |
| 支付成功但订单接口失败 | 支付流水保持成功，返回需补偿重试的业务错误；后续可由调度或人工按支付流水重试订单推进 |
| 重复支付回调 | 根据幂等键返回已有支付结果 |

### 7.3 取消与完成

取消：

1. `order-service` 幂等更新订单为 `cancelled`。
2. 调 `merchant-service` 释放库存预占。
3. 调 `settlement-service` 释放优惠券。
4. 调用失败时订单服务记录补偿任务并重试。

完成：

1. `fulfillment-service` 或用户确认送达。
2. `order-service` 推进订单为 `completed`。
3. 调 `settlement-service` 确认优惠券核销。
4. 如需要销量投影，再由同步接口更新商家侧统计。
5. 调用失败时订单服务记录补偿任务并重试。

## 8. 数据库拆分落地

多 schema 初始化脚本位于：

```text
db/microservices/init-microservice-schemas.sql
```

落地约束：

1. 每个服务使用独立数据库账号，只授予本服务 schema 的读写权限。
2. 跨 schema 不建外键，不跨库联表查询。
3. 订单、购物车、支付、评价等表只保存其他服务的 ID 和必要快照。
4. 高频展示字段通过 OpenFeign 同步接口获取，或由本服务维护必要快照；失败补偿使用本服务数据库中的补偿记录。

## 8.1 公共包与技术栈约束

公共包位于：

```text
services/common-lib
```

当前公共包提供统一响应、分页响应、业务异常、全局异常处理、基础实体、MyBatis-Plus 配置、自动填充、健康检查、Long ID 序列化、服务名和内部请求头常量。详细说明见：

```text
docs/microservices/common-lib-guide.md
```

微服务架构技术栈限定为：

```text
Gateway
Nacos
OpenFeign
LoadBalancer
```

当前阶段不引入额外消息队列、注册中心或配置中心技术。跨服务调用统一按 OpenFeign + LoadBalancer 规划，服务注册发现按 Nacos 规划，统一前端入口按 Gateway 规划。

当前 `order-service` 已从骨架推进到订单主链路服务：

1. 使用 `services/common-lib` 作为公共依赖。
2. 独立拥有 `orders`、`order_item`、`group_coupon`、`order_compensation` 等订单域表。
3. 提供 `GET /internal/orders/{orderId}` 内部订单快照接口。
4. 提供 `POST /internal/orders/{orderId}/mark-paid` 支付成功推进接口。
5. 提供 `/internal/fulfillment/**` 骑手任务、接单和送达内部接口。
6. 对前端开放 `GET /api/orders`、`GET /api/orders/{id}`、`POST /api/checkout`、`POST /api/orders/{id}/cancel`、`POST /api/orders/{id}/complete`、`GET/PUT /api/merchant/orders/**`、`GET /api/admin/orders/**`。
7. 通过 `MerchantProductClient`、`MerchantCatalogClient`、`SettlementCouponClient`、`UserClient` 调用 Owner 服务，不直接访问外部服务表。

当前 `settlement-service` 已从骨架推进到结算营销服务：

1. 使用 `services/common-lib` 作为公共依赖。
2. 独立拥有 `coupon`、`user_coupon`、`payment` 三张表。
3. 提供 `POST /internal/coupon-locks` 优惠券锁定接口。
4. 提供 `POST /internal/coupon-locks/{orderId}/release` 优惠券释放接口。
5. 提供 `POST /internal/coupon-locks/{orderId}/confirm` 优惠券核销接口。
6. 提供 `POST /internal/payments/mock-success` 模拟支付成功接口，并通过 OpenFeign 调用 `order-service /internal/orders/{orderId}/mark-paid`。
7. 对前端开放 `GET /api/coupons`、`GET /api/coupons/available`、`POST /api/coupons/{id}/claim`、`POST /api/orders/{id}/pay`、`GET /api/orders/{id}/payments`、`GET /api/payments/{id}`。

建议账号：

| 服务 | schema | 数据库账号 |
| --- | --- | --- |
| `user-service` | `user_db` | `user_svc` |
| `merchant-service` | `merchant_db` | `merchant_svc` |
| `order-service` | `order_db` | `order_svc` |
| `settlement-service` | `settlement_db` | `settlement_svc` |
| `fulfillment-service` | `fulfillment_db` | `fulfillment_svc` |
| `engagement-service` | `engagement_db` | `engagement_svc` |

## 9. 当前实际落地状态

当前分支已经不是三服务骨架，而是六个业务服务、Gateway、公共包、Docker Compose、K8s 清单和测试脚本都已进入可收尾验收的状态。

| 服务 | 当前已落地能力 | 仍需关注 |
| --- | --- | --- |
| `merchant-service` | 商家注册登录、公开商家/商品/分类、搜索、推荐、内部商家/商品快照、商品报价、库存预占/释放/状态查询。 | 已补齐商家后台资料、商品 CRUD、管理员商家管理接口。 |
| `user-service` | 消费者注册登录、管理员登录、用户资料、地址、购物车、收藏、用户内部快照、购物车按商家清理、管理员用户冻结/解冻。 | 购物车与收藏依赖 `merchant-service` 可用；服务不可用时按可重试错误处理。 |
| `order-service` | 消费者订单、下单、取消、完成、商家订单、管理员订单、内部订单快照、支付推进、履约任务代理、参与人校验、评价标记、补偿记录。 | 库存/券释放补偿记录已有落点，后续可补自动重试调度。 |
| `settlement-service` | 用户券、可领取券、领券、内部锁券/释放/确认、支付流水、模拟支付成功并调用订单 `mark-paid`。 | 当前为模拟支付链路，未接真实第三方支付回调。 |
| `fulfillment-service` | 骑手注册登录、资料、看板、任务列表、接单、送达、配送追踪、骑手内部快照、管理员骑手审核/冻结/解冻。 | 任务事实源仍在订单服务，履约服务不直接拥有订单表。 |
| `engagement-service` | 评价、图片上传、商品/商家/用户评价、商家评分、订单消息、线程、未读数、会话订单代理。 | `GET /api/user/reviews` 已由 Gateway 优先路由到 engagement-service；日志型事件发布器只是后续扩展点。 |

## 10. 测试和验收入口

| 验收项 | 命令或文件 | 说明 |
| --- | --- | --- |
| 全量后端服务 | `powershell -ExecutionPolicy Bypass -File scripts/test-microservices.ps1` | 编译并执行 Gateway、六个业务服务测试。 |
| B 侧边界专项 | `powershell -ExecutionPolicy Bypass -File scripts/test-b-side-services.ps1` | 扫描公共包复制、跨服务 Mapper/Service 引用、固定 URL 调用，并生成 `reports/testing/b-side-test-report.md`。 |
| 前端构建和单测 | `cd frontend && npm ci && npm.cmd run build && npm.cmd run test:ci` | 生成 Vitest JUnit XML。 |
| 前端 E2E | `cd frontend && npm.cmd run e2e` | Playwright 场景默认走前端 mock API，覆盖 UC01-UC21。 |
| Gateway 直连接口冒烟 | `scripts/e2e-b-side-api.ps1` | 需要先启动 Gateway 和业务服务，并传入有效 token；历史 skipped/connection refused 报告不能视为链路通过。 |

## 11. CI/CD 收口说明

仓库当前已有 `.github/workflows/ci.yml`，触发 `pull_request` 到 `main` 和 `push` 到 `main`。流水线主要阶段如下：

1. `microservice-test`：按矩阵测试 `api-gateway` 和六个业务服务，使用 Temurin JDK 21，执行 `./gradlew --no-daemon test`，并上传 Gradle HTML/JUnit 报告。
2. `frontend-test`：使用 Node 22，执行 `npm ci` 和 `npm run test:ci`，上传 `frontend/test-results/`。
3. `frontend-e2e`：安装 Chromium 后执行 `npm run e2e:direct`，上传 Playwright report 和 test-results。
4. `frontend-build`：在后端矩阵测试、前端单测、前端 E2E 全部通过后执行 `npm run build`。
5. `container-build-and-k8s-deploy`：仅 `push` 到 `main` 时执行，构建并推送 Gateway、六个业务服务和前端镜像，创建 kind 集群，执行 `scripts/deploy-kind.sh`，并做 Gateway `/actuator/health` 与前端首页健康检查。
6. 诊断归档：部署 job 始终收集 K8s resources、Pod describe、各 Deployment 日志和端口转发日志，上传为 `k8s-diagnostics`。

本地收尾验证仍保留 `scripts/test-microservices.ps1`、`scripts/test-b-side-services.ps1` 和前端 `npm.cmd run test:ci/e2e`，方便在提交前复现 CI 的核心质量门禁。

## 12. 当前接口差异记录

| 差异 | 影响 | 建议 |
| --- | --- | --- |
| 前端仍调用 `/api/merchant/dashboard`、`/api/merchant/profile`、`/api/merchant/products/**`，后端已暴露对应 Controller。 | 商家后台可直接走真实 Gateway 联调。 | 已完成。 |
| 前端和 Gateway 记录 `/api/admin/merchants/**`，后端已暴露管理员商家管理接口。 | 管理员商家审核、冻结、解冻可真实联调。 | 已完成。 |
| `engagement-service` 暴露 `GET /api/user/reviews`，Gateway 已补更高优先级路由。 | 该接口经 Gateway 访问时不会再被用户服务泛路由截获。 | 如需进一步统一命名，可后续把接口改为 `/api/reviews/mine` 并同步前端。 |
