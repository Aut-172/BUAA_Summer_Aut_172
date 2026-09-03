# 微服务故障容错实验报告

## 1. 实验概述

本实验用于验证 Life Service 微服务系统在关键依赖服务故障时的容错能力。实验选择商家看板链路作为被保护链路：商家端请求 `GET /api/merchant/dashboard` 时，`merchant-service` 需要通过 Feign 调用 `order-service` 的内部订单汇总接口。实验通过将 `order-service` 缩容到 0 来模拟依赖服务不可用，观察商家看板是否能够返回降级数据，并确认旁路链路和 Kubernetes 资源状态是否保持可控。

实验于 2026-09-03 在云服务器 K3s 集群执行。故障观测从 10:28:25 +0800 开始，到 10:36:48 +0800 结束，覆盖了故障注入前、故障持续期和服务恢复期。

## 2. 实验环境

| 项目 | 内容 |
| --- | --- |
| 系统 | Life Service 微服务系统 |
| 部署环境 | 云服务器 K3s 集群 |
| Kubernetes namespace | `default` |
| 被保护服务 | `life-assistant-merchant-service` |
| 故障依赖服务 | `life-assistant-order-service` |
| 外部访问入口 | `http://47.120.37.61:30081` |
| 故障注入方式 | 将 `life-assistant-order-service` 缩容到 0 |
| 恢复方式 | 使用恢复脚本将 `life-assistant-order-service` 恢复到原副本数 |
| 镜像版本 | `50d3346ee281e2de2ed57bfe9139fe8feb72b1c4` |

## 3. 原始材料

本报告依据以下 2026-09-03 实验材料编写：

| 材料 | 路径 | 说明 |
| --- | --- | --- |
| Kubernetes 观测目录 | `reports/fault/fault-observe-20260903-102823/` | 故障实验期间采集的 Deployment、Endpoint、HPA、Pod、Event 和服务日志 |
| Deployment 时间序列 | `reports/fault/fault-observe-20260903-102823/deployments.csv` | 记录服务副本数变化 |
| Endpoint 时间序列 | `reports/fault/fault-observe-20260903-102823/endpoints.csv` | 记录 Service 可用后端地址变化 |
| HPA 时间序列 | `reports/fault/fault-observe-20260903-102823/hpa.csv` | 记录 HPA 副本和 CPU 指标变化 |
| Pod 资源时间序列 | `reports/fault/fault-observe-20260903-102823/pods-top.csv` | 记录 Pod CPU 和内存采样 |
| 商家服务日志 | `reports/fault/fault-observe-20260903-102823/protected-service.log` | 记录商家看板调用依赖和降级处理 |
| 订单服务日志 | `reports/fault/fault-observe-20260903-102823/dependency-service.log` | 记录订单服务恢复启动过程 |
| S4 探测结果 | `reports/sentinel/sentinel-check-20260903-103402-dependency-fallback/` | 故障期间对商家看板的 HTTP 探测结果 |

## 4. 实验设计

### 4.1 被测链路

本次实验关注以下调用链：

```text
客户端 -> api-gateway -> merchant-service -> order-service
```

其中 `merchant-service` 对外提供商家看板接口，`order-service` 提供订单统计和收入趋势等内部汇总数据。若 `order-service` 不可用，理想行为不是让商家看板整体失败，而是返回临时看板数据并标记降级状态。

### 4.2 故障模型

故障模型为依赖服务完全不可用，即 `order-service` 没有可用 Pod，也没有可用 Endpoint。该故障能够模拟服务发布失败、服务崩溃、节点故障、网络隔离或注册发现实例全部消失等场景。

### 4.3 判定标准

| 判定项 | 通过标准 |
| --- | --- |
| 故障注入有效 | `order-service` Deployment 期望副本数变为 0，Endpoint 为 `none` |
| 被保护链路可用 | `GET /api/merchant/dashboard` 不返回 500，不发生连接级错误 |
| 业务降级生效 | 商家看板返回 HTTP `200`、业务 `code=200`、`data.degraded=true` |
| 故障隔离有效 | Gateway、merchant-service、user-service、Nacos、MySQL、Redis 等非故障服务保持可用 |
| 恢复有效 | `order-service` 恢复后 Endpoint 重新出现，Pod Running |

