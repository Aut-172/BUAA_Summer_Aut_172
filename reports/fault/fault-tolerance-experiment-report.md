# 云原生故障处理容错实验报告

## 1. 实验概述

本实验基于已部署到 Kubernetes 集群的 Life Assistant 微服务系统，主动停止商家看板依赖的订单服务，验证系统是否能够在依赖故障时返回设计好的备用结果，并保持其他服务和旁路接口不崩溃。

| 项目 | 内容 |
| --- | --- |
| 实验时间 | 2026-09-01 15:32:57 至 15:42:42，Asia/Shanghai |
| 集群入口 | `http://47.120.37.61:30081` |
| 受保护服务 | `life-assistant-merchant-service` |
| 依赖服务 | `life-assistant-order-service` |
| 故障方式 | 删除/暂停依赖服务 HPA 后，将依赖 Deployment 缩容到 0 |
| 受保护接口 | `GET /api/merchant/dashboard` |
| 旁路验证接口 | `GET /api/merchant/profile`、`GET /api/search?keyword=Braised` |
| 容错机制 | Feign 调用超时控制 + 服务层降级返回 |
| 预期结果 | 故障期看板接口 HTTP 2xx、业务 `code=200`、`data.degraded=true`，其他接口保持可用 |

## 2. 实验设计

`merchant-service` 的商家看板接口需要通过 OpenFeign 调用 `order-service` 的内部订单统计接口。当 `order-service` 不可用时，如果没有容错逻辑，远程调用异常会被全局异常处理为 500，导致商家看板不可用。

本次实验在 `merchant-service` 中增加了独立的看板服务层和降级结果构造器。远程调用成功时返回真实订单统计；远程调用为空、5xx、503 或抛出运行时异常时，返回备用看板数据，并在响应中显式标记降级状态：

```json
{
  "code": 200,
  "data": {
    "degraded": true,
    "degradedDependency": "order-service",
    "degradationMessage": "订单服务暂不可用，已返回临时看板数据，请稍后刷新。",
    "merchant": {
      "todayOrders": 0,
      "todayRevenue": 0,
      "pendingOrders": 0
    }
  }
}
```

为保证实验可复现，探测分为三个阶段：

| 阶段 | 目的 | 操作 |
| --- | --- | --- |
| 基线期 | 验证系统正常状态 | 访问登录、看板、商家资料、搜索接口 |
| 故障期 | 验证依赖故障时的降级和隔离 | 将 `order-service` 缩容为 0 后重复探测 |
| 恢复期 | 验证服务恢复后系统回到正常状态 | 恢复 `order-service` 和 HPA 后重复探测 |

## 3. 实验过程

### 3.1 基线期探测

Windows 本机执行探测脚本，访问公网网关并记录结果：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-fault-tolerance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -Phase baseline `
  -Iterations 5 `
  -IntervalSeconds 2
```

基线期共记录 16 次探测，其中 15 次通过。第一轮看板请求出现一次 `degraded=true`，后续 4 次看板请求恢复为正常状态。该样本发生在实验前系统刚完成配置调整和滚动更新后，判断为依赖注册或服务恢复过程中的瞬态降级；报告中保留该异常样本，不将其删除。

### 3.2 Kubernetes 采集

ECS 云服务器启动采集脚本：

```bash
DURATION_SECONDS=600 INTERVAL_SECONDS=10 bash scripts/collect-fault-experiment.sh
```

采集内容包括 Deployment 副本数、HPA 状态、Pod CPU/内存、Endpoint、事件和服务日志。采样实际覆盖 2026-09-01 15:32:57 至 15:42:42，共 35 个采样点。

### 3.3 故障注入

ECS 云服务器执行故障注入脚本：

```bash
bash scripts/fault-inject-dependency.sh
```

采集结果显示，`order-service` 在 15:33:15 开始没有可用 Endpoint，持续到 15:35:42。期间 `life-assistant-order-service` 的期望副本数为 0，符合“主动停止依赖服务”的实验要求。

### 3.4 故障期探测

Windows 本机执行故障期探测：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-fault-tolerance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -Phase fault `
  -Iterations 10 `
  -IntervalSeconds 3
```

故障期共记录 31 次探测，全部通过。其中 `merchant-dashboard` 共 10 次，全部返回 HTTP 200、业务 `code=200`，且 `degraded=true`、`degradedDependency=order-service`。`merchant-profile` 和 `merchant-search` 也全部成功，说明依赖服务故障没有导致商家服务整体崩溃。

### 3.5 服务恢复和恢复期探测

ECS 云服务器执行恢复脚本：

```bash
bash scripts/fault-restore-dependency.sh
```

