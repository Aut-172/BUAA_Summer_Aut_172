# B 侧服务拆分联调验收与收尾记录

本文按当前仓库状态整理 B 侧服务验收范围。B 侧服务指 `user-service`、`fulfillment-service`、`engagement-service`；它们已经接入 `common-lib`、Nacos 服务发现、OpenFeign 和独立 schema。A 侧主链路服务 `merchant-service`、`order-service`、`settlement-service` 已作为联调依赖存在。

## 1. 本轮 B 侧代码范围

技术路线限定为 `Gateway + Nacos + OpenFeign + LoadBalancer`：前端流量统一进入 Gateway，业务服务注册到 Nacos，服务间调用使用 `@FeignClient(name = "服务名")`，由 Spring Cloud LoadBalancer 完成实例选择。

| 服务 | 目录 | 拥有数据库 | 已搬迁能力 | 不再直接访问的外部表 |
| --- | --- | --- | --- | --- |
| 用户服务 | `services/user-service` | `user_db` | 用户/管理员认证、用户资料、地址、购物车、收藏、用户内部查询、购物车清理接口 | `merchant`、`product`、`product_spec`；改由 `merchant-service` Feign Client 访问 |
| 配送履约服务 | `services/fulfillment-service` | `fulfillment_db` | 骑手认证、骑手资料、骑手任务视图、接单、送达、配送追踪、骑手内部查询 | `orders`、`order_item`、`merchant`、`coupon`；改由 `order-service`、`merchant-service` Feign Client 访问 |
| 互动内容服务 | `services/engagement-service` | `engagement_db` | 评价、评价图片、消息、会话线程、未读数、会话订单代理查询 | `orders`、`order_item`、`user`、`merchant`、`product`、`rider`；改由 Feign Client 访问 Owner 服务 |

公共能力统一来自 A 侧提供的 `services/common-lib`，依赖坐标为 `com.example:microservice-common:0.1.0-SNAPSHOT`。B 侧服务不得复制 `com.example.demo.common` 源码。

## 2. B 侧需要 A 侧提供的内部接口

以下接口由 OpenFeign 按 Nacos 服务名调用，不在配置里写固定 `localhost` 地址。

| 调用方 | 依赖服务 | 内部接口 | 验收目的 |
| --- | --- | --- | --- |
| 用户服务 | 商家商品服务 | `POST /internal/products/quote` | 加购时校验商品有效、规格、价格、商家归属。 |
| 用户服务 | 商家商品服务 | `GET /internal/merchants/{merchantId}` | 收藏商家和购物车展示商家名。 |
| 配送履约服务 | 订单服务 | `GET /internal/fulfillment/tasks/available` | 骑手可接单列表。 |
| 配送履约服务 | 订单服务 | `GET /internal/fulfillment/tasks/assigned?riderId=` | 骑手进行中订单。 |
| 配送履约服务 | 订单服务 | `GET /internal/fulfillment/tasks/completed?riderId=` | 骑手已完成订单和收益统计。 |
| 配送履约服务 | 订单服务 | `POST /internal/fulfillment/orders/{orderId}/assign-rider` | 并发接单只允许一个骑手成功。 |
| 配送履约服务 | 订单服务 | `POST /internal/fulfillment/orders/{orderId}/delivered` | 送达后订单进入完成态。 |
| 配送履约服务 | 订单服务 | `GET /internal/orders/{orderId}` | 配送追踪读取订单状态和时间线。 |
| 配送履约服务 | 商家商品服务 | `GET /internal/merchants/{merchantId}` | 补齐取货地址、商家名和头像。 |
| 互动内容服务 | 订单服务 | `GET /internal/orders/{orderId}/participants?participantId=&participantType=` | 评价和消息发送前校验订单参与人。 |
| 互动内容服务 | 订单服务 | `POST /internal/orders/{orderId}/reviewed-items` | 评价成功后标记订单明细已评价。 |
| 互动内容服务 | 用户服务 | `GET /internal/users/{userId}` | 评价和消息展示用户昵称/头像，校验用户存在。 |
| 互动内容服务 | 商家商品服务 | `GET /internal/products/{productId}` | 评价展示商品名称/图片。 |
| 互动内容服务 | 商家商品服务 | `GET /internal/merchants/{merchantId}` | 消息展示商家名称/头像，校验商家存在。 |
| 互动内容服务 | 配送履约服务 | `GET /internal/riders/{riderId}` | 消息展示骑手名称，校验骑手存在。 |

## 3. 数据库验收清单

| 检查项 | 通过标准 |
| --- | --- |
| 用户服务 schema | `user_db` 只包含 `admin`、`user`、`address`、`cart`、`user_favorite_merchant`。 |
| 履约服务 schema | `fulfillment_db` 只包含 `rider`。 |
| 互动服务 schema | `engagement_db` 只包含 `review`、`message`。 |
| 种子数据 | `admin`、`user`、`rider`、`user_favorite_merchant` 与原 `init.sql` 中对应记录一致。 |
| 公共包依赖 | B 侧服务不得复制 `common/` 源码，应通过 `services/common-lib` 依赖统一使用 `Result`、异常、JWT、MyBatis、拦截器、健康检查等公共能力。 |
| 代码访问 | B 侧服务中不得出现外部服务 Mapper，例如 `OrdersMapper`、`MerchantMapper`、`ProductMapper`、跨域 `UserMapper` 只能出现在拥有服务内。 |
| 跨库查询 | 不允许任何 B 侧服务配置第二个业务数据源，不允许跨 schema SQL。 |

