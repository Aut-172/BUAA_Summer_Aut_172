# 故障处理容错实验步骤说明书

## 1. 实验目标

本实验用于验证微服务系统在依赖服务发生故障时，是否能够通过超时控制和降级返回避免故障扩散。

本项目当前实验方案：

- 主动停止依赖服务 `order-service`。
- 访问受保护服务 `merchant-service` 的商家看板接口。
- 期望商家看板接口不返回 500，而是返回事先设计好的备用结果和提示语。
- 同时验证 `merchant-service` 的其他接口仍然可用，说明其他功能没有跟着一起崩溃。

本轮实现采用本地代码级容错：`merchant-service` 调用 `order-service` 时设置 Feign 超时，并在远程调用失败时返回备用看板数据。脚本均保留了参数化入口，后续可以继续扩展到 Sentinel、Resilience4j、Service Mesh 或 Chaos Mesh。

## 2. 实验对象

| 项目 | 默认值 |
| --- | --- |
| 受保护 Deployment | `life-assistant-merchant-service` |
| 依赖 Deployment | `life-assistant-order-service` |
| 故障注入方式 | 将依赖 Deployment 缩容到 `0` |
| 受保护接口 | `GET /api/merchant/dashboard` |
| 旁路验证接口 | `GET /api/merchant/profile`、`GET /api/search?keyword=Braised` |
| 预期降级结果 | HTTP 2xx，业务 `code=200`，`data.degraded=true` |
| Windows 探测脚本 | `load-tests/run-fault-tolerance-check.ps1` |
| ECS 故障注入脚本 | `scripts/fault-inject-dependency.sh` |
| ECS 故障恢复脚本 | `scripts/fault-restore-dependency.sh` |
| ECS 采集脚本 | `scripts/collect-fault-experiment.sh` |
| 报告生成脚本 | `scripts/write-fault-tolerance-report.ps1` |

## 3. 部署实验代码

本次代码应位于分支：

```bash
codex/fault-tolerance-experiment
```

Windows 本机提交并推送：

```powershell
cd E:\Develop\IDEA\IdeaProject\new
git status
git add services/merchant-service k8s/business-services.yaml scripts load-tests docs
git commit -m "Add fault tolerance experiment for merchant dashboard"
git push -u origin codex/fault-tolerance-experiment
```

如果 GitHub Actions 的 CD 会自动部署该分支，等待流水线完成即可。

如果 CD 只部署 `main`，需要合并 PR 到 `main` 后再等待流水线。

如果需要手动部署，在 ECS 云服务器执行：

```bash
cd ~/life-service
git pull
kubectl apply -f k8s/business-services.yaml
kubectl rollout status deployment/life-assistant-merchant-service --timeout=240s
kubectl get pods -o wide
```

确认 `merchant-service` 新 Pod 正常运行后再开始实验。

## 4. 检查脚本权限和语法

在 ECS 云服务器执行：

```bash
cd ~/life-service
chmod +x scripts/fault-inject-dependency.sh
chmod +x scripts/fault-restore-dependency.sh
chmod +x scripts/collect-fault-experiment.sh

bash -n scripts/fault-inject-dependency.sh
bash -n scripts/fault-restore-dependency.sh
bash -n scripts/collect-fault-experiment.sh
```

如果 `bash -n` 没有输出，表示 Shell 脚本语法检查通过。

Windows 本机可检查 PowerShell 脚本：

```powershell
powershell -NoProfile -Command '$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile("load-tests/run-fault-tolerance-check.ps1",[ref]$null,[ref]$errors) > $null; if ($errors.Count -gt 0) { $errors | ForEach-Object { Write-Error $_.Message }; exit 1 }'

powershell -NoProfile -Command '$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile("scripts/write-fault-tolerance-report.ps1",[ref]$null,[ref]$errors) > $null; if ($errors.Count -gt 0) { $errors | ForEach-Object { Write-Error $_.Message }; exit 1 }'
```

## 5. 基线探测

先不要注入故障，先确认系统正常状态下能通过探测。

在 Windows PowerShell 执行：

```powershell
cd E:\Develop\IDEA\IdeaProject\new

powershell -ExecutionPolicy Bypass -File load-tests\run-fault-tolerance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -Phase baseline `
  -Iterations 5 `
  -IntervalSeconds 2
```

