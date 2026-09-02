# Sentinel 治理实验设计说明书

本文用于在没有云端 Sentinel Dashboard 的情况下，验证 Life Assistant 微服务系统的 Sentinel 入口限流、业务服务限流、内部依赖保护和业务降级效果。实验以 Nacos Config 中的规则为准，通过脚本临时调低规则阈值、发起探测、采集结果、恢复原规则。严格按时间和窗口划分的复现实操步骤见 [Sentinel 实验复现 Runbook](sentinel-experiment-runbook.md)。

## 1. 实验目标

本轮 Sentinel 实验不追求压垮系统，而是验证治理点是否真实生效：

- Gateway 入口限流是否能在请求进入下游前返回统一 `429`。
- 业务服务 URL 限流是否能在绕过或穿过 Gateway 后仍保护服务实例。
- 内部 Feign 依赖被 Sentinel block 时，上游业务是否能按预期快速失败或返回明确降级。
- 商家看板这种读接口在订单依赖异常时是否继续返回 `code=200` 且 `data.degraded=true`。
- Nacos 规则发布和恢复是否可重复，避免依赖 Dashboard 手工点选。

## 2. 前置条件

| 项目 | 要求 |
| --- | --- |
| 代码分支 | `codex/sentinel-integration` 或已合并该分支的 `main`。 |
| 云端入口 | Gateway NodePort 默认 `http://47.120.37.61:30081`。 |
| Nacos 访问 | 推荐用 `kubectl port-forward -n life-assistant svc/nacos 8848:8848` 暂时转发到本机或云服务器本地。 |
| Nacos group | `DEFAULT_GROUP`。 |
| Nacos namespace | 空，使用 public namespace。 |
| Nacos 鉴权 | 当前未开启。 |
| 验证码 | 实验环境建议临时关闭，便于脚本自动登录商家账号。 |
| 商家 token | `dependency-fallback` 场景需要商家登录 token；关闭验证码后脚本可用默认账号自动登录。 |

如果走 GitHub Actions 正常部署，`configs/nacos/` 会自动发布到 Nacos；如果是本地或临时环境，需要先手工发布一次规则。

端口转发说明：当前 `k8s/nacos.yaml` 中 Nacos Service 是集群内访问的 Service，正常情况下不会把 8848 暴露到公网，也不会常驻 `kubectl port-forward`。业务 Pod 通过集群 DNS 访问 Nacos，不需要端口转发。实验脚本运行在 Windows 本机或云服务器普通 shell 中，需要临时调用 Nacos HTTP API 备份、发布和恢复规则，所以才要开端口转发。GitHub Actions 部署时也会在 ECS 上临时打开 port-forward 发布配置，发布完成后关闭；这只影响配置发布流程，不是应用运行时依赖。

## 3. 实验矩阵

| 编号 | 场景 | 临时规则 | 探测接口 | 成功判定 |
| --- | --- | --- | --- | --- |
| S0 | 基线探测 | 不改规则 | `GET /api/captcha`、`GET /api/search` | HTTP 2xx，业务 `code=200`。 |
| S1 | Gateway 入口限流 | 将 `gateway-search-api` 阈值临时降到 1 QPS | 连续请求 `GET /api/search?keyword=Braised` | 至少出现一次 HTTP `429` 或业务 `code=429`，响应消息偏 Gateway block。 |
| S2 | 业务服务 URL 限流 | 将 Gateway 搜索阈值调高，将 `merchant-service` 的 `/api/search` 降到 1 QPS | 连续请求 `GET /api/search?keyword=Braised` | 至少出现一次 `429`，响应消息应为业务服务统一限流提示 `请求过于频繁，请稍后重试`。 |
| S3 | 内部依赖保护与业务降级 | 将 Gateway/merchant 看板阈值调高，将 `order-service` 的 `/internal/orders/merchant-dashboard` 降到 1 QPS | 连续请求 `GET /api/merchant/dashboard` | 至少出现一次 `code=200` 且 `data.degraded=true`，说明内部依赖 block 被商家看板降级接管。 |
| S4 | 依赖服务故障降级 | 复用 `order-service` 缩容到 0 的故障注入脚本 | `GET /api/merchant/dashboard`、旁路接口 | 故障期看板全部或多数 `degraded=true`，旁路接口继续可用。 |
| S5 | 恢复验证 | 恢复原 Nacos 规则和 Deployment | 重复 S0/S3 少量请求 | 不再持续出现 `429` 或 `degraded=true`。 |

