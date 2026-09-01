# HPA 自动扩缩容压测实验步骤说明书

## 1. 实验目标

本实验用于验证 Kubernetes 集群中的 HorizontalPodAutoscaler（HPA）是否能够根据业务服务负载自动调整 Pod 副本数。

期望观测到：

- 压力升高后，目标服务 CPU 利用率上升，HPA 将 Pod 副本数调高。
- 压力下降后，经过 HPA 缩容稳定窗口，Pod 副本数逐步回落。
- 能够记录吞吐量、平均响应时间、P95 响应时间、错误率、Pod 副本数变化、CPU/内存变化。

本项目默认使用 JMeter 从 Windows 本机访问云端公网入口进行压测，使用 ECS 云服务器上的 `kubectl` 采集 HPA 和 Pod 状态。

## 2. 实验对象

默认压测链路：

| 项目 | 默认值 |
| --- | --- |
| 网关公网地址 | `47.120.37.61` |
| 网关 NodePort | `30081` |
| 主要压测入口 | API Gateway |
| 主要观测服务 | `life-assistant-merchant-service`、`life-assistant-api-gateway` |
| JMeter 脚本 | `load-tests/life-assistant-hpa.jmx` |
| Windows 启动脚本 | `load-tests/run-hpa-jmeter.ps1` |
| ECS 采集脚本 | `scripts/collect-hpa-experiment.sh` |

如果后续要压测其他服务，可以通过脚本参数或环境变量调整目标接口、关键词、观测 Deployment 和 HPA。

## 3. 前置条件检查

### 3.1 集群和 Pod 状态

在 ECS 云服务器执行：

```bash
kubectl get nodes -o wide
kubectl get pods -o wide
kubectl get deploy
kubectl get svc
```

要求：

- 节点为 `Ready`。
- 业务 Pod 为 `Running`，且 `READY` 状态正常。
- API Gateway 的公网 NodePort 可以从 Windows 本机访问。

### 3.2 Metrics Server

如果以下命令能正常返回 CPU 和内存指标，说明 Metrics Server 已经可用，不需要重复安装：

```bash
kubectl top nodes
kubectl top pods
```

如果命令报错，例如 `Metrics API not available`，才需要安装或修复 Metrics Server。HPA 基于 CPU/内存指标工作，因此 `kubectl top` 不可用时不要开始实验。

### 3.3 资源请求和限制

HPA 使用 CPU 百分比扩缩容时，必须给容器配置 `resources.requests.cpu`。例如：

```yaml
resources:
  requests:
    cpu: "100m"
    memory: "384Mi"
  limits:
    cpu: "500m"
    memory: "768Mi"
```

本项目的业务服务资源配置位于：

- `k8s/business-services.yaml`
- `k8s/api-gateway.yaml`

两者关系：

- Deployment 的 `resources.requests.cpu` 定义 HPA 计算 CPU 利用率的基准。
- HPA 的 `averageUtilization` 表示当前 CPU 使用量相对于 request 的百分比。
- 如果没有 CPU request，HPA 通常无法正确计算 CPU 利用率。

### 3.4 HPA 配置

HPA 配置位于：

```bash
k8s/hpa.yaml
```

检查 HPA：

```bash
kubectl get hpa
kubectl describe hpa life-assistant-merchant-service
kubectl describe hpa life-assistant-api-gateway
```

如果修改了资源配置或 HPA 配置，需要重新部署：

```bash
kubectl apply -f k8s/business-services.yaml
kubectl apply -f k8s/api-gateway.yaml
kubectl apply -f k8s/hpa.yaml
```

如果 GitHub Actions 已经包含 CD，并且 CD 会在目标分支或 `main` 分支自动部署，则通常只需要 push 或合并 PR 后等待流水线完成。是否需要手工 `kubectl apply` 取决于你的 Actions 配置。

### 3.5 演示数据

压测前建议准备足够的演示商家和商品数据，避免接口很快返回空数据，影响响应时间和吞吐量代表性。

