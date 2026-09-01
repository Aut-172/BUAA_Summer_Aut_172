# 单体版与微服务版性能对比报告

## 测试条件

| 项目 | 单体版本 | 微服务版本 |
| --- | --- | --- |
| 机器 | DELONIX | DELONIX |
| 操作系统 | win32 v24.16.0 | win32 v24.16.0 |
| 测试时间 | 2026/9/1 11:52:33 | 2026/9/1 14:47:36 |
| 压测脚本 | `./scripts/performance/compare-performance.mjs` | `./scripts/performance/compare-performance.mjs` |
| 基址 | `http://localhost:8081/api` | `http://localhost:8080/api` |
| 并发数 | 20 | 20 |
| 每轮请求数 | 25 x 20 = 500 | 25 x 20 = 500 |
| 预热请求数 | 5 | 5 |
| 重复轮次 | 3 | 3 |
| 数据集 | `demo / 123456`, `merchant1 / 123456`, `merchantId=20001`, `productId=30001`, `orderId=70001` | `demo / 123456`, `merchant1 / 123456`, `merchantId=20001`, `productId=30001`, `orderId=70001` |

## 接口选择

| 接口 | 业务代表性 |
| --- | --- |
| `GET /api/merchants?page=1&size=20` | 商家列表，代表高频浏览查询。 |
| `GET /api/products/30001` | 商品详情，代表目录详情查询。 |
| `GET /api/orders/70001` | 订单详情，代表登录后订单查询。 |

## 汇总对比

| 场景 | 单体平均响应 | 微服务平均响应 | 平均响应变化 | 单体 P95 | 微服务 P95 | P95 变化 | 单体吞吐量 | 微服务吞吐量 | 吞吐量变化 | 单体错误率 | 微服务错误率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 商家列表 | 32.74 ms | 24.45 ms | -25.32% | 51.70 ms | 47.68 ms | -7.78% | 199.59 req/s | 156.94 req/s | -21.37% | 0.00% | 0.00% |
| 商品详情 | 18.48 ms | 11.85 ms | -35.88% | 26.69 ms | 17.12 ms | -35.86% | 179.87 req/s | 161.91 req/s | -9.98% | 0.00% | 0.00% |
| 订单详情 | 17.00 ms | 25.13 ms | +47.82% | 22.99 ms | 51.60 ms | +124.45% | 182.73 req/s | 159.65 req/s | -12.63% | 0.00% | 0.00% |

说明：响应时间和 P95 的负数表示微服务版本更低，吞吐量的正数表示微服务版本更高。

## 资源占用

| 版本 | 容器 | 平均 CPU | 平均内存 |
| --- | --- | ---: | ---: |
| 单体版本 | `buaa_summer_aut_172-monolith-fianl-version-backend-1` | 299.26% | 753.87 MiB |
| 微服务版本 | `life-assistant-api-gateway` | 0.69% | 658.96 MiB |
| 微服务版本 | `life-assistant-merchant-service` | 18.23% | 827.43 MiB |
| 微服务版本 | `life-assistant-user-service` | 0.33% | 525.89 MiB |
| 微服务版本 | `life-assistant-order-service` | 2.04% | 595.36 MiB |
| 微服务版本 | `life-assistant-settlement-service` | 0.35% | 499.42 MiB |
| 微服务版本 | `life-assistant-fulfillment-service` | 0.33% | 508.51 MiB |
| 微服务版本 | `life-assistant-engagement-service` | 0.27% | 556.72 MiB |
| 微服务版本合计 | 网关 + 6 个业务服务 | 22.24% | 4172.29 MiB |

## 结果分析

在本次实测中，商家列表和商品详情两个目录类查询接口的微服务版本平均响应时间更低，P95 也更低，这两个接口在本次测试条件下响应延迟有所下降，性能略有提升。

订单详情接口的微服务版本平均响应时间和 P95 都高于单体版本，性能略有下降。主要原因是微服务版本的订单详情请求需要经过 API 网关和订单服务链路，并且服务拆分后存在额外的网络转发、认证解析和服务治理开销；单体版本在同一进程内完成处理，链路更短。

吞吐量方面，三个接口的微服务版本均低于单体版本。这个结果说明当前微服务拆分的主要收益不在单机单接口吞吐，而在服务独立构建、部署、扩缩容和故障隔离。后续如果要提升吞吐量，可以针对网关连接池、JVM 参数、Docker 资源限制和热点服务实例数继续调优。

资源方面，单体版只有一个后端容器，平均内存约 753.87 MiB；微服务版本由网关和 6 个业务服务组成，合计平均内存约 4172.29 MiB。微服务版本内存明显更高，符合多 JVM、多进程部署的预期。

## 原始结果

- 单体版本报告：`reports/performance/monolith/performance-comparison.md`
- 单体版本 JSON：`reports/performance/monolith/performance-comparison.raw.json`
- 单体版本 CSV：`reports/performance/monolith/performance-comparison.raw.csv`
- 微服务版本报告：`reports/performance/microservice/performance-comparison.md`
- 微服务版本 JSON：`reports/performance/microservice/performance-comparison.raw.json`
- 微服务版本 CSV：`reports/performance/microservice/performance-comparison.raw.csv`