S1 和 S2 区分的是保护发生的位置：S1 在 Gateway 直接拦截，S2 请求进入下游后由业务服务拦截。S3 验证的是“依赖被保护以后，上游读接口能否给用户一个可理解的降级结果”。

## 4. 安全边界

- 实验脚本默认不修改 Nacos；只有显式传入 `-ApplyTemporaryRules` 才会发布低阈值规则。
- 启用 `-ApplyTemporaryRules` 时，脚本会先把原始 Nacos Data ID 备份到本次输出目录，然后在 `finally` 中恢复。
- 默认 `-RestoreOriginalRules $true`，不建议改成 `$false`，除非正在专门观察低阈值规则。
- S1/S2/S3 都只调低单个资源，影响面小；仍建议在非高峰期运行。
- S4 会缩容 `order-service`，会影响订单相关链路，应放在最后执行，并确认恢复脚本可用。
- 不对下单、支付、库存预留、优惠券确认等写链路设计“成功降级”，这些链路触发 Sentinel 时应快速失败。

## 5. 执行步骤

### 5.1 确认集群和入口

在云服务器确认 Pod、Service 和 NodePort：

```bash
kubectl get pods -n life-assistant -o wide
kubectl get svc -n life-assistant
curl -fsS http://47.120.37.61:30081/actuator/health
curl -fsS http://47.120.37.61:30081/api/captcha
```

预期所有业务 Pod、Gateway、Nacos、Redis、MySQL 均为 Running，Gateway health 返回 `UP`，验证码接口返回业务 JSON。

### 5.2 临时关闭验证码

实验环境推荐关闭验证码，让探测脚本可以直接使用 `merchant1 / 123456` 自动登录：

```bash
kubectl patch configmap life-assistant-config \
  -n life-assistant \
  --type merge \
  -p '{"data":{"app-auth-captcha-enabled":"false"}}'

kubectl rollout restart -n life-assistant deployment/life-assistant-user-service
kubectl rollout restart -n life-assistant deployment/life-assistant-merchant-service
kubectl rollout restart -n life-assistant deployment/life-assistant-fulfillment-service
kubectl rollout status -n life-assistant deployment/life-assistant-user-service --timeout=240s
kubectl rollout status -n life-assistant deployment/life-assistant-merchant-service --timeout=240s
kubectl rollout status -n life-assistant deployment/life-assistant-fulfillment-service --timeout=240s
```

只跑商家看板实验时，最少需要重启 `life-assistant-merchant-service`；为了多角色登录链路一致，建议三个鉴权相关服务一起重启。

### 5.3 连接 Nacos

云服务器或本机执行端口转发：

```bash
kubectl port-forward -n life-assistant svc/nacos 8848:8848
```

保持该窗口不关闭。另一个终端验证 Nacos 可访问：

```powershell
curl.exe http://127.0.0.1:8848/nacos/actuator/health
```

如果在云服务器上运行实验脚本，就在云服务器上开这个端口转发；如果在 Windows 本机运行实验脚本，则需要 Windows 本机的 `kubectl` 已连接到该 k3s 集群，并在 Windows 本机开端口转发。

当前推荐云服务器执行模式：Windows 没有 kubeconfig 且云服务器没有 PowerShell 时，使用 `load-tests/run-sentinel-governance-check.sh` 在云服务器运行 S0-S3。此时 Nacos 端口转发和 Sentinel 探测脚本都在云服务器，`127.0.0.1:8848` 指向同一台机器。

### 5.4 基线探测

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-sentinel-governance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -NacosUrl "http://127.0.0.1:8848" `
  -Mode baseline
```

### 5.5 Gateway 入口限流

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-sentinel-governance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -NacosUrl "http://127.0.0.1:8848" `
  -Mode gateway-flow `
  -ApplyTemporaryRules `
  -Iterations 12 `
  -IntervalMilliseconds 80
```

预期输出摘要中 `gateway-flow` 的 HTTP `429` 或业务 `429` 数量大于 0。

### 5.6 业务服务 URL 限流

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-sentinel-governance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -NacosUrl "http://127.0.0.1:8848" `
  -Mode service-flow `
  -ApplyTemporaryRules `
  -Iterations 12 `
  -IntervalMilliseconds 80
```

预期出现 `429`，且响应消息更接近业务服务统一提示 `请求过于频繁，请稍后重试`。