本项目已有演示数据脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-hpa-demo-data.ps1 `
  -UploadImages `
  -OssUtil "D:\Develop\oss\ossutil-v1.7.19-windows-amd64\ossutil64.exe" `
  -MerchantCount 80 `
  -ProductsPerMerchant 12
```

脚本会生成 SQL，例如：

```text
reports/perf/hpa-demo-data.sql
```

将 SQL 上传到 ECS 后，可通过 MySQL Pod 执行导入。示例：

```bash
kubectl cp reports/perf/hpa-demo-data.sql default/life-assistant-mysql-xxxx:/tmp/hpa-demo-data.sql
kubectl exec -it life-assistant-mysql-xxxx -- mysql -uroot -p merchant_db < /tmp/hpa-demo-data.sql
```

实际执行时请将 `life-assistant-mysql-xxxx` 替换为当前 MySQL Pod 名称，并根据 SQL 内容确认目标数据库。

## 4. 压测位置选择

推荐从 Windows 本机压测云服务公网端口，而不是 SSH 登录 ECS 后在服务器本机压测。

原因：

- Windows 本机访问公网入口，更接近真实用户流量路径。
- 不会让压测客户端和 Kubernetes 节点争抢同一台 ECS 的 CPU、内存和网络资源。
- JMeter 已安装在 Windows 本机，使用更方便。

只有在公网网络不稳定、需要排除外部网络影响，或需要内网压测时，才考虑在云服务器或独立压测机上运行压测工具。

## 5. 启动 Kubernetes 采集

在 ECS 云服务器开一个单独 SSH 窗口，执行：

```bash
cd ~/life-service
DURATION_SECONDS=1200 INTERVAL_SECONDS=15 bash scripts/collect-hpa-experiment.sh
```

参数说明：

- `DURATION_SECONDS=1200` 表示采集 20 分钟。
- `INTERVAL_SECONDS=15` 表示每 15 秒采样一次。
- 默认观测 `life-assistant-merchant-service` 和 `life-assistant-api-gateway`。

如果只做 10 分钟压测，但希望观察缩容，采集时间建议设置为 15 到 20 分钟。原因是 HPA 缩容通常有稳定窗口，例如 `scaleDown.stabilizationWindowSeconds: 300`，即压力结束后至少还需要等待约 5 分钟才明显缩容。

如果只关心扩容，采集 8 到 12 分钟通常够用；如果要完整记录扩容和缩容，建议 15 到 25 分钟。

采集脚本结束或被 `Ctrl+C` 中断时，会自动输出目录和压缩包：

```text
HPA observation files: reports/perf/hpa-observe-YYYYMMDD-HHMMSS
Archive: reports/perf/hpa-observe-YYYYMMDD-HHMMSS.tgz
```

## 6. Windows 本机运行 JMeter 压测

在 Windows PowerShell 执行：

```powershell
cd E:\Develop\IDEA\IdeaProject\new

powershell -ExecutionPolicy Bypass -File load-tests\run-hpa-jmeter.ps1 `
  -JMeterJar "E:\Develop\apache-jmeter-5.6.3\bin\ApacheJMeter.jar" `
  -HostName "47.120.37.61" `
  -Port 30081 `
  -Users 100 `
  -Ramp 120 `
  -Duration 600 `
  -Think 50 `
  -Keyword "Braised"
```

注意：

- `-HostName` 只能填写真实 IP 或域名，例如 `47.120.37.61`。
- 不要填写 `你的ECS公网IP或域名` 这类占位符。
- 不要在 `-HostName` 中写 `http://`，协议由 `-Protocol` 控制。
- `-Port` 要填写网关暴露的端口，本项目公网 NodePort 是 `30081`。

脚本会生成：

```text
reports/perf/hpa-YYYYMMDD-HHMMSS/samples.jtl
reports/perf/hpa-YYYYMMDD-HHMMSS/html/index.html
```

其中 HTML 报告用于查看吞吐量、平均响应时间、P95 响应时间和错误率。`samples.jtl` 可能很大，不建议提交到 Git 仓库。

## 7. 什么是爬坡

JMeter 的 `Ramp` 是爬坡时间，表示虚拟用户不是瞬间全部启动，而是在指定时间内逐步启动。