基线期预期：

- `merchant-login` 成功。
- `merchant-dashboard` 成功。
- `merchant-dashboard` 的 `degraded` 为 `False` 或空。
- `merchant-profile` 成功。
- `merchant-search` 成功。

如果基线失败，不要继续注入故障。应先检查网关地址、NodePort、安全组、商家账号、服务部署版本。

## 6. 启动 Kubernetes 采集

在 ECS 云服务器打开一个专门采集的 SSH 窗口，执行：

```bash
cd ~/life-service
DURATION_SECONDS=600 INTERVAL_SECONDS=10 bash scripts/collect-fault-experiment.sh
```

参数说明：

- `DURATION_SECONDS=600` 表示采集 10 分钟。
- `INTERVAL_SECONDS=10` 表示每 10 秒采样一次。
- 默认采集受保护服务、依赖服务、网关、用户服务、HPA、Endpoint、事件和日志。

采集脚本会持续输出类似：

```text
[2026-09-01T14:00:00+08:00] sample=1 elapsed=1s protected=1/1 dependency=1/1
```

如果实验提前完成，可以按 `Ctrl+C` 结束采集。脚本会自动收尾并生成 `.tgz` 归档。

## 7. 注入依赖故障

在 ECS 云服务器打开第二个 SSH 窗口，执行：

```bash
cd ~/life-service
bash scripts/fault-inject-dependency.sh
```

默认行为：

- 保存 `life-assistant-order-service` 当前 Deployment 配置。
- 保存对应 HPA 配置。
- 删除 `order-service` 的 HPA，避免它自动把副本拉回 1。
- 将 `life-assistant-order-service` 缩容到 0。

确认故障注入结果：

```bash
kubectl get deploy life-assistant-order-service
kubectl get pods | grep order
kubectl get hpa
kubectl get endpoints order-service
```

预期：

- `life-assistant-order-service` 的期望副本为 0。
- `order-service` Pod 消失。
- `order-service` Endpoint 没有可用地址。
- `merchant-service`、`api-gateway` 等其他服务仍在运行。

## 8. 故障期探测

在 Windows PowerShell 执行：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-fault-tolerance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -Phase fault `
  -Iterations 10 `
  -IntervalSeconds 3
```

故障期关键预期：

| 接口 | 预期 |
| --- | --- |
| `merchant-dashboard` | HTTP 2xx，业务 `code=200`，`degraded=True` |
| `merchant-dashboard` | `dependency=order-service` |
| `merchant-dashboard` | 返回提示 `订单服务暂不可用，已返回临时看板数据，请稍后刷新。` |
| `merchant-dashboard` | 今日订单、今日营收、待处理订单临时返回 0 |
| `merchant-profile` | 继续可用 |
| `merchant-search` | 继续可用 |

这一步是本实验最核心证据：依赖服务不可用，但受保护接口没有 500，其他接口没有被连带拖垮。

## 9. 恢复依赖服务

在 ECS 第二个 SSH 窗口执行：

```bash
cd ~/life-service
bash scripts/fault-restore-dependency.sh
```

默认行为：

- 从故障状态目录读取原始副本数。
- 将 `life-assistant-order-service` 恢复到原始副本数。
- 优先重新 apply 仓库中的 `k8s/hpa.yaml`。
- 等待 `order-service` Deployment rollout 完成。

确认恢复：

```bash
kubectl get deploy life-assistant-order-service
kubectl get pods | grep order
kubectl get hpa life-assistant-order-service
kubectl get endpoints order-service
```

预期：

- `order-service` Pod 重新 Running。
- HPA 重新存在。
- `order-service` Endpoint 恢复。

## 10. 恢复期探测

在 Windows PowerShell 执行：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-fault-tolerance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -Phase recovery `
  -Iterations 5 `
  -IntervalSeconds 2
```

恢复期预期：

- `merchant-dashboard` 恢复正常数据。
- `merchant-dashboard` 的 `degraded` 为 `False` 或空。
- `merchant-profile`、`merchant-search` 继续正常。

## 11. 停止采集并取回结果

