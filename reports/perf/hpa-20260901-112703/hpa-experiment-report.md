# Kubernetes HPA 自动扩缩容压力测试实验报告

## 1. 实验目的

本实验面向生活服务微服务项目的云端 Kubernetes 部署，验证在业务访问压力升高时 Horizontal Pod Autoscaler（HPA）能够根据 CPU 利用率自动增加 Pod 副本数，并在压力下降后自动缩减副本数。同时记录服务吞吐量、平均响应时间、P95 响应时间和错误率，用于分析当前部署在高负载下的性能表现。

## 2. 实验环境

| 项目 | 内容 |
| --- | --- |
| 压测客户端 | Windows 本机 |
| 压测工具 | Apache JMeter 5.6.3 |
| 被测环境 | 阿里云 ECS 上的 Kubernetes/K3s 集群 |
| 被测入口 | `http://47.120.37.61:30081` |
| 主要被测链路 | `api-gateway -> merchant-service` |
| JMeter 结果目录 | `reports/perf/hpa-20260901-112703` |
| HPA 采集目录 | `reports/perf/hpa-20260901-112703/reports/perf/hpa-observe-20260901-112704` |

## 3. 实验准备

### 3.1 Metrics Server 验证

实验前已确认 `kubectl top nodes` 和 `kubectl top pods` 可以正常返回 CPU、内存指标，说明 Kubernetes Metrics API 可用，HPA 能够读取 Pod 资源指标。

### 3.2 资源请求与限制

为业务服务和网关 Deployment 增加了容器级资源请求与限制。Java 微服务采用如下配置：

```yaml
resources:
  requests:
    cpu: "100m"
    memory: "384Mi"
  limits:
    cpu: "500m"
    memory: "768Mi"
```

其中 `requests.cpu` 是 HPA 按 CPU 利用率计算扩缩容比例的基准。若未设置 CPU request，CPU 类型 HPA 无法稳定计算当前利用率。

### 3.3 HPA 配置

本次实验配置了两个 HPA：

| 服务 | minReplicas | maxReplicas | CPU 目标 |
| --- | ---: | ---: | ---: |
| `life-assistant-merchant-service` | 1 | 5 | 50% |
| `life-assistant-api-gateway` | 1 | 3 | 60% |

HPA 缩容稳定窗口配置为 300 秒，因此压力停止后不会立即缩容，而是等待一段稳定时间后再降低副本数。

### 3.4 演示数据

实验前通过 `scripts/seed-hpa-demo-data.ps1` 生成并导入了演示数据。压测相关数据规模为：

| 类型 | 数量 |
| --- | ---: |
| 演示商家 | 80 |
| 演示商品 | 960 |
| OSS 示例图片 | 6 张，供商家和商品复用 |

## 4. 压测方案

### 4.1 压测接口

JMeter 脚本对以下 3 个读接口循环发起请求：

| 接口 | 说明 |
| --- | --- |
| `GET /api/search?keyword=food` | 搜索接口，主要压测 merchant-service 的查询和组装逻辑 |
| `GET /api/merchants` | 商家列表接口 |
| `GET /api/recommend` | 推荐接口 |

### 4.2 压测参数

| 参数 | 值 |
| --- | ---: |
| 并发用户数 | 50 |
| 爬坡时间 | 120 秒 |
| 压测持续时间 | 600 秒 |
| 实际采样窗口 | 2026-09-01 11:27:05 至 11:37:16 |
| 实际持续时间 | 611.3 秒 |

“爬坡时间”表示 JMeter 在 120 秒内逐步启动 50 个并发用户，而不是瞬间启动全部用户。这样可以减少瞬时冲击，更容易观察 HPA 的扩容过程。

### 4.3 观测采集

云服务器上运行 `scripts/collect-hpa-experiment.sh` 采集 HPA、Deployment 和 Pod 资源指标：

| 参数 | 值 |
| --- | ---: |
| 采集开始时间 | 2026-09-01 11:27:04 |
| 采集结束时间 | 2026-09-01 11:46:59 |
| 采样间隔 | 约 15 秒 |
| 样本数 | 每个 HPA 63 条 |

## 5. 压测结果

### 5.1 总体性能

