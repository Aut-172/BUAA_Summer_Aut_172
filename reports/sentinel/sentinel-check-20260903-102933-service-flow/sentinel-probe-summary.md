# Sentinel Governance Probe Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-03 10:29:51 +0800 |
| Gateway URL | http://47.120.37.61:30081 |
| Nacos URL | http://127.0.0.1:8848 |
| Group | DEFAULT_GROUP |
| Namespace | public |
| Mode | service-flow |
| Temporary Rules Applied | true |
| Original Rules Restored | true |
| Total Probes | 12 |

## Scenario Metrics

| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| service-flow | 12 | 2 | 2 | 0 | 0 | 0 | 320.66 |

## Probe Results

| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| 2026-09-03 10:29:45.315 +0800 | service-flow | merchant-service-search | 200 | 200 | 3392.07 | - | success | - |
| 2026-09-03 10:29:45.961 +0800 | service-flow | merchant-service-search | 200 | 200 | 54.69 | - | success | - |
| 2026-09-03 10:29:46.607 +0800 | service-flow | merchant-service-search | 200 | 200 | 52.74 | - | success | - |
| 2026-09-03 10:29:47.149 +0800 | service-flow | merchant-service-search | 200 | 200 | 66.97 | - | success | - |
| 2026-09-03 10:29:47.692 +0800 | service-flow | merchant-service-search | 200 | 200 | 33.59 | - | success | - |
| 2026-09-03 10:29:48.194 +0800 | service-flow | merchant-service-search | 200 | 200 | 24.12 | - | success | - |
| 2026-09-03 10:29:48.689 +0800 | service-flow | merchant-service-search | 200 | 200 | 28.3 | - | success | - |
| 2026-09-03 10:29:49.233 +0800 | service-flow | merchant-service-search | 429 | 429 | 49.71 | - | 请求过于频繁，请稍后重试 | - |
| 2026-09-03 10:29:49.731 +0800 | service-flow | merchant-service-search | 200 | 200 | 38.0 | - | success | - |
| 2026-09-03 10:29:50.274 +0800 | service-flow | merchant-service-search | 429 | 429 | 34.44 | - | 请求过于频繁，请稍后重试 | - |
| 2026-09-03 10:29:50.791 +0800 | service-flow | merchant-service-search | 200 | 200 | 29.48 | - | success | - |
| 2026-09-03 10:29:51.335 +0800 | service-flow | merchant-service-search | 200 | 200 | 43.84 | - | success | - |

## Expected Signals

- baseline: HTTP 2xx and business code 200.
- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.
- service-flow: at least one HTTP 429 or business 429 with business service block message.
- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.

## Output Files

- CSV: /root/life-service/reports/sentinel/sentinel-check-20260903-102933-service-flow/sentinel-probe-results.csv
- JSONL: /root/life-service/reports/sentinel/sentinel-check-20260903-102933-service-flow/sentinel-probe-results.jsonl
- Original Nacos configs: /root/life-service/reports/sentinel/sentinel-check-20260903-102933-service-flow/original-nacos-configs
