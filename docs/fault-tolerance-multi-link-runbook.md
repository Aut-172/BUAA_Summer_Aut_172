# 多链路故障容错实验 Runbook

本文用于复现升级后的 fault 实验。当前实验仍以 `order-service` 故障为核心，但探测范围从单一商家看板扩展为多条链路：商家看板降级、订单直连链路失败快返、依赖订单服务的结算/履约/消息链路失败快返，以及与订单服务无关的旁路链路持续可用。

## 0. 窗口分工

| 窗口 | 位置 | 用途 |
| --- | --- | --- |
| A | 云服务器 SSH | 采集 Kubernetes 状态。 |
| B | 云服务器 SSH | 注入和恢复 `order-service` 故障。 |
| D | 云服务器 SSH | 运行多链路 fault 探测脚本。 |
| E | Windows PowerShell | 可选，取回报告。 |

## 1. 云服务器 D 窗口：确认文件

```bash
cd ~/life-service
ls load-tests/run-fault-tolerance-check.sh
ls scripts/fault-inject-dependency.sh
ls scripts/fault-restore-dependency.sh
ls scripts/collect-fault-experiment.sh
```

## 2. 云服务器 D 窗口：确认依赖

```bash
command -v bash
command -v curl
command -v python3
command -v kubectl
```

四条命令都要有输出。

## 3. 云服务器 D 窗口：检查脚本语法

```bash
cd ~/life-service
bash -n load-tests/run-fault-tolerance-check.sh
bash -n scripts/fault-inject-dependency.sh
bash -n scripts/fault-restore-dependency.sh
bash -n scripts/collect-fault-experiment.sh
```

无输出表示 Bash 语法检查通过。

## 4. 云服务器 D 窗口：确认 namespace

```bash
kubectl get deploy -A | grep life-assistant
kubectl get svc -A | grep api-gateway
```

当前云端实验材料显示资源位于 `default` namespace。后续命令默认使用：

```bash
export NS=default
export GATEWAY_URL=http://47.120.37.61:30081
```

如果实际输出显示资源位于其他 namespace，把 `NS=default` 改为实际 namespace。

## 5. 云服务器 D 窗口：确认集群基线状态

```bash
kubectl get pods -n "$NS" -o wide
kubectl get deploy -n "$NS"
kubectl get endpoints -n "$NS" order-service merchant-service user-service api-gateway
```

预期 `order-service`、`merchant-service`、`user-service`、`fulfillment-service`、`settlement-service`、`engagement-service`、`api-gateway` 均存在 Running Pod；`order-service` Endpoint 有可用 IP。

## 6. 云服务器 D 窗口：关闭验证码

```bash
kubectl patch configmap life-assistant-config \
  -n "$NS" \
  --type merge \
  -p '{"data":{"app-auth-captcha-enabled":"false"}}'
```

## 7. 云服务器 D 窗口：重启登录相关服务

```bash
kubectl rollout restart -n "$NS" deployment/life-assistant-user-service
kubectl rollout restart -n "$NS" deployment/life-assistant-merchant-service
kubectl rollout restart -n "$NS" deployment/life-assistant-fulfillment-service
```

## 8. 云服务器 D 窗口：等待登录相关服务重启完成

```bash
kubectl rollout status -n "$NS" deployment/life-assistant-user-service --timeout=240s
kubectl rollout status -n "$NS" deployment/life-assistant-merchant-service --timeout=240s
kubectl rollout status -n "$NS" deployment/life-assistant-fulfillment-service --timeout=240s
```

## 9. 云服务器 D 窗口：确认 Gateway 和公开接口可访问

```bash
curl -fsS "$GATEWAY_URL/actuator/health"
curl -fsS "$GATEWAY_URL/api/captcha"
curl -fsS "$GATEWAY_URL/api/search?keyword=Braised"
```

预期 health 返回 `UP`，业务接口返回 JSON。

## 10. 云服务器 D 窗口：执行多链路基线探测

```bash
cd ~/life-service

bash load-tests/run-fault-tolerance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --phase baseline \
  --iterations 3 \
  --interval-seconds 2 \
  --order-id 70001
```

预期所有探测通过。重点检查：

