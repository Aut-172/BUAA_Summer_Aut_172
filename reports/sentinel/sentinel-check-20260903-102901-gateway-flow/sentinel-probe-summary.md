# Sentinel Governance Probe Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-03 10:29:16 +0800 |
| Gateway URL | http://47.120.37.61:30081 |
| Nacos URL | http://127.0.0.1:8848 |
| Group | DEFAULT_GROUP |
| Namespace | public |
| Mode | gateway-flow |
| Temporary Rules Applied | true |
| Original Rules Restored | true |
| Total Probes | 12 |

## Scenario Metrics

| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| gateway-flow | 12 | 3 | 3 | 0 | 0 | 0 | 60.38 |

## Probe Results

| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| 2026-09-03 10:29:09.953 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 31.08 | - | success | - |
| 2026-09-03 10:29:10.677 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 120.02 | - | success | - |
| 2026-09-03 10:29:11.491 +0800 | gateway-flow | gateway-search-api | 429 | 429 | 193.4 | - | 系统繁忙，请稍后重试 | - |
| 2026-09-03 10:29:12.020 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 29.75 | - | success | - |
| 2026-09-03 10:29:12.575 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 45.65 | - | success | - |
| 2026-09-03 10:29:13.087 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 36.18 | - | success | - |
| 2026-09-03 10:29:13.705 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 122.36 | - | success | - |
| 2026-09-03 10:29:14.211 +0800 | gateway-flow | gateway-search-api | 429 | 429 | 5.22 | - | 系统繁忙，请稍后重试 | - |
| 2026-09-03 10:29:14.709 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 27.82 | - | success | - |
| 2026-09-03 10:29:15.258 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 38.01 | - | success | - |
| 2026-09-03 10:29:15.769 +0800 | gateway-flow | gateway-search-api | 429 | 429 | 4.34 | - | 系统繁忙，请稍后重试 | - |
| 2026-09-03 10:29:16.349 +0800 | gateway-flow | gateway-search-api | 200 | 200 | 70.78 | - | success | - |

## Expected Signals

- baseline: HTTP 2xx and business code 200.
- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.
- service-flow: at least one HTTP 429 or business 429 with business service block message.
- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.

## Output Files

- CSV: /root/life-service/reports/sentinel/sentinel-check-20260903-102901-gateway-flow/sentinel-probe-results.csv
- JSONL: /root/life-service/reports/sentinel/sentinel-check-20260903-102901-gateway-flow/sentinel-probe-results.jsonl
- Original Nacos configs: /root/life-service/reports/sentinel/sentinel-check-20260903-102901-gateway-flow/original-nacos-configs