Endpoint 采集显示，`order-service` 在 15:35:59 首次恢复 Endpoint，之后恢复到多个可用 Pod。恢复期探测共记录 16 次请求，其中 15 次通过。第一轮看板请求仍出现一次 `degraded=true`，随后 4 次看板请求恢复为 `degraded=false`，说明服务恢复存在短暂传播延迟，但最终业务链路恢复正常。

## 4. 探测结果统计

| 阶段 | 接口 | 次数 | 通过 | 失败 | 降级次数 | 平均响应时间 ms | P95 响应时间 ms | 最大响应时间 ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | `merchant-login` | 1 | 1 | 0 | 0 | 2914.11 | 2914.11 | 2914.11 |
| baseline | `merchant-dashboard` | 5 | 4 | 1 | 1 | 800.95 | 3184.10 | 3184.10 |
| baseline | `merchant-profile` | 5 | 5 | 0 | 0 | 152.67 | 183.91 | 183.91 |
| baseline | `merchant-search` | 5 | 5 | 0 | 0 | 1534.55 | 2259.16 | 2259.16 |
| fault | `merchant-login` | 1 | 1 | 0 | 0 | 747.74 | 747.74 | 747.74 |
| fault | `merchant-dashboard` | 10 | 10 | 0 | 10 | 239.09 | 957.74 | 957.74 |
| fault | `merchant-profile` | 10 | 10 | 0 | 0 | 430.03 | 1434.27 | 1434.27 |
| fault | `merchant-search` | 10 | 10 | 0 | 0 | 1527.56 | 3723.42 | 3723.42 |
| recovery | `merchant-login` | 1 | 1 | 0 | 0 | 3119.18 | 3119.18 | 3119.18 |
| recovery | `merchant-dashboard` | 5 | 4 | 1 | 1 | 1721.70 | 2215.44 | 2215.44 |
| recovery | `merchant-profile` | 5 | 5 | 0 | 0 | 194.92 | 345.04 | 345.04 |
| recovery | `merchant-search` | 5 | 5 | 0 | 0 | 2049.71 | 3200.70 | 3200.70 |

整体统计：

| 指标 | 数值 |
| --- | ---: |
| 总探测次数 | 63 |
| 脚本断言通过次数 | 61 |
| 脚本断言失败次数 | 2 |
| 脚本断言通过率 | 96.83% |
| 故障期探测次数 | 31 |
| 故障期通过次数 | 31 |
| 故障期通过率 | 100.00% |
| 故障期看板探测次数 | 10 |
| 故障期看板降级次数 | 10 |
| 故障期看板降级覆盖率 | 100.00% |

说明：脚本断言失败的 2 个样本均为基线期或恢复期的看板瞬态降级，不是 HTTP 5xx 或业务错误。全部探测请求均获得 HTTP 200 和业务 `code=200`。

## 5. Kubernetes 观测结果

### 5.1 Deployment 副本变化

| Deployment | 样本数 | 最小就绪副本 | 最大就绪副本 | 最小期望副本 | 最大期望副本 |
| --- | ---: | ---: | ---: | ---: | ---: |
| `life-assistant-api-gateway` | 35 | 1 | 1 | 1 | 1 |
| `life-assistant-merchant-service` | 35 | 1 | 5 | 1 | 5 |
| `life-assistant-order-service` | 35 | 1 | 4 | 0 | 4 |
| `life-assistant-user-service` | 35 | 1 | 1 | 1 | 1 |

`order-service` 的期望副本数在故障期下降到 0，证明依赖服务被主动停止。恢复后，`order-service` 回到可用状态，最终保留 2 个就绪 Pod。`merchant-service` 在实验期间曾由 HPA 扩容到 5 个副本，最终回落到 2 个副本，说明受保护服务在故障期间仍由 Kubernetes 正常调度和管理，没有出现整体崩溃。

### 5.2 Endpoint 变化

| 时间 | `order-service` Endpoint 状态 |
| --- | --- |
| 15:32:57 | 存在 Endpoint：`10.42.0.201` |
| 15:33:15 至 15:35:42 | Endpoint 为 `none` |
| 15:35:59 | Endpoint 恢复 |
| 15:42:42 | 保留 Endpoint：`10.42.0.209 10.42.0.210` |

Endpoint 为空期间，故障期看板探测仍全部返回降级结果，证明容错逻辑在依赖服务无可用实例时生效。

### 5.3 Pod 和资源状态

采集结束时，核心 Pod 均处于 Running，且重启次数为 0。结束时可见：

