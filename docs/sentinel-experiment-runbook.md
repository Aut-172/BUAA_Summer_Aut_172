# Sentinel 实验复现 Runbook

本文是严格按时间顺序编排的 Sentinel 实验执行步骤。当前环境假设如下：Windows 本机没有 kubeconfig，云服务器没有 PowerShell，因此 Nacos 端口转发、Sentinel 规则发布、Sentinel 探测脚本全部在云服务器执行；Windows 只用于最后取回报告。

## 0. 窗口分工

| 窗口 | 位置 | 用途 |
| --- | --- | --- |
| A | 云服务器 SSH | 采集 Kubernetes 状态。 |
| B | 云服务器 SSH | 注入和恢复 `order-service` 故障。 |
| C | 云服务器 SSH | Nacos `kubectl port-forward`，保持不关闭。 |
| D | 云服务器 SSH | 运行 Sentinel S0-S3 探测脚本。 |
| E | Windows PowerShell | 可选，取回报告。 |

## 1. 云服务器 D 窗口：确认代码文件

```bash
cd ~/life-service
ls load-tests/run-sentinel-governance-check.sh
ls scripts/publish-nacos-config.sh
```

如果文件不存在，先同步包含 Sentinel 实验脚本的分支或上传这两个脚本。

## 2. 云服务器 D 窗口：确认脚本依赖

```bash
command -v bash
command -v curl
command -v python3
```

三条命令都要有输出。

## 3. 云服务器 D 窗口：检查脚本语法

```bash
cd ~/life-service
bash -n load-tests/run-sentinel-governance-check.sh
bash -n scripts/publish-nacos-config.sh
```

无输出表示语法检查通过。

## 4. 云服务器 D 窗口：确认 namespace

```bash
kubectl get deploy -A | grep life-assistant
kubectl get svc -A | grep nacos
```

下面步骤默认 namespace 是 `life-assistant`。如果实际资源在 `default`，把后续命令里的 `life-assistant` 改为 `default`。

## 5. 云服务器 D 窗口：设置公共变量

```bash
export NS=life-assistant
export GATEWAY_URL=http://47.120.37.61:30081
```

## 6. 云服务器 D 窗口：确认集群状态

```bash
kubectl get pods -n "$NS" -o wide
kubectl get svc -n "$NS"
```

预期 Gateway、六个业务服务、Nacos、MySQL、Redis 都是 Running。

## 7. 云服务器 D 窗口：确认 Gateway 可访问

```bash
curl -fsS "$GATEWAY_URL/actuator/health"
curl -fsS "$GATEWAY_URL/api/captcha"
curl -fsS "$GATEWAY_URL/api/search?keyword=Braised"
```

预期 health 返回 `UP`，业务接口返回 JSON。

## 8. 云服务器 D 窗口：关闭验证码

```bash
kubectl patch configmap life-assistant-config \
  -n "$NS" \
  --type merge \
  -p '{"data":{"app-auth-captcha-enabled":"false"}}'
```

## 9. 云服务器 D 窗口：重启鉴权相关服务

```bash
kubectl rollout restart -n "$NS" deployment/life-assistant-user-service
kubectl rollout restart -n "$NS" deployment/life-assistant-merchant-service
kubectl rollout restart -n "$NS" deployment/life-assistant-fulfillment-service
```

## 10. 云服务器 D 窗口：等待鉴权相关服务重启完成

```bash
kubectl rollout status -n "$NS" deployment/life-assistant-user-service --timeout=240s
kubectl rollout status -n "$NS" deployment/life-assistant-merchant-service --timeout=240s
kubectl rollout status -n "$NS" deployment/life-assistant-fulfillment-service --timeout=240s
```

## 11. 云服务器 C 窗口：开启 Nacos 端口转发

```bash
export NS=life-assistant
kubectl port-forward -n "$NS" svc/nacos 8848:8848
```

C 窗口保持不关闭。端口转发不是常驻服务，只是让云服务器本机的实验脚本能通过 `127.0.0.1:8848` 调用集群内 Nacos。

## 12. 云服务器 D 窗口：确认 Nacos 可访问

```bash
curl -fsS http://127.0.0.1:8848/nacos/actuator/health
```

## 13. 云服务器 A 窗口：启动采集

```bash
cd ~/life-service
export NS=life-assistant

NAMESPACE="$NS" \
DURATION_SECONDS=1200 \
INTERVAL_SECONDS=10 \
PROTECTED_DEPLOYMENT=life-assistant-merchant-service \
DEPENDENCY_DEPLOYMENT=life-assistant-order-service \
EXTRA_DEPLOYMENTS="life-assistant-api-gateway life-assistant-user-service life-assistant-order-service" \
TARGET_HPAS="life-assistant-merchant-service life-assistant-order-service life-assistant-api-gateway" \
TARGET_SERVICES="api-gateway merchant-service order-service user-service nacos" \
bash scripts/collect-fault-experiment.sh
```

A 窗口保持采集，不输入其他命令。

## 14. 云服务器 D 窗口：S0 基线探测

```bash
cd ~/life-service

bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode baseline
```

预期 `captcha`、`search` 正常返回，不持续出现 `429`。

## 15. 云服务器 D 窗口：S1 Gateway 入口限流

```bash
bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode gateway-flow \
  --apply-temporary-rules \
  --iterations 12 \
  --interval-ms 80
```

预期摘要中 `gateway-flow` 至少出现一次 HTTP `429` 或业务 `429`。脚本会在结束前恢复原 Nacos 规则。

## 16. 云服务器 D 窗口：S2 业务服务限流

