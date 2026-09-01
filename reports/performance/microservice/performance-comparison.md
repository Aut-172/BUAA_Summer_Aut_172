# 性能对比报告

## 测试条件

| 项目 | 值 |
| --- | --- |
| 机器 | DELONIX |
| 操作系统 | win32 v24.16.0 |
| 生成时间 | 2026/9/1 14:47:36 |
| 并发数 | 20 |
| 每轮请求数 | 25 |
| 预热请求数 | 5 |
| 重复轮次 | 3 |
| 数据集 | demo / 123456, merchant1 / 123456, merchantId=20001, productId=30001, orderId=70001 |
| 压测脚本 | ./scripts/performance/compare-performance.mjs |

## 接口选择

- `GET /api/merchants?page=1&size=20`：商家列表，代表高频浏览接口。
- `GET /api/products/30001`：商品详情，代表目录详情查询。
- `GET /api/orders/70001`：订单详情，代表登录后查询型接口。

## 版本信息

| 版本 | 基址 | 分支 | 提交 |
| --- | --- | --- | --- |
| 微服务版本 | http://localhost:8080/api | feature/a-side | b283e33 |

## 单轮结果

| 版本 | 场景 | 轮次 | 平均响应时间 | P95 | 吞吐量 | 错误率 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 微服务版本 | 商家列表 | 1 | 36.19 ms | 59.39 ms | 157.99 req/s | 0.00% |
| 微服务版本 | 商家列表 | 2 | 21.48 ms | 32.98 ms | 157.14 req/s | 0.00% |
| 微服务版本 | 商家列表 | 3 | 15.68 ms | 22.09 ms | 155.70 req/s | 0.00% |
| 微服务版本 | 商品详情 | 1 | 12.75 ms | 18.64 ms | 165.07 req/s | 0.00% |
| 微服务版本 | 商品详情 | 2 | 12.27 ms | 16.91 ms | 164.81 req/s | 0.00% |
| 微服务版本 | 商品详情 | 3 | 10.54 ms | 15.52 ms | 156.18 req/s | 0.00% |
| 微服务版本 | 订单详情 | 1 | 37.50 ms | 59.68 ms | 157.56 req/s | 0.00% |
| 微服务版本 | 订单详情 | 2 | 21.72 ms | 35.03 ms | 164.85 req/s | 0.00% |
| 微服务版本 | 订单详情 | 3 | 16.18 ms | 25.96 ms | 156.79 req/s | 0.00% |

## 分版本汇总

### 微服务版本

| 微服务版本 场景 | 平均响应时间 | P95 | 吞吐量 | 错误率 |
| --- | ---: | ---: | ---: | ---: |
| 商家列表 | 24.45 ms | 47.68 ms | 156.94 req/s | 0.00% |
| 商品详情 | 11.85 ms | 17.12 ms | 161.91 req/s | 0.00% |
| 订单详情 | 25.13 ms | 51.60 ms | 159.65 req/s | 0.00% |

| 微服务版本 容器 | 平均 CPU | 平均内存 |
| --- | ---: | ---: |
| life-assistant-api-gateway | 0.69% | 658.96 MiB |
| life-assistant-merchant-service | 18.23% | 827.43 MiB |
| life-assistant-user-service | 0.33% | 525.89 MiB |
| life-assistant-order-service | 2.04% | 595.36 MiB |
| life-assistant-settlement-service | 0.35% | 499.42 MiB |
| life-assistant-fulfillment-service | 0.33% | 508.51 MiB |
| life-assistant-engagement-service | 0.27% | 556.72 MiB |

## 原始数据

- [JSON 原始结果](performance-comparison.raw.json)
- [机器可读汇总](performance-comparison.raw.csv)

## 结论写法建议

- 只有当微服务版本在相同条件下的实测数据更优时，才写“性能提升”。
- 如果微服务版本更慢，也可以如实写出，并结合链路拆分、网关转发和跨服务调用解释原因。
- 原始数据和每轮结果都保留，便于复核。
