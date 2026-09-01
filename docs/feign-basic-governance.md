# Feign 基础治理说明

本文记录当前微服务版在引入 Sentinel 等治理中间件前，先落地的 OpenFeign 基础治理约定。

## 目标

- 统一服务间调用超时，避免单个依赖拖慢调用链。
- 将 Feign HTTP 失败、连接失败和超时转换为稳定的业务响应。
- 透传请求标识和调用方标识，为后续日志、指标、链路追踪和 Sentinel 规则治理预留入口。
- 保留关键业务场景的显式降级返回，不在业务代码里自研完整熔断器。

## 默认配置

所有使用 OpenFeign 的业务服务统一使用以下默认值：

| 配置项 | 默认值 | 说明 |
| --- | ---: | --- |
| `FEIGN_CONNECT_TIMEOUT` | `1000` | 建立连接超时时间，单位毫秒。 |
| `FEIGN_READ_TIMEOUT` | `3000` | 等待响应超时时间，单位毫秒。 |
| `FEIGN_LOGGER_LEVEL` | `basic` | 默认记录请求方法、URL、状态码和耗时。 |

如某个依赖接口确实属于慢接口，应在对应 Feign client 维度单独覆盖，而不是放宽全局默认值。

## 公共能力

公共配置位于 `services/common-lib/src/main/java/com/example/demo/common/feign/FeignGovernanceConfig.java`，由各业务服务通过 `@EnableFeignClients(defaultConfiguration = FeignGovernanceConfig.class)` 接入。

当前包含：

- `ErrorDecoder`：将依赖服务返回的 4xx、429、5xx 转换为 `RemoteServiceException`。
- `RequestInterceptor`：自动写入 `X-Caller-Service`，透传或生成 `X-Request-Id`，并透传 `X-Idempotency-Key`、`Authorization`。
- `Retryer.NEVER_RETRY`：禁止 Feign 默认隐式重试，避免在下游异常时放大流量。
- `Logger.Level`：通过 `FEIGN_LOGGER_LEVEL` 控制 Feign 日志级别。

全局异常处理器会识别：

- `RemoteServiceException`：依赖服务有 HTTP 响应但调用失败。
- `RetryableException`：连接失败、连接超时、读取超时等无稳定响应的情况。
- `FeignException`：未被公共解码器覆盖的 Feign 异常。

## 降级边界

当前只建议对“非核心强一致展示”做降级，例如商家看板依赖订单统计时返回带 `degraded=true` 的临时结果。订单创建、支付、库存扣减、优惠券锁定这类强一致写链路不应静默降级，应返回明确失败并依靠补偿记录或后续 MQ/Outbox 机制处理。

## Sentinel 接入预留

后续引入 Sentinel 时，建议沿现有边界继续扩展：

- Gateway 入口限流：保护 `/api/search`、`/api/recommend`、`/api/orders/**` 等高频入口。
- Feign 调用熔断：按下游服务维度配置慢调用比例、异常比例、半开探测。
- 热点参数限流：保护商品详情、商家详情、订单详情等按 ID 查询接口。
- 降级结果复用：继续使用当前业务层显式降级结构，避免中间件直接吞掉业务语义。