```bash
bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode service-flow \
  --apply-temporary-rules \
  --iterations 12 \
  --interval-ms 80
```

预期出现 `429`，响应消息更接近 `请求过于频繁，请稍后重试`。脚本会在结束前恢复原 Nacos 规则。

## 17. 云服务器 D 窗口：S3 内部依赖保护与商家看板降级

```bash
bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode dependency-fallback \
  --apply-temporary-rules \
  --merchant-username "merchant1" \
  --merchant-password "123456" \
  --iterations 12 \
  --interval-ms 80
```

预期 `merchant-dashboard` 至少一次返回 `code=200` 且 `data.degraded=true`。脚本会在结束前恢复原 Nacos 规则。

## 18. 云服务器 D 窗口：S0 规则恢复复查

```bash
bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode baseline \
  --iterations 5
```

预期不持续出现 `429`。

## 19. 云服务器 B 窗口：S4 注入依赖故障

```bash
cd ~/life-service
export NS=life-assistant

NAMESPACE="$NS" bash scripts/fault-inject-dependency.sh
```

## 20. 云服务器 B 窗口：确认故障状态

```bash
kubectl get deploy -n "$NS" life-assistant-order-service
kubectl get endpoints -n "$NS" order-service
kubectl get pods -n "$NS" | grep order
```

预期 `order-service` 副本为 0，或 Endpoint 为 `none`。

## 21. 云服务器 D 窗口：S4 故障期看板探测

```bash
bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode dependency-fallback \
  --merchant-username "merchant1" \
  --merchant-password "123456" \
  --iterations 10 \
  --interval-ms 3000
```

预期 `merchant-dashboard` 多数或全部返回 `code=200` 且 `data.degraded=true`。本步骤不传 `--apply-temporary-rules`，因为故障来源是第 19 步缩容 `order-service`，不是临时 Sentinel 低阈值规则。

## 22. 云服务器 D 窗口：S4 故障期旁路接口探测

```bash
MERCHANT_TOKEN=$(curl -fsS \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"username":"merchant1","password":"123456"}' \
  "$GATEWAY_URL/api/auth/merchant/login" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')

curl -fsS "$GATEWAY_URL/api/search?keyword=Braised"
curl -fsS "$GATEWAY_URL/api/merchant/profile" \
  -H "Authorization: Bearer $MERCHANT_TOKEN"
```

预期搜索和商家资料接口仍可用，说明订单服务故障没有拖垮旁路链路。

## 23. 云服务器 B 窗口：恢复依赖服务

```bash
cd ~/life-service
NAMESPACE="$NS" bash scripts/fault-restore-dependency.sh
```

## 24. 云服务器 B 窗口：确认恢复状态

```bash
kubectl rollout status -n "$NS" deployment/life-assistant-order-service --timeout=240s
kubectl get endpoints -n "$NS" order-service
kubectl get pods -n "$NS" | grep order
```

预期 `order-service` Pod Running，Endpoint 恢复。

## 25. 云服务器 D 窗口：恢复期基线复查

```bash
bash load-tests/run-sentinel-governance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --nacos-url "http://127.0.0.1:8848" \
  --mode baseline \
  --iterations 5
```

预期 `captcha`、`search` 正常返回。

## 26. 云服务器 D 窗口：查看 Sentinel 实验输出

```bash
find reports/sentinel -name sentinel-probe-summary.md -print
find reports/sentinel -name sentinel-probe-results.csv -print
```

## 27. 云服务器 A 窗口：结束采集

按 `Ctrl+C`。采集脚本会自动生成目录和压缩包。

输出位置：

```text
~/life-service/reports/fault/fault-observe-YYYYMMDD-HHMMSS/
~/life-service/reports/fault/fault-observe-YYYYMMDD-HHMMSS.tgz
```

## 28. Windows E 窗口：取回报告

```powershell
scp -r root@47.120.37.61:/root/life-service/reports/sentinel E:\Develop\IDEA\IdeaProject\new\reports\
scp root@47.120.37.61:/root/life-service/reports/fault/fault-observe-*.tgz E:\Develop\IDEA\IdeaProject\new\reports\fault\
```

如果 SSH 用户不是 `root`，把命令中的 `root@47.120.37.61` 改为实际用户。

## 29. 云服务器 A 或 B 窗口：按需恢复验证码

如果实验环境后续仍要自动化测试，可以不恢复验证码。若要恢复：

```bash
kubectl patch configmap life-assistant-config \
  -n "$NS" \
  --type merge \
  -p '{"data":{"app-auth-captcha-enabled":"true"}}'

kubectl rollout restart -n "$NS" deployment/life-assistant-user-service
kubectl rollout restart -n "$NS" deployment/life-assistant-merchant-service
kubectl rollout restart -n "$NS" deployment/life-assistant-fulfillment-service
```

## 30. 云服务器 C 窗口：关闭 Nacos 端口转发

按 `Ctrl+C` 结束 `kubectl port-forward`。

## 31. 判定标准

| 场景 | 通过标准 |
| --- | --- |
| S0 基线 | `captcha`、`search` HTTP 2xx，业务 `code=200`。 |
| S1 Gateway 入口限流 | 至少出现一次 HTTP `429` 或业务 `429`。 |
| S2 业务服务限流 | 至少出现一次 `429`，消息接近 `请求过于频繁，请稍后重试`。 |
| S3 内部依赖保护 | 商家看板至少一次 `data.degraded=true`，不暴露 500。 |
| S4 依赖故障 | `order-service` Endpoint 为 `none` 期间，看板返回降级，旁路接口可用。 |
| 恢复复查 | 不持续出现 `429`，`order-service` Endpoint 恢复。 |