| 类别 | 预期 |
| --- | --- |
| `dashboard-normal` | 商家看板 HTTP 2xx，业务 `code=200`，未降级 |
| `bypass-success` | 商家资料、搜索、用户资料、骑手资料均成功 |
| `success` | 订单列表、订单详情、支付记录、骑手任务、配送详情、消息订单详情均成功 |

如果本步骤失败，先修复账号、验证码、演示数据或 Gateway 路由，不进入故障注入。

## 11. 云服务器 A 窗口：启动 Kubernetes 状态采集

```bash
cd ~/life-service
export NS=default

NAMESPACE="$NS" \
DURATION_SECONDS=1200 \
INTERVAL_SECONDS=10 \
PROTECTED_DEPLOYMENT=life-assistant-merchant-service \
DEPENDENCY_DEPLOYMENT=life-assistant-order-service \
EXTRA_DEPLOYMENTS="life-assistant-api-gateway life-assistant-user-service life-assistant-fulfillment-service life-assistant-settlement-service life-assistant-engagement-service" \
TARGET_HPAS="life-assistant-merchant-service life-assistant-order-service life-assistant-api-gateway life-assistant-fulfillment-service life-assistant-settlement-service life-assistant-engagement-service life-assistant-user-service" \
TARGET_SERVICES="api-gateway merchant-service order-service user-service fulfillment-service settlement-service engagement-service nacos mysql redis" \
bash scripts/collect-fault-experiment.sh
```

A 窗口保持运行，不输入其他命令。

## 12. 云服务器 B 窗口：注入 `order-service` 故障

```bash
cd ~/life-service
export NS=default

NAMESPACE="$NS" \
TARGET_DEPLOYMENT=life-assistant-order-service \
TARGET_HPA=life-assistant-order-service \
bash scripts/fault-inject-dependency.sh
```

## 13. 云服务器 B 窗口：确认故障注入状态

```bash
kubectl get deploy -n "$NS" life-assistant-order-service
kubectl get endpoints -n "$NS" order-service
kubectl get pods -n "$NS" | grep order || true
kubectl get hpa -n "$NS" life-assistant-order-service || true
```

预期 `life-assistant-order-service` 期望副本数为 0，`order-service` Endpoint 为 `none` 或没有可用地址。

## 14. 云服务器 D 窗口：执行多链路故障期探测

```bash
cd ~/life-service

bash load-tests/run-fault-tolerance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --phase fault \
  --iterations 5 \
  --interval-seconds 3 \
  --order-id 70001
```

预期结果：

| 类别 | 覆盖接口 | 预期 |
| --- | --- | --- |
| `dashboard-degraded` | `GET /api/merchant/dashboard` | HTTP 2xx，业务 `code=200`，`data.degraded=true`，`data.degradedDependency=order-service` |
| `bypass-success` | `GET /api/merchant/profile`、`GET /api/search`、`GET /api/user/profile`、`GET /api/rider/profile` | HTTP 2xx，业务 `code=200` |
| `dependency-unavailable` | `GET /api/orders`、`GET /api/orders/70001`、`GET /api/orders/70001/payments`、`GET /api/rider/tasks`、`GET /api/delivery/70001`、`GET /api/messages/orders/70001` | HTTP `502/503/504` 或业务 `code=503`，失败要快速且可解释 |

本步骤是升级后的核心实验：它同时证明降级链路可用、依赖订单服务的链路失败边界清晰、旁路链路未被拖垮。

## 15. 云服务器 B 窗口：恢复 `order-service`

```bash
cd ~/life-service
export NS=default

NAMESPACE="$NS" bash scripts/fault-restore-dependency.sh
```

## 16. 云服务器 B 窗口：确认恢复状态

```bash
kubectl rollout status -n "$NS" deployment/life-assistant-order-service --timeout=240s
kubectl get deploy -n "$NS" life-assistant-order-service
kubectl get endpoints -n "$NS" order-service
kubectl get pods -n "$NS" | grep order
kubectl get hpa -n "$NS" life-assistant-order-service
```

预期 `order-service` Pod Running，Endpoint 恢复，HPA 恢复。

## 17. 云服务器 D 窗口：执行多链路恢复期探测