## 5. 实验过程与观测结果

### 5.1 故障注入前状态

观测开始时间为 `2026-09-03T10:28:25+08:00`。此时核心服务均处于 Running 状态：

| 服务 | 初始状态 |
| --- | --- |
| `life-assistant-api-gateway` | 3/3 available |
| `life-assistant-merchant-service` | 2/2 available |
| `life-assistant-order-service` | 1/1 available |
| `life-assistant-user-service` | 1/1 available |
| `life-assistant-mysql` | 1/1 Running |
| `life-assistant-nacos` | 1/1 Running |
| `life-assistant-redis` | 1/1 Running |

Endpoint 初始状态显示 `order-service` 有一个可用后端地址 `10.42.0.198:8083`，说明故障注入前依赖链路存在可用实例。

### 5.2 故障注入前基线访问

Sentinel 基线探测材料显示，故障注入前 `GET /api/captcha` 与 `GET /api/search?keyword=Braised` 共 24 次请求全部返回 HTTP `200` 和业务 `code=200`，无 `429`、无 `503`、无降级响应，平均延迟为 169.66 ms。

该结果说明实验开始时外部入口和基础业务链路可用，后续故障现象可以归因于依赖故障注入，而不是系统初始状态异常。

### 5.3 故障注入过程

实验通过脚本将 `life-assistant-order-service` 缩容到 0。Deployment 时间序列显示：

| 时间 | `order-service` 状态 |
| --- | --- |
| 10:28:25 - 10:30:15 | desired=1，ready=1，available=1 |
| 10:30:36 | desired 从 1 调整到 3，HPA 曾短暂扩容 |
| 10:31:36 | desired=0，ready/available 为空 |
| 10:31:36 - 10:36:08 | desired=0，服务处于故障注入状态 |
| 10:36:30 | desired=3，ready=3，available=3，恢复完成 |

Endpoint 时间序列显示：

| 时间 | `order-service` Endpoint |
| --- | --- |
| 10:28:25 - 10:31:16 | `10.42.0.198` |
| 10:31:36 - 10:36:08 | `none` |
| 10:36:30 | `10.42.0.216 10.42.0.217 10.42.0.218` |

因此，本次实验的核心故障窗口可以界定为 `2026-09-03T10:31:36+08:00` 至 `2026-09-03T10:36:08+08:00`。在该时间段内，`order-service` 没有可用 Endpoint，故障注入有效。

### 5.4 故障期间商家看板探测

故障期间使用 Bash 探测脚本对商家看板执行 S4 探测。结果目录为 `reports/sentinel/sentinel-check-20260903-103402-dependency-fallback/`。

| 指标 | 数值 |
| --- | ---: |
| 总请求数 | 11 |
| 登录请求数 | 1 |
| 商家看板请求数 | 10 |
| HTTP 200 | 11 |
| 业务 code=200 | 11 |
| HTTP 429 | 0 |
| 业务 429 | 0 |
| HTTP 503 | 0 |
| 业务 503 | 0 |
| 降级响应数 | 10 |
| 平均延迟 | 265.00 ms |

10 次商家看板请求全部返回 `data.degraded=true`，并标识 `data.degradedDependency=order-service`。典型响应包含以下语义：

| 字段 | 值 |
| --- | --- |
| `code` | `200` |
| `message` | `success` |
| `data.degraded` | `true` |
| `data.degradedDependency` | `order-service` |
| `data.fallbackReason` | `remote code 503` |
| `data.degradationMessage` | `订单服务暂不可用，已返回临时看板数据，请稍后刷新。` |

结论：`order-service` 完全不可用时，商家看板没有向用户暴露 500 或连接失败，而是返回临时看板数据，业务降级生效。

