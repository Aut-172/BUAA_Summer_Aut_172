# Kubernetes HPA 自动扩缩容报告

整理日期：2026-09-03

## 1. 实验目的

验证 Kubernetes HorizontalPodAutoscaler 能否在业务访问压力升高时自动增加 Pod 副本数，并在压力下降后按缩容稳定窗口回落。同时记录吞吐量、响应时间、P95、错误率和资源变化。

## 2. 实验环境

| 项目 | 内容 |
| --- | --- |
| 被测系统 | Life Service 微服务系统 |
| 被测环境 | 阿里云 ECS 上的 Kubernetes/K3s 集群 |
| 网关入口 | `http://47.120.37.61:30081` |
| 压测工具 | Apache JMeter 5.6.3 |
| 观测脚本 | `scripts/collect-hpa-experiment.sh` |
| 压测脚本 | `load-tests/run-hpa-jmeter.ps1` |
| HPA 配置 | `k8s/hpa.yaml` |

## 3. HPA 配置摘要

| 服务 | minReplicas | maxReplicas | CPU 目标 |
| --- | ---: | ---: | ---: |
| `life-assistant-merchant-service` | 1 | 5 | 50% |
| `life-assistant-api-gateway` | 1 | 3 | 60% |
| `life-assistant-user-service` | 1 | 4 | 60% |
| `life-assistant-order-service` | 1 | 4 | 60% |
| `life-assistant-settlement-service` | 1 | 3 | 60% |
| `life-assistant-fulfillment-service` | 1 | 4 | 60% |
| `life-assistant-engagement-service` | 1 | 4 | 60% |

所有 HPA 均配置 `scaleDown.stabilizationWindowSeconds: 300`，压力停止后不会立即缩容。

## 4. 历史实验结果摘要

2026-09-01 的有效 HPA 实验记录显示：

| 指标 | 结果 |
| --- | ---: |
| 总请求数 | 3711 |
| 成功请求数 | 3450 |
| 失败请求数 | 261 |
| 错误率 | 7.03% |
| 吞吐量 | 6.07 req/s |
| 平均响应时间 | 7290 ms |
| P95 响应时间 | 23737 ms |
| P99 响应时间 | 34899 ms |

扩缩容观察：

- `merchant-service` 在压力升高后扩容至 5 个副本，压力下降后回落至 1。
- `api-gateway` 在压力升高后扩容至 3 个副本，压力下降后回落至 1。
- 两个服务均满足“升压扩容、降压缩容”的预期。

## 5. 问题分析

- HPA 行为符合预期，但 50 并发下系统出现明显延迟和 7.03% 错误率。
- `/api/search` 是主要瓶颈，原因是查询和组装商品时容易产生较多数据库访问和较大响应体。
- 单 Pod CPU 峰值接近 `500m` limit，说明资源限制、数据库连接池和查询逻辑都可能影响高负载表现。

## 6. 改进建议

1. 优化 `/api/search`，避免按商家循环查询商品，改为批量查询并分组。
2. 搜索结果分页，限制每个商家返回商品数量。
3. 检查 `merchant.status`、`merchant.category`、`product.merchant_id`、`product.status` 等索引。
4. 评估 Java 服务 CPU limit，从 `500m` 调整到 `800m` 或 `1000m` 后复测。
5. 补充 20、30、40、50 并发梯度压测，定位稳定边界。

## 7. 交付说明

考虑到本地没有额外备份，2026-09-01 的 HPA/JMeter 原始实验材料已复原并继续纳入仓库：

- `reports/perf/hpa-20260901-102919/`
- `reports/perf/hpa-20260901-112703/`

当前交付同时保留 HPA 配置、实验步骤说明、本摘要报告、HTML 报告和 Kubernetes 观测 CSV/TXT。`.gitignore` 仅忽略后续本地重跑产生的 `*.jtl`、日志、压缩包和临时目录，避免新的噪声文件混入提交。
