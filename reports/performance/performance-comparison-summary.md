# 单体版与微服务版性能对比报告

## 测试条件

| 项目 | 单体版本 | 微服务版本 |
| --- | --- | --- |
| 机器 | DELONIX | DELONIX |
| 操作系统 | win32 v24.16.0 | win32 v24.16.0 |
| 测试时间 | 2026/9/1 11:52:33 | 2026/9/3 09:52:02 |
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
| `GET /api/products/30001` | 商品详情，代表商家目录详情查询。 |
| `GET /api/orders/70001` | 订单详情，代表登录后订单查询。 |

## 汇总对比

| 场景 | 单体平均响应 | 微服务平均响应 | 平均响应变化 | 单体 P95 | 微服务 P95 | P95 变化 | 单体吞吐量 | 微服务吞吐量 | 吞吐量变化 | 单体错误率 | 微服务错误率 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 商家列表 | 32.74 ms | 24.83 ms | -24.16% | 51.70 ms | 43.74 ms | -15.40% | 199.59 req/s | 166.80 req/s | -16.43% | 0.00% | 0.00% |
| 商品详情 | 18.48 ms | 11.11 ms | -39.88% | 26.69 ms | 16.59 ms | -37.83% | 179.87 req/s | 152.81 req/s | -15.05% | 0.00% | 0.00% |
| 订单详情 | 17.00 ms | 21.73 ms | +27.82% | 22.99 ms | 35.51 ms | +54.46% | 182.73 req/s | 152.90 req/s | -16.32% | 0.00% | 0.00% |

说明：响应时间和 P95 的负数表示微服务版本更低，吞吐量的正数表示微服务版本更高。

## 资源占用

| 版本 | 容器 | 平均 CPU | 平均内存 |
| --- | --- | ---: | ---: |
| 单体版本 | `buaa_summer_aut_172-monolith-fianl-version-backend-1` | 299.26% | 753.87 MiB |
| 微服务版本 | `life-assistant-api-gateway` | 0.69% | 658.96 MiB |
| 微服务版本 | `life-assistant-merchant-service` | 152.55% | 894.68 MiB |
| 微服务版本 | `life-assistant-user-service` | 0.40% | 563.83 MiB |
| 微服务版本 | `life-assistant-order-service` | 80.09% | 627.27 MiB |
| 微服务版本 | `life-assistant-settlement-service` | 0.48% | 536.76 MiB |
| 微服务版本 | `life-assistant-fulfillment-service` | 0.50% | 518.90 MiB |
| 微服务版本 | `life-assistant-engagement-service` | 0.44% | 564.08 MiB |
| 微服务版本合计 | 网关 + 6 个业务服务 | 311.89% | 4389.12 MiB |

## 结果分析

在本次最新实测中，商家列表和商品详情两个目录类查询接口的微服务版本平均响应时间和 P95 都低于单体版本，说明拆分后这两类读接口在当前条件下响应延迟有所下降。

订单详情接口的微服务版本平均响应时间和 P95 都高于单体版本，说明订单链路在拆分后引入了额外的网关转发、服务间调用和治理开销。

吞吐量方面，三个接口的微服务版本仍然低于单体版本，说明当前微服务版本的主要收益不在单机吞吐，而在服务独立构建、部署、扩缩容和故障隔离。

资源方面，单体版只有一个后端容器，平均内存约 753.87 MiB；微服务版本由网关和 6 个业务服务组成，合计平均内存约 4389.12 MiB，明显更高。

## 原始结果

- 单体版本报告：`reports/performance/monolith/performance-comparison.md`
- 单体版本 JSON：`reports/performance/monolith/performance-comparison.raw.json`
- 单体版本 CSV：`reports/performance/monolith/performance-comparison.raw.csv`
- 微服务版本报告：`reports/performance/microservice-rerun-20260903/performance-comparison.md`
- 微服务版本 JSON：`reports/performance/microservice-rerun-20260903/performance-comparison.raw.json`
- 微服务版本 CSV：`reports/performance/microservice-rerun-20260903/performance-comparison.raw.csv`