## 4. 端到端验收场景

| 场景 | 步骤 | 预期结果 |
| --- | --- | --- |
| 用户登录 | 经 Gateway 调用 `/api/auth/login`。 | Gateway 路由到用户服务，返回 consumer token，用户服务只读 `user_db.user`。 |
| 管理员登录 | 经 Gateway 调用 `/api/auth/admin/login`。 | Gateway 路由到用户服务，返回 admin token，用户服务只读 `user_db.admin`。 |
| 加入购物车 | 用户 token 调用 `/api/user/cart`，传商品 ID 和规格。 | 用户服务调用商家商品服务报价接口，写入 `user_db.cart` 商品快照。商品服务不可用时不写购物车。 |
| 收藏商家 | 用户 token 调用 `/api/user/favorites/{merchantId}`。 | 用户服务调用商家商品服务校验商家 active，写入 `user_db.user_favorite_merchant`。 |
| 骑手登录 | 经 Gateway 调用 `/api/auth/rider/login`。 | Gateway 路由到履约服务，返回 rider token，履约服务只读 `fulfillment_db.rider`。 |
| 骑手接单 | 骑手 token 调用 `/api/rider/tasks/{orderId}`，状态传 `待接单`。 | 履约服务先校验骑手 active，再调用订单服务接单接口；并发冲突时返回订单服务错误。 |
| 配送追踪 | 用户 token 调用 `/api/delivery/{orderId}`。 | 履约服务从订单服务取订单状态，从本地骑手表取骑手信息，从商家服务取商家信息。 |
| 提交评价 | 用户 token 调用互动服务 `/api/reviews`。 | 互动服务调用订单服务校验订单已完成且商品属于订单，只写 `engagement_db.review`，再请求订单服务标记明细已评价；`ReviewCreated` 当前为日志型扩展点。 |
| 重复评价 | 对同一订单再次调用 `/api/reviews`。 | 返回“该订单已评价，不可重复评价”，不新增评价。 |
| 发送消息 | 任一订单参与方调用 `/api/messages`。 | 互动服务调用各 Owner 服务校验接收方和订单参与关系，只写 `engagement_db.message`。 |
| 未读消息 | 接收方调用 `/api/messages/unread-count` 后进入会话。 | 未读数正确，拉取会话后本地消息被标记已读。 |

## 5. 异常场景

| 异常 | 预期处理 |
| --- | --- |
| 商家商品服务不可用时加购 | 返回 503 或统一错误，不写购物车。 |
| 商家被冻结时收藏 | 返回业务错误，不写收藏。 |
| 订单服务不可用时骑手接单 | 不修改 `rider` 表，返回可重试错误。 |
| 两个骑手同时抢单 | 订单服务只允许一个成功，失败方刷新任务列表。 |
| 订单未完成就评价 | 互动服务拒绝写入评价。 |
| 消息接收方不是订单参与人 | 互动服务拒绝写入消息。 |
| 评价写入成功但订单标记失败 | 当前保留日志型事件发布器作为扩展点；若要完全生产化，需要补本地补偿记录和重试调度。 |

## 6. 当前测试报告和 CI/CD 接入

| 项目 | 当前状态 |
| --- | --- |
| B 侧专项脚本 | `scripts/test-b-side-services.ps1` 会先做边界扫描，再运行 `merchant-service`、`user-service`、`fulfillment-service`、`engagement-service` 测试，并生成 `reports/testing/b-side-test-report.md`。 |
| 最近报告 | `reports/testing/b-side-test-report.md` 显示 63/63 通过；`reports/testing/b-side-test-report-smoke.md` 是早期 37/37 冒烟报告。 |
| Gateway 直连接口冒烟 | `reports/testing/b-side-e2e-report.md` 的失败原因是本机 `localhost:8080` Gateway 未启动；`reports/testing/b-side-e2e-script-smoke.md` 因未传 token 跳过 10 个场景，不能作为真实联调通过依据。 |
| CI/CD 接入 | `.github/workflows/ci.yml` 已存在：PR/push 到 `main` 时执行微服务矩阵测试、前端单测、前端 E2E 和前端构建；push 到 `main` 时进一步构建/推送镜像、部署 kind/K8s、做 Gateway 与前端健康检查并上传 `k8s-diagnostics`。B 侧专项脚本仍作为本地收尾验证和报告生成入口。 |

## 7. 收尾风险项

| 风险 | 说明 | 建议 |
| --- | --- | --- |
| `/api/user/reviews` Gateway 路由 | `engagement-service` 已实现该接口，Gateway 已补高优先级路由覆盖。 | 已完成。 |
| 商家后台真实接口 | 前端和 Gateway 记录了 `/api/merchant/dashboard`、`/api/merchant/profile`、`/api/merchant/products/**`，现已由 `merchant-service` 落地。 | 可直接进入真实联调与验收。 |
| 管理员商家管理 | `/api/admin/merchants/**` 已落地后端 Controller，可直接联调。 | 已完成。 |
