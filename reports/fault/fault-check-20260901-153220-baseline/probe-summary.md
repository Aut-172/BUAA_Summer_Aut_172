# Fault Tolerance Probe Summary

## Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-01 15:32:43 +08:00 |
| Gateway URL | http://47.120.37.61:30081 |
| Phase | baseline |
| Iterations | 5 |
| Total Probes | 16 |
| Passed | 15 |
| Failed | 1 |
| Dashboard Avg Latency ms | 800.95 |
| Dashboard Degraded Count | 1 |

## Probe Results

| Time | Name | HTTP | Code | Latency ms | Degraded | Dependency | Message | Passed |
| --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| 2026-09-01 15:32:23.328 +08:00 | merchant-login | 200 | 200 | 2914.11 | - | - | success | True |
| 2026-09-01 15:32:26.559 +08:00 | merchant-dashboard | 200 | 200 | 3184.1 | True | order-service | è®¢åæå¡æä¸å¯ç¨ï¼å·²è¿åä¸´æ¶çæ¿æ°æ®ï¼è¯·ç¨åå·æ°ã | False |
| 2026-09-01 15:32:26.726 +08:00 | merchant-profile | 200 | 200 | 166.31 | - | - | success | True |
| 2026-09-01 15:32:28.995 +08:00 | merchant-search | 200 | 200 | 2259.16 | - | - | success | True |
| 2026-09-01 15:32:31.284 +08:00 | merchant-dashboard | 200 | 200 | 267.54 | False | - | - | True |
| 2026-09-01 15:32:31.465 +08:00 | merchant-profile | 200 | 200 | 183.91 | - | - | success | True |
| 2026-09-01 15:32:33.093 +08:00 | merchant-search | 200 | 200 | 1625.73 | - | - | success | True |
| 2026-09-01 15:32:35.289 +08:00 | merchant-dashboard | 200 | 200 | 189.23 | False | - | - | True |
| 2026-09-01 15:32:35.434 +08:00 | merchant-profile | 200 | 200 | 143.25 | - | - | success | True |
| 2026-09-01 15:32:37.122 +08:00 | merchant-search | 200 | 200 | 1684.08 | - | - | success | True |
| 2026-09-01 15:32:39.315 +08:00 | merchant-dashboard | 200 | 200 | 185 | False | - | - | True |
| 2026-09-01 15:32:39.450 +08:00 | merchant-profile | 200 | 200 | 133.7 | - | - | success | True |
| 2026-09-01 15:32:40.606 +08:00 | merchant-search | 200 | 200 | 1152.01 | - | - | success | True |
| 2026-09-01 15:32:42.791 +08:00 | merchant-dashboard | 200 | 200 | 178.88 | False | - | - | True |
| 2026-09-01 15:32:42.929 +08:00 | merchant-profile | 200 | 200 | 136.16 | - | - | success | True |
| 2026-09-01 15:32:43.876 +08:00 | merchant-search | 200 | 200 | 951.75 | - | - | success | True |

## Output Files

- CSV: E:\Develop\IDEA\IdeaProject\new\reports\fault\fault-check-20260901-153220-baseline\probe-results.csv
- JSON: E:\Develop\IDEA\IdeaProject\new\reports\fault\fault-check-20260901-153220-baseline\probe-results.json