### 5.5 服务日志证据

`protected-service.log` 中可以看到故障期商家服务调用订单服务时的关键日志：

```text
No servers available for service: order-service
Load balancer does not contain an instance for the service order-service
Feign call failed: methodKey=OrderSummaryClient#getMerchantDashboardResult(Long), status=503
Order summary dependency returned business error 503 for merchant 20001
```

随后商家看板返回包含 `degraded=true`、`degradedDependency=order-service`、`fallbackReason=remote code 503` 的成功响应。该日志链路证明降级不是探测脚本臆测，而是业务服务在依赖不可用时实际执行了兜底逻辑。

`dependency-service.log` 显示恢复阶段 `order-service` 重新启动，加载 Nacos 配置 `order-service.yml` 和 `life-assistant-common.yml`，注册 Sentinel Web 拦截器，并在 `10:36:15` 左右完成 Nacos 服务注册。随后健康检查返回 `status=UP`。

### 5.6 故障隔离观察

故障期间 `merchant-service` 没有因为下游 `order-service` 缺失而不可用。Deployment 时间序列显示 `life-assistant-merchant-service` 在实验期间从 2 个副本逐步扩容到 5 个副本，并在故障窗口内保持 `ready=5`、`available=5`。

Gateway、user-service、Nacos、MySQL、Redis 等服务在观测首尾均保持 Running。Endpoint 观测显示，除被注入故障的 `order-service` 外，`api-gateway`、`merchant-service`、`user-service`、`nacos` 等服务均保留可用 Endpoint。该现象说明订单服务故障没有导致网关或商家服务整体崩溃。

### 5.7 资源与 HPA 观察

`hpa.csv` 和 `pods-top.csv` 显示实验期间存在 HPA 扩缩容行为：

| 服务 | 资源变化 |
| --- | --- |
| `life-assistant-merchant-service` | HPA 曾因 CPU 超过目标从 2 个副本扩到 4 个，再到 5 个；故障窗口内保持 5 个副本可用 |
| `life-assistant-order-service` | 故障前曾被 HPA 扩到 3 个副本，故障注入后 desired=0；恢复后重新达到 3 个可用副本 |
| `life-assistant-api-gateway` | 从 3 个副本逐步回落到 1 个副本，Endpoint 始终保留可用地址 |

Pod 资源采样显示：

| 组件 | CPU 范围 | 内存范围 |
| --- | ---: | ---: |
| api-gateway | 7m - 65m | 249Mi - 269Mi |
| merchant-service | 7m - 502m | 78Mi - 316Mi |
| order-service | 7m - 502m | 61Mi - 307Mi |
| user-service | 9m - 53m | 299Mi - 316Mi |
| fulfillment-service | 8m - 501m | 87Mi - 305Mi |
| settlement-service | 6m - 20m | 288Mi - 290Mi |
| mysql | 13m - 24m | 471Mi - 487Mi |
| nacos | 9m - 20m | 580Mi - 586Mi |
| redis | 8m - 12m | 4Mi - 8Mi |

实验期间存在部分新 Pod 启动初期的 readiness/liveness 探针失败，典型表现为 `connect: connection refused` 或 `context deadline exceeded`。结合事件时间和后续 Running 状态判断，这些主要发生在滚动更新、HPA 扩容或服务恢复启动阶段，没有形成持续性不可用。

## 6. 恢复验证

恢复阶段 `order-service` 从 0 个副本恢复到 3 个副本。`endpoints.csv` 在 `10:36:30` 记录到 `order-service` Endpoint 恢复为 `10.42.0.216 10.42.0.217 10.42.0.218`。`deployments-end.txt` 显示 `life-assistant-order-service` 最终为 `3/3 available`。

`dependency-service.log` 显示订单服务恢复后健康检查返回 `code=200`、`status=UP`、`databaseStatus=UP`，说明服务进程、Web 容器、Nacos 注册和数据库连接均恢复正常。

## 7. 综合判定

