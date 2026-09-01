# Fault Tolerance Probe Summary

## Summary

| Item | Value |
| --- | --- |
| Generated At | 2026-09-01 15:36:47 +08:00 |
| Gateway URL | http://47.120.37.61:30081 |
| Phase | recovery |
| Iterations | 5 |
| Total Probes | 16 |
| Passed | 15 |
| Failed | 1 |
| Dashboard Avg Latency ms | 1721.7 |
| Dashboard Degraded Count | 1 |

## Probe Results

| Time | Name | HTTP | Code | Latency ms | Degraded | Dependency | Message | Passed |
| --- | --- | ---: | ---: | ---: | --- | --- | --- | --- |
| 2026-09-01 15:36:19.511 +08:00 | merchant-login | 200 | 200 | 3119.18 | - | - | success | True |
| 2026-09-01 15:36:21.794 +08:00 | merchant-dashboard | 200 | 200 | 2215.44 | True | order-service | è®¢åæå¡æä¸å¯ç¨ï¼å·²è¿åä¸´æ¶çæ¿æ°æ®ï¼è¯·ç¨åå·æ°ã | False |
| 2026-09-01 15:36:21.971 +08:00 | merchant-profile | 200 | 200 | 174.11 | - | - | success | True |
| 2026-09-01 15:36:24.730 +08:00 | merchant-search | 200 | 200 | 2755.24 | - | - | success | True |
| 2026-09-01 15:36:28.829 +08:00 | merchant-dashboard | 200 | 200 | 2087.78 | False | - | - | True |
| 2026-09-01 15:36:28.982 +08:00 | merchant-profile | 200 | 200 | 152.04 | - | - | success | True |
| 2026-09-01 15:36:30.469 +08:00 | merchant-search | 200 | 200 | 1482.85 | - | - | success | True |
| 2026-09-01 15:36:32.698 +08:00 | merchant-dashboard | 200 | 200 | 222.51 | False | - | - | True |
| 2026-09-01 15:36:32.851 +08:00 | merchant-profile | 200 | 200 | 152.12 | - | - | success | True |
| 2026-09-01 15:36:34.376 +08:00 | merchant-search | 200 | 200 | 1520.57 | - | - | success | True |
| 2026-09-01 15:36:38.443 +08:00 | merchant-dashboard | 200 | 200 | 2053.7 | False | - | - | True |
| 2026-09-01 15:36:38.795 +08:00 | merchant-profile | 200 | 200 | 345.04 | - | - | success | True |
| 2026-09-01 15:36:40.074 +08:00 | merchant-search | 200 | 200 | 1289.19 | - | - | success | True |
| 2026-09-01 15:36:44.124 +08:00 | merchant-dashboard | 200 | 200 | 2029.05 | False | - | - | True |
| 2026-09-01 15:36:44.277 +08:00 | merchant-profile | 200 | 200 | 151.3 | - | - | success | True |
| 2026-09-01 15:36:47.486 +08:00 | merchant-search | 200 | 200 | 3200.7 | - | - | success | True |

## Output Files

- CSV: E:\Develop\IDEA\IdeaProject\new\reports\fault\fault-check-20260901-153616-recovery\probe-results.csv
- JSON: E:\Develop\IDEA\IdeaProject\new\reports\fault\fault-check-20260901-153616-recovery\probe-results.json
