# 微服务公共包说明

## 1. 目录位置

公共包位于：

```text
services/common-lib
```

该包作为每个业务微服务的共同依赖使用。当前 `merchant-service` 已通过 Gradle composite build 引入：

```gradle
// services/merchant-service/settings.gradle
includeBuild '../common-lib'

// services/merchant-service/build.gradle
implementation 'com.example:microservice-common:0.1.0-SNAPSHOT'
```

后续 `user-service`、`order-service`、`settlement-service`、`fulfillment-service`、`engagement-service` 可以沿用同样方式。

## 2. 当前公共内容

| 包/类 | 用途 |
| --- | --- |
| `com.example.demo.common.Result` | 统一 API 响应体 |
| `com.example.demo.common.PageResult` | 统一分页响应体 |
| `com.example.demo.common.BusinessException` | 业务异常和业务状态码 |
| `com.example.demo.common.GlobalExceptionHandler` | 统一异常转换 |
| `com.example.demo.common.BaseEntity` | MyBatis-Plus 基础实体字段 |
| `com.example.demo.common.MyBatisPlusConfig` | MyBatis-Plus 分页插件 |
| `com.example.demo.common.MyMetaObjectHandler` | `createTime/updateTime` 自动填充 |
| `com.example.demo.common.controller.HealthController` | 每个服务统一 `/api/health` |
| `com.example.demo.config.JacksonConfig` | Long ID 序列化为字符串 |
| `com.example.demo.common.contract.ServiceNames` | Nacos/OpenFeign/Gateway 使用的服务名常量 |
| `com.example.demo.common.contract.InternalHeaders` | 内部调用请求头常量 |

## 3. 不放入公共包的内容

公共包不能拥有业务表，也不放具体业务实体、Mapper、Service、Controller 和业务 DTO。

不应放入：

| 类型 | 原因 |
| --- | --- |
| `Merchant`、`Product`、`Orders`、`User` 等业务实体 | 实体代表表归属，必须留在 Owner 服务 |
| Mapper/Repository | 其他服务不能通过公共包绕过 Owner 服务直接查表 |
| 下单、支付、评价等业务规则 | 业务规则属于对应服务，不应共享成隐式耦合 |
| 具体 Feign Client 接口 | Client 应放在调用方或独立 contract 包中，避免公共包反向依赖业务服务 |

## 4. 技术栈约束

微服务架构相关技术限定为：

```text
Gateway
Nacos
OpenFeign
LoadBalancer
```

推荐使用方式：

| 技术 | 职责 |
| --- | --- |
| Gateway | 前端统一入口、路由转发、跨域、鉴权拦截 |
| Nacos | 服务注册发现和按环境维护配置 |
| OpenFeign | 服务间 HTTP 调用 |
| LoadBalancer | OpenFeign 根据 Nacos 服务实例做客户端负载均衡 |

当前阶段只建立公共包和 `merchant-service` 小闭环，暂不强制引入 Nacos/Gateway/OpenFeign。等第二个业务服务开始调用 `merchant-service` 时，再添加 OpenFeign 和 LoadBalancer；等前端需要统一入口时，再添加 Gateway；本地和 CI 环境需要动态发现时，再接 Nacos。

## 5. 跨服务调用约定

1. 服务间通过 OpenFeign 调 Owner 服务的 `/internal/**` 接口。
2. Feign `name` 使用 `ServiceNames` 中的常量。
3. 每次写链路调用携带 `X-Request-Id`；幂等写操作携带 `X-Idempotency-Key`。
4. 调用失败时，调用方返回可重试错误，或写入本服务补偿记录后重试。
5. 不使用跨服务直接读库、跨 schema 联表或共享 Mapper。

## 6. Docker 构建约定

因为业务服务依赖 `services/common-lib`，Docker 构建上下文应使用仓库根目录，例如：

```powershell
docker build -f services/merchant-service/Dockerfile -t merchant-service:0.1.0 .
```

不要在 `services/merchant-service` 目录内直接执行 `docker build .`，否则 Docker 无法读取 `common-lib`。