ECS 采集窗口如果仍在运行，可以等待自然结束，也可以按 `Ctrl+C`。

脚本会输出：

```text
Fault observation files: reports/fault/fault-observe-YYYYMMDD-HHMMSS
Archive: reports/fault/fault-observe-YYYYMMDD-HHMMSS.tgz
```

在 Windows PowerShell 取回归档：

```powershell
scp -i E:\Develop\pem\buaa-summer.pem `
  root@47.120.37.61:/root/life-service/reports/fault/fault-observe-YYYYMMDD-HHMMSS.tgz `
  E:\Develop\IDEA\IdeaProject\new\reports\fault\
```

解压：

```powershell
tar -xzf reports\fault\fault-observe-YYYYMMDD-HHMMSS.tgz -C .
```

## 12. 生成实验报告

在 Windows PowerShell 执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\write-fault-tolerance-report.ps1 `
  -ProbeRoot "reports\fault" `
  -ObservationDir "reports\fault\fault-observe-YYYYMMDD-HHMMSS" `
  -OutputPath "reports\fault\fault-tolerance-experiment-report.md"
```

脚本会读取：

- `reports/fault/fault-check-*/probe-results.csv`
- `reports/fault/fault-observe-*/deployments.csv`

并生成 Markdown 报告。

如果需要更正式的中文课程报告，可以继续基于以下文件整理：

| 文件 | 用途 |
| --- | --- |
| `probe-results.csv` | 每次接口探测结果、耗时、是否降级 |
| `probe-results.json` | 完整探测响应 |
| `deployments.csv` | 受保护服务和依赖服务副本变化 |
| `pods-top.csv` | Pod CPU 和内存 |
| `endpoints.csv` | 依赖服务 Endpoint 是否消失和恢复 |
| `events-end.txt` | Kubernetes 事件 |
| `protected-service.log` | `merchant-service` 日志 |
| `dependency-service.log` | `order-service` 日志 |

## 13. 判断实验是否成功

实验成功需要同时满足：

- 基线期 `merchant-dashboard` 正常返回，未降级。
- 故障期 `order-service` 确实被缩容到 0，Endpoint 消失。
- 故障期 `merchant-dashboard` 返回 HTTP 2xx 和业务 `code=200`。
- 故障期 `merchant-dashboard` 返回 `data.degraded=true`。
- 故障期返回了设计好的提示语或备用结果。
- 故障期 `merchant-profile`、`merchant-search` 等旁路接口仍可用。
- 恢复期 `order-service` 恢复 Running，Endpoint 恢复。
- 恢复期 `merchant-dashboard` 恢复正常，不再降级。

## 14. 风险控制

- 第一轮只停止无状态业务服务 `order-service`，不要直接停止 MySQL、Redis 或 Nacos。
- 注入故障前必须确认恢复脚本可用。
- 故障期间订单相关功能会受影响，建议在非生产时间执行。
- 如果恢复脚本失败，可以手动恢复：

```bash
kubectl scale deployment life-assistant-order-service --replicas=1
kubectl apply -f k8s/hpa.yaml
kubectl rollout status deployment/life-assistant-order-service --timeout=240s
```

## 15. 为未来中间件实验预留的扩展点

当前实验脚本刻意保留了环境变量参数，便于后续替换故障注入方式或容错机制。

例如替换观测目标：

```bash
PROTECTED_DEPLOYMENT=life-assistant-fulfillment-service \
DEPENDENCY_DEPLOYMENT=life-assistant-order-service \
TARGET_HPAS="life-assistant-fulfillment-service life-assistant-order-service" \
bash scripts/collect-fault-experiment.sh
```

未来可以扩展的方向：

- Sentinel：加入熔断、限流、热点参数保护和 Dashboard 观测。
- Resilience4j：加入 CircuitBreaker、TimeLimiter、Retry、Bulkhead。
- Service Mesh：用流量规则注入延迟、错误率或熔断策略。
- Chaos Mesh：用标准 Chaos 实验对象替代手写缩容脚本。

建议保持“基线探测、注入故障、故障期探测、恢复、恢复期探测、报告生成”的流程不变，只替换内部容错机制或故障注入机制。这样不同实验之间的数据更容易横向对比。
