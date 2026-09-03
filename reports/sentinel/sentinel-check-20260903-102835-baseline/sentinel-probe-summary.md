# Sentinel Governance Probe Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-03 10:28:50 +0800 |
| Gateway URL | http://47.120.37.61:30081 |
| Nacos URL | http://127.0.0.1:8848 |
| Group | DEFAULT_GROUP |
| Namespace | public |
| Mode | baseline |
| Temporary Rules Applied | false |
| Original Rules Restored | true |
| Total Probes | 24 |

## Scenario Metrics

| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 24 | 0 | 0 | 0 | 0 | 0 | 169.66 |

## Probe Results

| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| 2026-09-03 10:28:36.071 +0800 | baseline | captcha | 200 | 200 | 601.81 | - | success | - |
| 2026-09-03 10:28:36.502 +0800 | baseline | captcha | 200 | 200 | 23.72 | - | success | - |
| 2026-09-03 10:28:36.918 +0800 | baseline | captcha | 200 | 200 | 24.13 | - | success | - |
| 2026-09-03 10:28:37.339 +0800 | baseline | captcha | 200 | 200 | 22.53 | - | success | - |
| 2026-09-03 10:28:37.759 +0800 | baseline | captcha | 200 | 200 | 19.83 | - | success | - |
| 2026-09-03 10:28:38.447 +0800 | baseline | captcha | 200 | 200 | 279.21 | - | success | - |
| 2026-09-03 10:28:38.903 +0800 | baseline | captcha | 200 | 200 | 21.53 | - | success | - |
| 2026-09-03 10:28:39.360 +0800 | baseline | captcha | 200 | 200 | 19.97 | - | success | - |
| 2026-09-03 10:28:39.781 +0800 | baseline | captcha | 200 | 200 | 18.84 | - | success | - |
| 2026-09-03 10:28:40.213 +0800 | baseline | captcha | 200 | 200 | 17.23 | - | success | - |
| 2026-09-03 10:28:40.625 +0800 | baseline | captcha | 200 | 200 | 18.45 | - | success | - |
| 2026-09-03 10:28:41.092 +0800 | baseline | captcha | 200 | 200 | 20.88 | - | success | - |
| 2026-09-03 10:28:42.151 +0800 | baseline | search | 200 | 200 | 729.16 | - | success | - |
| 2026-09-03 10:28:43.313 +0800 | baseline | search | 200 | 200 | 660.38 | - | success | - |
| 2026-09-03 10:28:44.112 +0800 | baseline | search | 200 | 200 | 297.08 | - | success | - |
| 2026-09-03 10:28:44.812 +0800 | baseline | search | 200 | 200 | 172.92 | - | success | - |
| 2026-09-03 10:28:45.626 +0800 | baseline | search | 200 | 200 | 331.27 | - | success | - |
| 2026-09-03 10:28:46.362 +0800 | baseline | search | 200 | 200 | 134.69 | - | success | - |
| 2026-09-03 10:28:46.971 +0800 | baseline | search | 200 | 200 | 127.0 | - | success | - |
| 2026-09-03 10:28:47.525 +0800 | baseline | search | 200 | 200 | 59.31 | - | success | - |
| 2026-09-03 10:28:48.107 +0800 | baseline | search | 200 | 200 | 132.55 | - | success | - |
| 2026-09-03 10:28:48.717 +0800 | baseline | search | 200 | 200 | 107.7 | - | success | - |
| 2026-09-03 10:28:49.316 +0800 | baseline | search | 200 | 200 | 127.48 | - | success | - |
| 2026-09-03 10:28:49.907 +0800 | baseline | search | 200 | 200 | 104.11 | - | success | - |

## Expected Signals

- baseline: HTTP 2xx and business code 200.
- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.
- service-flow: at least one HTTP 429 or business 429 with business service block message.
- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.

## Output Files

- CSV: /root/life-service/reports/sentinel/sentinel-check-20260903-102835-baseline/sentinel-probe-results.csv
- JSONL: /root/life-service/reports/sentinel/sentinel-check-20260903-102835-baseline/sentinel-probe-results.jsonl
- Original Nacos configs: /root/life-service/reports/sentinel/sentinel-check-20260903-102835-baseline/original-nacos-configs
