# Sentinel 服务治理说明

本文记录 Life Assistant 微服务版引入 Sentinel 的第一阶段约定：入口限流、业务服务限流、Feign 熔断开关、Nacos 规则持久化和统一响应格式。

## 当前边界

本阶段不在云端部署 Sentinel Dashboard。Dashboard 只作为本机或临时排障观测工具，正式规则通过 Nacos Config 发布和持久化。

当前云端约定：

- Sentinel 规则使用 Nacos `DEFAULT_GROUP`。
- 不使用 Nacos namespace，保持 public namespace。
- Nacos 未开启鉴权，发布脚本暂不配置用户名、密码或 token。
- 前端 NodePort `30080`、Gateway NodePort `30081` 继续保持不变。

已接入范围：

- `api-gateway`：接入 Sentinel Gateway 适配包，支持网关 API 分组和网关限流规则。
- 六个业务服务：接入 Sentinel starter、Nacos 规则数据源和 Feign Sentinel 开关。
- `common-lib`：提供业务服务统一 Sentinel block JSON 响应和 URL 归一化。
- Nacos Config：新增 Gateway `gw-flow` / `gw-api-group` 规则，以及业务服务 `flow` / `degrade` 规则样例。

未做事项：

- 不自研熔断器。
- 不把下单、支付、库存扣减、优惠券锁定等强一致写链路改成静默降级成功。
- 不直接公网暴露 Sentinel Dashboard 或 Nacos 控制台。

## Sentinel Dashboard 取舍

部署 Dashboard 的好处：

- 可以直观看到资源调用、限流和熔断效果，现场排障更快。
- 可以临时调整规则，适合压测、演示和规则阈值摸底。
- 多服务接入后，能集中观察哪些接口最容易触发保护。

暂不部署 Dashboard 的好处：

- 少维护一个组件，降低当前 k3s 单机环境的内存和端口占用。
- 少一个管理控制台暴露面，避免未加固前误开公网。
- 规则以 Nacos Config 为准，更容易纳入仓库样例、脚本发布和变更记录。

当前阶段建议继续不在云端部署 Dashboard。后续如果要做压测演示或治理观察，可以临时部署到集群内网，只通过 `kubectl port-forward` 或 SSH 隧道访问；规则仍以 Nacos Config 持久化为准。

## 运行配置

公共环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SENTINEL_ENABLED` | `true` | 是否启用 Sentinel。 |
| `SENTINEL_EAGER` | `true` | 是否启动时初始化 Sentinel，便于尽早暴露配置问题。 |
| `SENTINEL_DASHBOARD` | 空 | Dashboard 地址。云端默认不配置。 |
| `SENTINEL_RULE_GROUP` | `DEFAULT_GROUP` | Sentinel 规则所在 Nacos group。 |
| `FEIGN_SENTINEL_ENABLED` | `true` | 是否开启 Feign + Sentinel 集成。 |

业务服务默认读取两个规则 Data ID：

- `sentinel-${spring.application.name}-flow.json`
- `sentinel-${spring.application.name}-degrade.json`

Gateway 默认读取两个规则 Data ID：

- `sentinel-api-gateway-gw-flow.json`
- `sentinel-api-gateway-gw-api-group.json`

## 规则样例

规则文件位于 `configs/nacos/`。首批规则以保守阈值起步：

| 服务 | 规则文件 | 重点保护资源 |
| --- | --- | --- |
| Gateway | `sentinel-api-gateway-gw-api-group.json`、`sentinel-api-gateway-gw-flow.json` | 搜索、推荐、订单、支付、商家看板入口。 |
| merchant-service | `sentinel-merchant-service-flow.json`、`sentinel-merchant-service-degrade.json` | 搜索、推荐、商家看板、商品报价、库存预留。 |
| order-service | `sentinel-order-service-flow.json`、`sentinel-order-service-degrade.json` | 订单列表、订单详情、下单、商家看板内部统计。 |
| settlement-service | `sentinel-settlement-service-flow.json`、`sentinel-settlement-service-degrade.json` | 支付、优惠券、优惠券锁定。 |
| user-service | `sentinel-user-service-flow.json`、`sentinel-user-service-degrade.json` | 登录、注册、验证码、购物车。 |
| fulfillment-service | `sentinel-fulfillment-service-flow.json`、`sentinel-fulfillment-service-degrade.json` | 骑手任务、配送详情、骑手登录。 |
| engagement-service | `sentinel-engagement-service-flow.json`、`sentinel-engagement-service-degrade.json` | 评价、图片上传、消息。 |

业务服务 URL 会对常见 ID 路径做归一化，例如 `/api/orders/70001` 会归为 `/api/orders/{id}`。新增路径变量接口时，应同步检查 `SentinelWebBlockConfig` 是否需要补归一化规则。

## 发布规则

发布脚本会自动发布 `configs/nacos/` 下的 `.yml`、`.yaml`、`.json` 文件。

Windows 本机：

```powershell
docker compose up -d nacos

powershell -ExecutionPolicy Bypass -File scripts\publish-nacos-config.ps1 `
  -NacosUrl "http://127.0.0.1:8848" `
  -Group "DEFAULT_GROUP"
```

k3s 云端推荐使用端口转发，不开放公网端口：

```bash
kubectl port-forward -n life-assistant svc/nacos 8848:8848
```

另一个终端发布：

```bash
pwsh scripts/publish-nacos-config.ps1 \
  -NacosUrl "http://127.0.0.1:8848" \
  -Group "DEFAULT_GROUP"
```

发布后建议重启受影响服务：

```bash
kubectl rollout restart -n life-assistant deployment/life-assistant-api-gateway
kubectl rollout restart -n life-assistant deployment/life-assistant-merchant-service
kubectl rollout restart -n life-assistant deployment/life-assistant-order-service
```

## 响应格式

Gateway 限流返回：

```json
{"code":429,"message":"系统繁忙，请稍后重试","data":null}
```

业务服务限流返回：

```json
{"code":429,"message":"请求过于频繁，请稍后重试","data":null}
```

业务服务熔断返回：

```json
{"code":503,"message":"依赖服务暂不可用，请稍后重试","data":null}
```

商家看板是明确允许降级的读接口。它依赖订单服务失败或熔断时仍返回 `code=200`，但业务数据中会携带 `degraded=true`、`degradedDependency=order-service` 和 `fallbackReason`。

## 调整建议

- 日常调低或调高 QPS：改对应 `sentinel-*-flow.json`，发布到 Nacos 后重启或等待规则刷新。
- 调整熔断敏感度：改对应 `sentinel-*-degrade.json` 的 `count`、`minRequestAmount`、`statIntervalMs` 和 `timeWindow`。
- 新增接口保护：优先在 Gateway 增加入口规则；如果接口会直接打到服务，再加业务服务 URL 规则。
- 强一致写接口：只做限流和快速失败，不做静默降级成功。
- 可降级读接口：在业务层显式返回带 `degraded=true` 的业务结构，不让 Sentinel 直接决定业务数据。

## 官方参考

- [Spring Cloud Alibaba Sentinel](https://github.com/alibaba/spring-cloud-alibaba/wiki/Sentinel)
- [Sentinel Gateway Flow Control](https://sentinelguard.io/en-us/docs/api-gateway-flow-control.html)
- [Sentinel Dynamic Rule Configuration](https://sentinelguard.io/en-us/docs/dynamic-rule-configuration.html)