例如：

```text
Users=100, Ramp=120
```

含义是 100 个虚拟用户在 120 秒内逐步启动完成。这样可以观察系统从低负载到高负载的过程，也能减少瞬间冲击导致的非实验性失败。

## 8. 实验过程中的观察命令

在 ECS 上可以辅助观察：

```bash
kubectl get hpa -w
kubectl get pods -w
kubectl top pods
kubectl describe hpa life-assistant-merchant-service
kubectl describe hpa life-assistant-api-gateway
```

更规范的方式是优先使用 `scripts/collect-hpa-experiment.sh` 采集 CSV 和文本文件，命令行观察只作为实时辅助。

## 9. 取回云服务器采集结果

在 Windows PowerShell 执行：

```powershell
scp -i E:\Develop\pem\buaa-summer.pem `
  root@47.120.37.61:/root/life-service/reports/perf/hpa-observe-YYYYMMDD-HHMMSS.tgz `
  E:\Develop\IDEA\IdeaProject\new\reports\perf\
```

解压：

```powershell
tar -xzf reports\perf\hpa-observe-YYYYMMDD-HHMMSS.tgz -C .
```

## 10. 结果整理建议

最终报告建议至少包含：

- 实验时间、集群环境、目标服务、HPA 参数。
- JMeter 线程数、爬坡时间、持续时间、请求路径。
- 吞吐量、平均响应时间、P95 响应时间、错误率。
- 压测前、压测中、压测后的 Pod 副本数变化。
- HPA `currentReplicas`、`desiredReplicas`、CPU utilization 的变化。
- 是否达到“升压扩容、降压缩容”的预期。
- 异常情况说明，例如错误率偏高、数据库瓶颈、节点内存压力高、缩容延迟等。

可使用的数据文件：

| 文件 | 用途 |
| --- | --- |
| `samples.jtl` | JMeter 原始采样数据，不建议提交 |
| `html/index.html` | JMeter 可视化性能报告 |
| `hpa.csv` | HPA 当前副本、期望副本、CPU 指标 |
| `deployments.csv` | Deployment 副本数变化 |
| `pods-top.csv` | Pod CPU 和内存变化 |
| `hpa-describe-end.txt` | HPA 事件和最终状态 |
| `pods-start.txt`、`pods-end.txt` | 压测前后 Pod 对比 |

## 11. 常见问题

### 11.1 JMeter 全部 UnknownHostException

通常是 `-HostName` 填成了占位符或完整 URL。应填写：

```powershell
-HostName "47.120.37.61" -Port 30081
```

### 11.2 报告乱码

优先使用仓库中的 `run-hpa-jmeter.ps1`，它已经给 Java 和 JMeter 增加了 UTF-8 参数。

如果仍乱码，检查 PowerShell 终端编码，并优先查看 JMeter HTML 报告中的数值指标。

### 11.3 采集脚本一直运行无输出

当前版本采集脚本每个采样周期会输出一行摘要。如果长时间无输出，检查：

```bash
kubectl get hpa
kubectl top pods
```

如果脚本正在运行但压测已结束，可以根据是否还要观察缩容决定是否 `Ctrl+C`。如果要记录缩容，建议等压力结束后至少再采集 5 到 8 分钟。

### 11.4 Push 被 GitHub 拒绝大文件

`samples.jtl` 可能超过 100MB，不应提交。处理方式：

```powershell
git rm --cached reports/perf/**/samples.jtl
git commit --amend --no-edit
git push --force-with-lease
```

如果大文件已经进入更早的提交，需要用历史清理工具处理。后续保持 `.gitignore` 忽略 `*.jtl`。

## 12. 风险控制

- 不要一开始就把线程数设置过高，建议从 50 或 100 开始。
- 观察错误率，如果大量 5xx 或连接超时，应降低压力或停止实验。
- 压测可能让服务短暂不可用，建议在非生产时间执行。
- 单节点集群资源有限，内存使用率较高时要特别注意 MySQL、Nacos、Redis 等基础服务是否稳定。