### 5.7 内部依赖保护与商家看板降级

如果验证码开启，建议先通过前端或接口拿到商家 token，然后传入 `-MerchantToken`：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-sentinel-governance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -NacosUrl "http://127.0.0.1:8848" `
  -Mode dependency-fallback `
  -ApplyTemporaryRules `
  -MerchantToken "你的商家访问令牌" `
  -Iterations 12 `
  -IntervalMilliseconds 80
```

如果实验环境临时关闭了验证码，也可以让脚本自己登录：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-sentinel-governance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -NacosUrl "http://127.0.0.1:8848" `
  -Mode dependency-fallback `
  -ApplyTemporaryRules `
  -MerchantUsername "merchant1" `
  -MerchantPassword "123456"
```

预期至少一个 `merchant-dashboard` 样本满足：HTTP 2xx、业务 `code=200`、`data.degraded=true`。

### 5.8 一次性运行 S0-S3

确认环境空闲后，可以一次跑完非缩容类实验：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-sentinel-governance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -NacosUrl "http://127.0.0.1:8848" `
  -Mode all `
  -ApplyTemporaryRules `
  -MerchantToken "你的商家访问令牌" `
  -Iterations 12 `
  -IntervalMilliseconds 80
```

### 5.9 依赖服务故障降级

S4 继续复用既有容错实验说明书：

```bash
bash scripts/fault-inject-dependency.sh
```

Windows 本机执行：

```powershell
powershell -ExecutionPolicy Bypass -File load-tests\run-fault-tolerance-check.ps1 `
  -GatewayUrl "http://47.120.37.61:30081" `
  -Phase fault `
  -MerchantToken "你的商家访问令牌" `
  -Iterations 10 `
  -IntervalSeconds 3
```

恢复：

```bash
bash scripts/fault-restore-dependency.sh
```

### 5.10 恢复验证码

实验结束后如需恢复验证码：

```bash
kubectl patch configmap life-assistant-config \
  -n life-assistant \
  --type merge \
  -p '{"data":{"app-auth-captcha-enabled":"true"}}'

kubectl rollout restart -n life-assistant deployment/life-assistant-user-service
kubectl rollout restart -n life-assistant deployment/life-assistant-merchant-service
kubectl rollout restart -n life-assistant deployment/life-assistant-fulfillment-service
```

如果只是课程实验环境，也可以保持关闭，后续登录脚本和自动化测试会更稳定。

## 6. 输出文件

`run-sentinel-governance-check.ps1` 默认输出到：

```text
reports/sentinel/sentinel-check-YYYYMMDD-HHMMSS-<mode>/
```

包含：

| 文件 | 用途 |
| --- | --- |
| `sentinel-probe-results.csv` | 每次探测的 HTTP 状态、业务 code、耗时、降级标记。 |
| `sentinel-probe-results.json` | 完整响应体，便于排查。 |
| `sentinel-probe-summary.md` | 自动生成的摘要和场景统计。 |
| `original-nacos-configs/` | 实验前备份的 Nacos 原始规则。 |

建议提交 Markdown 和 CSV 作为实验报告证据；JSON 可按体积和敏感性决定是否提交。

## 7. 判定标准

| 能力点 | 通过标准 |
| --- | --- |
| Gateway 入口限流 | S1 出现 `429`，且业务服务日志不应出现同等数量的实际处理。 |
| 业务服务限流 | S2 出现 `429`，响应消息为业务服务 block 语义。 |
| 内部依赖保护 | S3 中订单内部接口被限流后，商家看板不暴露 500。 |
| 业务降级 | S3/S4 中商家看板返回 `code=200` 和 `data.degraded=true`。 |
| 规则恢复 | 实验结束后再次运行 S0，不应持续出现 `429`。 |
| 故障隔离 | S4 故障期旁路接口继续可用，核心 Pod 不重启。 |

## 8. 后续扩展

- 为骑手看板、推荐、评价列表、消息会话列表等读接口补业务语义降级后，可以按 S3 的方式新增场景。
- 如果后续部署 Sentinel Dashboard，可把 Dashboard 截图作为辅助证据，但规则仍以 Nacos Config 为准。
- 如果引入 Prometheus/Grafana，可以把 `429` 次数、接口耗时和 Pod CPU 加到报告里。
- 如果引入 Chaos Mesh，可以用标准 Chaos CRD 替换当前缩容脚本，实验步骤和判定标准保持不变。