| 指标 | 值 |
| --- | ---: |
| 总请求数 | 3711 |
| 成功请求数 | 3450 |
| 失败请求数 | 261 |
| 错误率 | 7.03% |
| 吞吐量 | 6.07 req/s |
| 平均响应时间 | 7290 ms |
| P95 响应时间 | 23737 ms |
| P99 响应时间 | 34899 ms |

### 5.2 分接口性能

| 接口 | 样本数 | 错误数 | 错误率 | 吞吐量 | 平均响应时间 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `GET /api/search` | 1260 | 147 | 11.67% | 2.06 req/s | 14533 ms | 31448 ms | 39759 ms |
| `GET /api/merchants` | 1227 | 17 | 1.39% | 2.06 req/s | 1955 ms | 7923 ms | 11202 ms |
| `GET /api/recommend` | 1224 | 97 | 7.92% | 2.05 req/s | 5180 ms | 11687 ms | 20809 ms |

### 5.3 错误类型

| 类型 | 次数 | 说明 |
| --- | ---: | --- |
| HTTP 200 OK | 3450 | 请求成功 |
| `java.net.SocketTimeoutException: Read timed out` | 250 | 客户端等待服务响应超时 |
| `org.apache.http.NoHttpResponseException` | 11 | 服务端或中间链路关闭连接，客户端未收到完整响应 |

本轮压测不再出现 `UnknownHostException`，说明请求已正确发送到云端服务。错误主要来自高负载下的响应超时和少量连接无响应。

## 6. HPA 扩缩容结果

### 6.1 merchant-service 扩缩容过程

| 时间 | 当前副本数 | 期望副本数 | CPU 利用率 | 说明 |
| --- | ---: | ---: | ---: | --- |
| 11:27:04 | 3 | 3 | 10% | 采集开始时仍存在前序副本状态 |
| 11:27:23 | 2 | 2 | 8% | 压测刚开始，处于低负载采样 |
| 11:27:43 | 2 | 5 | 443% | CPU 大幅超过 50% 目标，HPA 将期望副本数提升到 5 |
| 11:28:06 | 5 | 5 | 460% | merchant-service 完成扩容并达到 maxReplicas |
| 11:35:12 - 11:41:07 | 5 | 5 | 高位后逐步下降 | 压测期间和结束后稳定保持 5 个副本 |
| 11:42:21 | 5 | 2 | 5% | 压力下降后，经过缩容稳定窗口开始缩容 |
| 11:43:35 | 2 | 1 | 4% | HPA 继续降低期望副本数到 1 |
| 11:43:54 | 1 | 1 | 3% | 缩容完成，回到最小副本数 |

merchant-service 的 Deployment 观测结果显示，最大期望副本数为 5，最大就绪副本数为 5，最终回落到 1。

### 6.2 api-gateway 扩缩容过程

| 时间 | 当前副本数 | 期望副本数 | CPU 利用率 | 说明 |
| --- | ---: | ---: | ---: | --- |
| 11:27:04 | 1 | 1 | 6% | 采集开始 |
| 11:27:23 | 1 | 2 | 73% | CPU 超过 60% 目标，开始扩容 |
| 11:27:43 | 2 | 2 | 116% | 扩容到 2 个副本 |
| 11:28:51 | 3 | 3 | 126% | 达到 maxReplicas 3 |
| 11:43:35 | 1 | 1 | 4% | 压力下降后缩容回 1 |

api-gateway 的 Deployment 观测结果显示，最大期望副本数为 3，最大就绪副本数为 3，最终回落到 1。

### 6.3 Pod 资源峰值

| 对象 | 采样数 | 单 Pod 最大 CPU | 平均 CPU |
| --- | ---: | ---: | ---: |
| api-gateway | 130 | 498m | 48.94m |
| merchant-service | 254 | 501m | 107.61m |
| mysql | 63 | 320m | 85.46m |

merchant-service 和 api-gateway 单 Pod CPU 峰值接近 `500m` 的 limit，说明压测期间 Pod 已经接近或达到 CPU 上限。此现象与较高响应时间、超时错误率相互印证。

## 7. 结果分析

### 7.1 扩缩容目标达成情况

本次实验成功观察到以下现象：

