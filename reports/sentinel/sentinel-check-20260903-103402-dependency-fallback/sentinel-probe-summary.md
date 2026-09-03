# Sentinel Governance Probe Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-03 10:34:36 +0800 |
| Gateway URL | http://47.120.37.61:30081 |
| Nacos URL | http://127.0.0.1:8848 |
| Group | DEFAULT_GROUP |
| Namespace | public |
| Mode | dependency-fallback |
| Temporary Rules Applied | false |
| Original Rules Restored | true |
| Total Probes | 11 |

## Scenario Metrics

| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| dependency-fallback | 11 | 0 | 0 | 0 | 0 | 10 | 265.0 |

## Probe Results

| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| 2026-09-03 10:34:05.227 +0800 | dependency-fallback | merchant-login | 200 | 200 | 2665.38 | - | success | - |
| 2026-09-03 10:34:05.610 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 28.92 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:08.996 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 26.18 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:12.402 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 34.95 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:15.821 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 24.31 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:19.288 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 43.49 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:22.655 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 19.1 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:25.994 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 17.47 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:29.331 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 17.23 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:32.671 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 18.63 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |
| 2026-09-03 10:34:36.068 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 19.36 | true | 订单服务暂不可用，已返回临时看板数据，请稍后刷新。 | - |

## Expected Signals

- baseline: HTTP 2xx and business code 200.
- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.
- service-flow: at least one HTTP 429 or business 429 with business service block message.
- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.

## Output Files

- CSV: /root/life-service/reports/sentinel/sentinel-check-20260903-103402-dependency-fallback/sentinel-probe-results.csv
- JSONL: /root/life-service/reports/sentinel/sentinel-check-20260903-103402-dependency-fallback/sentinel-probe-results.jsonl
- Original Nacos configs: /root/life-service/reports/sentinel/sentinel-check-20260903-103402-dependency-fallback/original-nacos-configs