| 判定项 | 实验结果 | 判定 |
| --- | --- | --- |
| 故障注入有效 | `order-service` Endpoint 在 10:31:36 - 10:36:08 为 `none` | 通过 |
| 商家看板降级 | 故障期 10 次看板请求全部返回 `degraded=true` | 通过 |
| 错误隔离 | 看板返回业务成功和临时数据，无 HTTP 500/503 | 通过 |
| 依赖恢复 | `order-service` 恢复为 3 个可用副本，Endpoint 重新出现 | 通过 |
| 非故障服务稳定性 | Gateway、merchant-service、Nacos、MySQL、Redis 保持可用 | 通过 |

总体结论：本次故障容错实验通过。系统在 `order-service` 完全不可用时，能够通过 Feign 异常治理和商家看板降级逻辑，将下游故障转换为可解释的临时业务数据，避免故障向用户界面扩散；依赖恢复后，服务 Endpoint 和健康检查恢复正常。

## 8. 发现的问题与风险

### 8.1 故障窗口内只有商家看板被重点验证

本次 fault 实验重点验证的是 `merchant-service -> order-service` 的商家看板链路。它能够证明该链路具备较好的故障降级能力，但不能直接证明所有跨服务链路都已经具备同等容错能力。订单创建、支付状态回写、骑手接单、评价写入等链路仍需要单独设计故障实验。

### 8.2 HPA 与故障注入存在相互影响

实验中可以看到 `order-service` 在故障注入前曾被 HPA 扩到 3 个副本，故障注入后 desired 变为 0，恢复后又回到 3 个副本。说明手工缩容故障注入与 HPA 控制器会共同影响副本数。后续复现实验时，应记录注入前副本数，并确认恢复脚本读取和恢复的是正确状态。

### 8.3 启动期探针失败会干扰事件判断

Kubernetes Events 中存在新 Pod 启动期 readiness/liveness 探针失败。这些事件不等同于业务故障，但会增加报告分析噪声。后续建议将事件按对象和时间窗口过滤，区分“故障注入导致的不可用”和“扩容启动期间的短暂未就绪”。

### 8.4 旁路接口结果未形成独立归档

当前材料中已经能证明商家看板降级有效，Kubernetes 观测也显示旁路服务未整体不可用。但如果要更严格证明“旁路接口可用”，建议将 `GET /api/search`、`GET /api/merchant/profile` 等旁路接口探测也写入 CSV/JSONL，而不是只依赖命令行输出或人工观察。

## 9. 优化建议

1. 将 fault 探测脚本扩展为 Bash 版，直接输出 baseline、fault、recovery 三阶段 CSV 和 JSONL，避免 Windows PowerShell 与云服务器 Bash 环境割裂。
2. 对每个关键跨服务链路建立独立故障用例。例如 `order-service -> merchant-service` 商品快照、`settlement-service -> order-service` 支付回写、`fulfillment-service -> order-service` 配送状态更新等。
3. 在故障注入脚本中显式暂停或记录 HPA 状态，避免 HPA 在实验期间自动扩缩容导致副本数判断复杂化。
4. 将旁路接口探测纳入自动报告，形成“被保护链路降级”和“非相关链路不受影响”的双重证据。
5. 为启动期探针失败设置单独分析段落，或者在采集脚本中标记服务重启、扩容、恢复阶段，减少事件误读。

## 10. 结论

2026-09-03 的故障容错实验验证了当前系统对 `order-service` 不可用故障的基本隔离和降级能力。`order-service` Endpoint 在故障窗口内确认为 `none`，商家看板在该期间 10 次请求全部返回 `code=200` 和 `data.degraded=true`，并给出明确降级提示。恢复后，订单服务重新注册 Endpoint，Pod 和健康检查恢复正常。

因此，可以认为当前商家看板链路已经具备可复现的依赖故障容错能力。后续工作应从“单链路证明”推进到“主要跨服务链路覆盖”，并增强旁路接口和恢复期探测的自动化归档。