| 目标 | 结果 |
| --- | --- |
| 压力升高后 Pod 数量增加 | 达成。merchant-service 扩容至 5，api-gateway 扩容至 3 |
| 压力下降后 Pod 数量减少 | 达成。两个 HPA 最终都缩容回 1 |
| 记录吞吐量 | 达成。总体吞吐量 6.07 req/s |
| 记录平均响应时间 | 达成。总体平均响应时间 7290 ms |
| 记录 P95 响应时间 | 达成。总体 P95 为 23737 ms |
| 记录错误率 | 达成。总体错误率 7.03% |

因此，本轮实验可以作为 Kubernetes HPA 自动扩缩容验证的有效实验记录。

### 7.2 性能瓶颈判断

虽然 HPA 扩缩容行为符合预期，但系统在 50 并发下出现明显性能退化：总体 P95 达到 23.7 秒，错误率达到 7.03%。其中 `/api/search` 的错误率和延迟最高，是主要瓶颈接口。

结合当前代码逻辑，`/api/search` 会先查询所有 active 商家，再对每个商家查询商品并在应用层组装结果。在演示数据达到 80 个商家、960 个商品后，该接口容易产生较多数据库查询和较大的响应体，导致 merchant-service 与 MySQL 压力上升。

### 7.3 HPA 行为说明

merchant-service 在 11:27:43 的 CPU 利用率达到 443%，远高于 50% 目标，因此 HPA 快速将期望副本数提升到最大值 5。随后 Pod 资源采样显示 merchant-service 单 Pod 最大 CPU 达到 501m，接近设置的 CPU limit，说明单 Pod 已经被充分压榨。

压力停止后，HPA 未立即缩容，而是在约 5 分钟后逐步从 5 降到 2，再降到 1。这符合配置中的 `scaleDown.stabilizationWindowSeconds: 300`，避免短时波动导致频繁缩容。

## 8. 问题与改进建议

1. 降低 `/api/search` 查询成本：避免按商家循环查询商品，可改为批量查询商品后按 `merchant_id` 分组，减少 N+1 查询。
2. 控制响应体大小：搜索结果可分页，或限制每个商家返回的商品数量。
3. 调整数据库索引：检查 `merchant.status`、`merchant.category`、`product.merchant_id`、`product.status` 等索引是否被有效使用。
4. 调整连接池参数：根据 ECS 资源和 MySQL 承载能力评估 HikariCP、Tomcat/Undertow 线程池和 Gateway 连接参数。
5. 重新评估资源限制：当前 Java 服务 CPU limit 为 500m，压测期间已接近上限。若目标是降低错误率，可尝试提高到 800m 或 1000m 后重复实验。
6. 分阶段压测：建议补充 20、30、40、50 并发梯度实验，用于定位系统从稳定到退化的临界点。

## 9. 实验结论

本次实验验证了 Kubernetes HPA 在当前微服务项目中的自动扩缩容能力。压测开始后，`life-assistant-merchant-service` 根据 CPU 指标从低副本扩容至 5 个副本，`life-assistant-api-gateway` 扩容至 3 个副本；压测结束后，两个服务均在缩容稳定窗口后回落至 1 个副本。实验现象符合“压力升高后 Pod 数量增加，压力下降后 Pod 数量减少”的预期。

同时，JMeter 结果显示系统在 50 并发下出现明显延迟和 7.03% 错误率，说明 HPA 能够缓解单 Pod 压力，但不能完全消除应用查询逻辑、数据库承载能力、连接池配置或资源上限带来的瓶颈。后续应结合接口优化和资源参数调优继续开展梯度压测。

## 10. 附件

| 文件 | 说明 |
| --- | --- |
| `samples.jtl` | JMeter 原始采样结果 |
| `html/index.html` | JMeter HTML 报告入口 |
| `html/statistics.json` | JMeter 汇总统计数据 |
| `reports/perf/hpa-observe-20260901-112704/hpa.csv` | HPA 时间序列采样 |
| `reports/perf/hpa-observe-20260901-112704/deployments.csv` | Deployment 副本数时间序列 |
| `reports/perf/hpa-observe-20260901-112704/pods-top.csv` | Pod CPU/内存采样 |
| `reports/perf/hpa-observe-20260901-112704/hpa-describe-end.txt` | 实验结束时 HPA 状态详情 |
