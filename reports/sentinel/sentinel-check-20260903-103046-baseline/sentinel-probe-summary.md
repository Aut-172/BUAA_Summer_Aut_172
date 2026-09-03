# Sentinel Governance Probe Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-03 10:30:53 +0800 |
| Gateway URL | http://47.120.37.61:30081 |
| Nacos URL | http://127.0.0.1:8848 |
| Group | DEFAULT_GROUP |
| Namespace | public |
| Mode | baseline |
| Temporary Rules Applied | false |
| Original Rules Restored | true |
| Total Probes | 10 |

## Scenario Metrics

| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| baseline | 10 | 0 | 0 | 0 | 0 | 0 | 136.91 |

## Probe Results

| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |
| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |
| 2026-09-03 10:30:46.473 +0800 | baseline | captcha | 200 | 200 | 18.44 | - | success | - |
| 2026-09-03 10:30:47.027 +0800 | baseline | captcha | 200 | 200 | 38.32 | - | success | - |
| 2026-09-03 10:30:47.592 +0800 | baseline | captcha | 200 | 200 | 48.04 | - | success | - |
| 2026-09-03 10:30:48.131 +0800 | baseline | captcha | 200 | 200 | 47.84 | - | success | - |
| 2026-09-03 10:30:48.698 +0800 | baseline | captcha | 200 | 200 | 18.88 | - | success | - |
| 2026-09-03 10:30:49.186 +0800 | baseline | search | 200 | 200 | 36.44 | - | success | - |
| 2026-09-03 10:30:49.825 +0800 | baseline | search | 200 | 200 | 115.65 | - | success | - |
| 2026-09-03 10:30:51.083 +0800 | baseline | search | 200 | 200 | 691.87 | - | success | - |
| 2026-09-03 10:30:51.730 +0800 | baseline | search | 200 | 200 | 55.85 | - | success | - |
| 2026-09-03 10:30:52.630 +0800 | baseline | search | 200 | 200 | 297.77 | - | success | - |

## Expected Signals

- baseline: HTTP 2xx and business code 200.
- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.
- service-flow: at least one HTTP 429 or business 429 with business service block message.
- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.

## Output Files

- CSV: /root/life-service/reports/sentinel/sentinel-check-20260903-103046-baseline/sentinel-probe-results.csv
- JSONL: /root/life-service/reports/sentinel/sentinel-check-20260903-103046-baseline/sentinel-probe-results.jsonl
- Original Nacos configs: /root/life-service/reports/sentinel/sentinel-check-20260903-103046-baseline/original-nacos-configs