```bash
cd ~/life-service

bash load-tests/run-fault-tolerance-check.sh \
  --gateway-url "$GATEWAY_URL" \
  --phase recovery \
  --iterations 3 \
  --interval-seconds 2 \
  --order-id 70001
```

预期所有探测重新通过，商家看板不再降级，订单直连和依赖订单服务的链路恢复成功。

## 18. 云服务器 D 窗口：查看探测输出

```bash
find reports/fault -path '*multi-link*' -name probe-summary.md -print
find reports/fault -path '*multi-link*' -name probe-results.csv -print
```

每次探测会生成：

```text
reports/fault/fault-check-YYYYMMDD-HHMMSS-baseline-multi-link/probe-summary.md
reports/fault/fault-check-YYYYMMDD-HHMMSS-baseline-multi-link/probe-results.csv
reports/fault/fault-check-YYYYMMDD-HHMMSS-fault-multi-link/probe-summary.md
reports/fault/fault-check-YYYYMMDD-HHMMSS-fault-multi-link/probe-results.csv
reports/fault/fault-check-YYYYMMDD-HHMMSS-recovery-multi-link/probe-summary.md
reports/fault/fault-check-YYYYMMDD-HHMMSS-recovery-multi-link/probe-results.csv
```

响应体会保存在各目录的 `bodies/` 下，默认不建议提交到 Git。

## 19. 云服务器 A 窗口：结束采集

按 `Ctrl+C`。采集脚本会自动生成观测目录和压缩包。

输出位置：

```text
~/life-service/reports/fault/fault-observe-YYYYMMDD-HHMMSS/
~/life-service/reports/fault/fault-observe-YYYYMMDD-HHMMSS.tgz
```

## 20. Windows E 窗口：取回结果

```powershell
cd E:\Develop\IDEA\IdeaProject\new

scp -i .\buaa-summer.pem -r `
  root@47.120.37.61:/root/life-service/reports/fault `
  E:\Develop\IDEA\IdeaProject\new\reports\
```

如果 pem 不在项目根目录，把 `.\buaa-summer.pem` 改为实际路径。

## 21. 判定标准

| 阶段 | 通过标准 |
| --- | --- |
| 基线期 | 多链路探测全部通过，商家看板未降级，订单相关链路可访问 |
| 故障注入 | `order-service` Endpoint 消失，Deployment 期望副本为 0 |
| 故障期降级链路 | 商家看板返回 `code=200` 和 `data.degraded=true` |
| 故障期旁路链路 | 商家资料、搜索、用户资料、骑手资料继续返回 `code=200` |
| 故障期依赖链路 | 订单列表、订单详情、支付记录、骑手任务、配送详情、消息订单详情返回 HTTP `502/503/504` 或业务 `code=503` |
| 恢复期 | `order-service` Endpoint 恢复，多链路探测全部通过 |

## 22. 按需恢复验证码

如果实验环境后续仍要自动化测试，可以保持验证码关闭。若要恢复：

```bash
kubectl patch configmap life-assistant-config \
  -n "$NS" \
  --type merge \
  -p '{"data":{"app-auth-captcha-enabled":"true"}}'

kubectl rollout restart -n "$NS" deployment/life-assistant-user-service
kubectl rollout restart -n "$NS" deployment/life-assistant-merchant-service
kubectl rollout restart -n "$NS" deployment/life-assistant-fulfillment-service
```

## 23. 后续扩展方向

本 runbook 当前验证的是 `order-service` 故障。下一轮可以按同样格式增加其他依赖故障：

| 故障目标 | 主要影响链路 | 重点判定 |
| --- | --- | --- |
| `merchant-service` | 下单商品快照、购物车商品快照、履约商家信息、评价商家信息 | 下单失败快返，购物车/评价读接口是否可解释失败 |
| `user-service` | 下单地址、消息用户快照、评价用户快照 | 下单失败快返，消息和评价读接口不拖垮核心服务 |
| `settlement-service` | 下单优惠券锁定、支付记录、支付确认 | 优惠券不可用时订单创建是否回滚或补偿 |
| `fulfillment-service` | 评价骑手快照、消息骑手快照 | 相关读接口失败边界清晰 |