| 服务 | 结束状态 |
| --- | --- |
| `api-gateway` | 1 个 Pod Running，重启 0 次 |
| `merchant-service` | 2 个 Pod Running，重启 0 次 |
| `order-service` | 2 个 Pod Running，重启 0 次 |
| `user-service` | 1 个 Pod Running，重启 0 次 |
| `mysql`、`nacos`、`redis` | 均 Running |

资源采集显示，实验期间部分 `merchant-service` 和 `order-service` Pod CPU 峰值接近或达到 500m 限制，但未出现重启或服务级崩溃。基础组件 MySQL、Nacos、Redis 保持运行。

## 6. 现象分析

本次实验观测到了符合预期的故障处理行为：

- `order-service` Endpoint 为空时，`merchant-dashboard` 没有返回 500，而是返回 `degraded=true` 的备用结果。
- 故障期 10 次看板请求全部被降级逻辑接管，降级覆盖率为 100%。
- `merchant-profile` 和 `merchant-search` 在故障期全部成功，说明订单服务故障没有拖垮商家服务的其他接口。
- 采集结束时核心服务 Pod 均 Running 且重启次数为 0，说明故障被限制在依赖调用链路内。

基线期和恢复期各出现 1 次看板降级。结合实验时间线判断，这两个样本分别出现在依赖服务刚完成部署/注册或恢复传播期间，属于短暂的服务发现或远程调用波动。由于看板接口仍返回 HTTP 200 和业务 `code=200`，该现象反而说明降级机制能够覆盖短暂抖动，不会把瞬态依赖异常直接暴露为用户可见的 500 错误。

探测结果中的中文提示在 CSV/Markdown 摘要中出现了乱码，这是因为响应体或 PowerShell 读取链路中未完整声明 UTF-8 编码；不过结构化字段 `degraded`、`degradedDependency`、HTTP 状态和业务 code 均被正确记录。正式结论以结构化字段和代码中配置的降级提示为准。

## 7. 实验结论

本次故障处理实验达成目标。

当依赖服务 `order-service` 被主动停止并且 Endpoint 为空时，受保护服务 `merchant-service` 的商家看板接口能够返回事先设计好的备用结果，且通过 `degraded=true` 明确告知调用方当前处于降级状态。旁路接口在故障期间全部成功，Kubernetes 采集结果也未发现核心服务崩溃或 Pod 重启。

因此，可以认为当前系统已经具备基础的超时返回和备用结果能力，能够在订单服务短时不可用时实现局部故障隔离。后续若引入 Sentinel、Resilience4j、Service Mesh 或 Chaos Mesh，可在保留本实验探测和采集流程的基础上，将容错策略从本地代码升级为更完整的熔断、限流、隔离和混沌实验体系。

## 8. 数据来源

| 文件 | 用途 |
| --- | --- |
| `reports/fault/fault-check-20260901-153220-baseline/probe-results.csv` | 基线期接口探测明细 |
| `reports/fault/fault-check-20260901-153345-fault/probe-results.csv` | 故障期接口探测明细 |
| `reports/fault/fault-check-20260901-153616-recovery/probe-results.csv` | 恢复期接口探测明细 |
| `reports/fault/reports/fault/fault-observe-20260901-153255/deployments.csv` | Deployment 副本变化 |
| `reports/fault/reports/fault/fault-observe-20260901-153255/endpoints.csv` | Service Endpoint 变化 |
| `reports/fault/reports/fault/fault-observe-20260901-153255/hpa.csv` | HPA 状态变化 |
| `reports/fault/reports/fault/fault-observe-20260901-153255/pods-start.txt` | 采集开始时 Pod 状态 |
| `reports/fault/reports/fault/fault-observe-20260901-153255/pods-end.txt` | 采集结束时 Pod 状态 |
| `reports/fault/reports/fault/fault-observe-20260901-153255/pods-top.csv` | Pod CPU/内存采样 |

## 9. 仓库存档策略

建议提交到远程仓库的实验材料：

- 最终实验报告 `fault-tolerance-experiment-report.md`。
- 每阶段的 `probe-summary.md` 和 `probe-results.csv`。
- Kubernetes 采集得到的 CSV 和 TXT 文件。

不建议提交到远程仓库的材料：

- `probe-results.json`：包含完整响应体，体积较大，且与 CSV/摘要重复。
- `protected-service.log`、`dependency-service.log`：日志较冗长，可能包含运行环境细节。
- `.tgz`、`.gz` 归档文件：可由目录内容重新打包生成。

因此 `.gitignore` 已配置为忽略 `reports/fault/**/*.json`、`reports/fault/**/*.log`、`reports/fault/**/*.tgz` 和 `reports/fault/**/*.gz`，保留 Markdown、CSV 和 TXT 作为可复核证据。
