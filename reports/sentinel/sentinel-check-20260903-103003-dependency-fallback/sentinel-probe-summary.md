# Sentinel Governance Probe Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-03 10:30:34 +0800 |
| Gateway URL | http://47.120.37.61:30081 |
| Nacos URL | http://127.0.0.1:8848 |
| Group | DEFAULT_GROUP |
| Namespace | public |
| Mode | dependency-fallback |
| Temporary Rules Applied | true |
| Original Rules Restored | true |
| Total Probes | 13 |

## Scenario Metrics

| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| dependency-fallback | 13 | 0 | 4 | 0 | 0 | 0 | 1211.49 |

## Probe Results

| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| 2026-09-03 10:30:15.497 +0800 | dependency-fallback | merchant-login | 200 | 200 | 2749.62 | - | success | - |
| 2026-09-03 10:30:20.703 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 4604.28 | false | success | - |
| 2026-09-03 10:30:21.579 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 193.92 | false | success | - |
| 2026-09-03 10:30:24.898 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 2765.0 | false | success | - |
| 2026-09-03 10:30:27.514 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 2110.9 | false | success | - |
| 2026-09-03 10:30:28.063 +0800 | dependency-fallback | merchant-dashboard | 200 | 429 | 130.59 | - | 依赖服务请求过多，请稍后重试 | - |
| 2026-09-03 10:30:31.235 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 2745.38 | false | success | - |
| 2026-09-03 10:30:31.817 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 120.35 | false | success | - |
| 2026-09-03 10:30:32.392 +0800 | dependency-fallback | merchant-dashboard | 200 | 429 | 112.94 | - | 依赖服务请求过多，请稍后重试 | - |
| 2026-09-03 10:30:32.917 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 76.89 | false | success | - |
| 2026-09-03 10:30:33.378 +0800 | dependency-fallback | merchant-dashboard | 200 | 429 | 20.87 | - | 依赖服务请求过多，请稍后重试 | - |
| 2026-09-03 10:30:33.891 +0800 | dependency-fallback | merchant-dashboard | 200 | 200 | 88.22 | false | success | - |
| 2026-09-03 10:30:34.359 +0800 | dependency-fallback | merchant-dashboard | 200 | 429 | 30.46 | - | 依赖服务请求过多，请稍后重试 | - |

## Expected Signals

- baseline: HTTP 2xx and business code 200.
- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.
- service-flow: at least one HTTP 429 or business 429 with business service block message.
- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.

## Output Files

- CSV: /root/life-service/reports/sentinel/sentinel-check-20260903-103003-dependency-fallback/sentinel-probe-results.csv
- JSONL: /root/life-service/reports/sentinel/sentinel-check-20260903-103003-dependency-fallback/sentinel-probe-results.jsonl
- Original Nacos configs: /root/life-service/reports/sentinel/sentinel-check-20260903-103003-dependency-fallback/original-nacos-configs
