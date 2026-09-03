# 报告目录说明

本目录只保留当前交付仍需要追溯的测试、性能和治理实验材料。HPA/JMeter 历史实验材料已保留在 `reports/perf/`，重复嵌套采集目录和已被新版报告替代的旧实验材料应及时清理，避免误用旧结论。

最终汇报材料总入口位于 `docs/delivery/00-交付材料清单.md`，该文件已经把测试报告、故障处理报告、压测对比报告和 HPA 报告与提交清单逐项对应。

## 当前推荐阅读入口

| 类别 | 推荐入口 | 说明 |
| --- | --- | --- |
| 后端全量测试 | `testing/microservice-test-report.md` | 最近仓库内报告为 84/84 通过。 |
| B 侧专项测试 | `testing/b-side-test-report.md` | 最近仓库内报告为 63/63 通过。 |
| 前端测试 | `testing/frontend-e2e-report.md` | 历史本机报告失败原因是 Playwright 浏览器启动 `spawn EPERM`，需结合 CI 判断。 |
| 性能对比 | `performance/performance-comparison-summary.md` | 单体和微服务性能对比汇总。 |
| HPA 原始归档 | `perf/hpa-20260901-102919/`、`perf/hpa-20260901-112703/` | 2026-09-01 自动扩缩容 JMeter HTML、统计 JSON 和 Kubernetes 观测数据。 |
| Sentinel 治理 | `sentinel/sentinel-governance-experiment-report-20260903.md` | 2026-09-03 Sentinel 限流、熔断和降级实验报告。 |
| 故障容错 | `fault/fault-tolerance-experiment-report-20260903.md` | 2026-09-03 商家看板依赖订单服务故障容错实验报告。 |

## 保留规则

- 保留可支撑最终交付结论的 Markdown 汇总报告。
- 保留 2026-09-03 治理实验的原始 CSV、探测摘要和 Kubernetes 采集材料。
- 保留 `testing/` 下用于解释当前测试结论的报告和日志。
- 性能对比保留结构化原始数据 `*.raw.json`、`*.raw.csv` 和汇总 Markdown。

## 清理规则

- 已被新版报告替代的旧无日期报告可以删除。
- HPA/JMeter 历史归档已经纳入主干；后续重跑产生的 `*.jtl`、日志、压缩包和临时目录由 `.gitignore` 忽略。
- 重复嵌套采集目录不再纳入主干。
- 旧日期实验材料如需长期保存，建议移至外部归档或 Git tag，不放在当前交付分支中。
