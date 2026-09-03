# 性能报告目录

这里存放单体版与微服务版性能对比脚本自动生成的报告和原始数据。

推荐先阅读：

- `performance-comparison-summary.md`：最终汇总口径。
- `monolith/performance-comparison.md`：单体版压测报告。
- `microservice/performance-comparison.md`：微服务版首次压测报告。
- `microservice-rerun-20260903/performance-comparison.md`：2026-09-03 微服务复测报告。

每个压测目录中的 `*.raw.json` 和 `*.raw.csv` 是脚本生成的原始数据，用于复核统计结果。

生成脚本见 [../../scripts/performance/compare-performance.mjs](../../scripts/performance/compare-performance.mjs)。
