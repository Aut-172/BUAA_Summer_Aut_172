# 性能对比报告

## 测试条件

| 项目 | 值 |
| --- | --- |
| 机器 | DELONIX |
| 操作系统 | win32 v24.16.0 |
| 生成时间 | 2026/9/3 09:52:02 |
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
| 微服务版本 | http://localhost:8080/api | main | ac49c6c |

## 单轮结果

| 版本 | 场景 | 轮次 | 平均响应时间 | P95 | 吞吐量 | 错误率 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 微服务版本 | 商家列表 | 1 | 33.73 ms | 56.88 ms | 202.46 req/s | 0.00% |
| 微服务版本 | 商家列表 | 2 | 23.06 ms | 33.02 ms | 153.55 req/s | 0.00% |
| 微服务版本 | 商家列表 | 3 | 17.71 ms | 26.91 ms | 153.05 req/s | 0.00% |
| 微服务版本 | 商品详情 | 1 | 12.49 ms | 19.02 ms | 153.07 req/s | 0.00% |
| 微服务版本 | 商品详情 | 2 | 10.79 ms | 13.88 ms | 152.89 req/s | 0.00% |
| 微服务版本 | 商品详情 | 3 | 10.04 ms | 14.29 ms | 152.48 req/s | 0.00% |
| 微服务版本 | 订单详情 | 1 | 27.75 ms | 40.60 ms | 152.77 req/s | 0.00% |
| 微服务版本 | 订单详情 | 2 | 21.14 ms | 31.46 ms | 151.89 req/s | 0.00% |
| 微服务版本 | 订单详情 | 3 | 16.31 ms | 23.93 ms | 154.04 req/s | 0.00% |

## 分版本汇总

### 微服务版本

| 微服务版本 场景 | 平均响应时间 | P95 | 吞吐量 | 错误率 |
| --- | ---: | ---: | ---: | ---: |
| 商家列表 | 24.83 ms | 43.74 ms | 166.80 req/s | 0.00% |
| 商品详情 | 11.11 ms | 16.59 ms | 152.81 req/s | 0.00% |
| 订单详情 | 21.73 ms | 35.51 ms | 152.90 req/s | 0.00% |

| 微服务版本 容器 | 平均 CPU | 平均内存 |
| --- | ---: | ---: |
| life-assistant-api-gateway | 77.43% | 683.60 MiB |
| life-assistant-merchant-service | 152.55% | 894.68 MiB |
| life-assistant-user-service | 0.40% | 563.83 MiB |
| life-assistant-order-service | 80.09% | 627.27 MiB |
| life-assistant-settlement-service | 0.48% | 536.76 MiB |
| life-assistant-fulfillment-service | 0.50% | 518.90 MiB |
| life-assistant-engagement-service | 0.44% | 564.08 MiB |

## 原始数据

- [JSON 原始结果](performance-comparison.raw.json)
- [机器可读汇总](performance-comparison.raw.csv)

## 结论写法建议

- 只有当微服务版本在相同条件下的实测数据更优时，才写“性能提升”。
- 如果微服务版本更慢，也可以如实写出，并结合链路拆分、网关转发和跨服务调用解释原因。
- 原始数据和每轮结果都保留，便于复核。
