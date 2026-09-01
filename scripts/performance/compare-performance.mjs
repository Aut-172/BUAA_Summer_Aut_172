import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { performance } from 'node:perf_hooks';

const execFileAsync = promisify(execFile);

const DEFAULT_SCENARIOS = [
  {
    id: 'catalog-merchants',
    name: '商家列表',
    method: 'GET',
    path: '/merchants?page=1&size=20',
    auth: 'none',
    request: '-',
    expected: 'code=200; 返回商家列表'
  },
  {
    id: 'catalog-product',
    name: '商品详情',
    method: 'GET',
    path: '/products/30001',
    auth: 'none',
    request: 'productId=30001',
    expected: 'code=200; 返回商品详情'
  },
  {
    id: 'order-detail',
    name: '订单详情',
    method: 'GET',
    path: '/orders/70001',
    auth: 'consumer',
    request: 'orderId=70001',
    expected: 'code=200; 返回订单详情'
  }
];

const DEFAULT_OPTIONS = {
  monolithBaseUrl: 'http://localhost:8081/api',
  microserviceBaseUrl: 'http://localhost:8080/api',
  monolithLabel: '单体版本',
  microserviceLabel: '微服务版本',
  username: 'demo',
  password: '123456',
  concurrency: 20,
  requestsPerWorker: 25,
  warmupRequests: 5,
  runs: 3,
  timeoutMs: 30000,
  reportDir: null,
  version: 'both',
  monolithContainers: ['life-assistant-backend'],
  microserviceContainers: [
    'life-assistant-api-gateway',
    'life-assistant-merchant-service',
    'life-assistant-user-service',
    'life-assistant-order-service',
    'life-assistant-settlement-service',
    'life-assistant-fulfillment-service',
    'life-assistant-engagement-service'
  ]
};

function parseArgs(argv) {
  const result = {};
  for (let i = 0; i < argv.length; i += 1) {
    const current = argv[i];
    if (!current.startsWith('--')) {
      continue;
    }
    const key = current.slice(2);
    const next = argv[i + 1];
    if (typeof next === 'undefined' || next.startsWith('--')) {
      result[key] = 'true';
      continue;
    }
    result[key] = next;
    i += 1;
  }
  return result;
}

function getRepoRoot() {
  return path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
}

function toInt(value, fallback) {
  const parsed = Number.parseInt(value ?? '', 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function splitList(value, fallback) {
  if (typeof value !== 'string' || value.trim() === '') {
    return fallback;
  }
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function escapeCell(value) {
  if (value === null || typeof value === 'undefined') {
    return '-';
  }
  const text = Array.isArray(value) || typeof value === 'object'
    ? JSON.stringify(value)
    : String(value);
  return text.replace(/\r?\n/g, '<br>').replace(/\|/g, '\\|');
}

function percentile(values, ratio) {
  if (!values.length) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * ratio) - 1);
  return sorted[index];
}

function mean(values) {
  if (!values.length) {
    return 0;
  }
  return values.reduce((sum, item) => sum + item, 0) / values.length;
}

function formatNumber(value, digits = 2) {
  return Number(value).toFixed(digits);
}

function parseCpuPercent(value) {
  if (!value) {
    return null;
  }
  const parsed = Number.parseFloat(String(value).replace('%', '').trim());
  return Number.isFinite(parsed) ? parsed : null;
}

function parseMemoryToMiB(value) {
  if (!value) {
    return null;
  }
  const match = String(value).trim().match(/^([0-9.]+)\s*([KMGTP]i?B)$/i);
  if (!match) {
    return null;
  }
  const amount = Number.parseFloat(match[1]);
  if (!Number.isFinite(amount)) {
    return null;
  }
  const unit = match[2].toUpperCase();
  const factorMap = {
    B: 1 / (1024 * 1024),
    KB: 1 / 1024,
    KIB: 1 / 1024,
    MB: 1,
    MIB: 1,
    GB: 1024,
    GIB: 1024,
    TB: 1024 * 1024,
    TIB: 1024 * 1024
  };
  const factor = factorMap[unit] ?? 1;
  return amount * factor;
}

async function readJsonResponse(response) {
  const text = await response.text();
  if (!text.trim()) {
    return { rawText: text, json: null };
  }
  try {
    return { rawText: text, json: JSON.parse(text) };
  } catch {
    return { rawText: text, json: null };
  }
}

async function requestJson(url, { method = 'GET', token, body, timeoutMs }) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(new Error(`Request timed out after ${timeoutMs} ms`)), timeoutMs);
  const headers = {
    Accept: 'application/json'
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const init = {
    method,
    headers,
    signal: controller.signal
  };
  if (typeof body !== 'undefined' && body !== null) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  try {
    const response = await fetch(url, init);
    const payload = await readJsonResponse(response);
    return {
      ok: response.ok,
      status: response.status,
      body: payload.json,
      rawText: payload.rawText
    };
  } finally {
    clearTimeout(timeout);
  }
}

async function getCommandLine(command, args, cwd) {
  try {
    const { stdout } = await execFileAsync(command, args, { cwd, windowsHide: true, maxBuffer: 1024 * 1024 });
    return stdout.trim();
  } catch (error) {
    return null;
  }
}

async function getGitInfo(root) {
  const branch = await getCommandLine('git', ['-C', root, 'branch', '--show-current'], root);
  const commit = await getCommandLine('git', ['-C', root, 'rev-parse', '--short', 'HEAD'], root);
  return {
    branch: branch || 'N/A',
    commit: commit || 'N/A'
  };
}

async function login(baseUrl, username, password, timeoutMs) {
  const response = await requestJson(`${baseUrl}/auth/login`, {
    method: 'POST',
    body: { username, password },
    timeoutMs
  });
  if (!response.ok || !response.body?.data?.accessToken) {
    throw new Error(`Login failed for ${baseUrl}: ${response.rawText}`);
  }
  return response.body.data.accessToken;
}

function createDockerSampler(containers, report) {
  if (!containers.length) {
    return {
      stop: async () => {},
      collect: () => null
    };
  }

  let stopped = false;
  let running = false;

  const loop = (async () => {
    while (!stopped) {
      if (running) {
        await new Promise((resolve) => setTimeout(resolve, 250));
        continue;
      }

      running = true;
      try {
        const args = ['stats', '--no-stream', '--format', '{{json .}}', ...containers];
        const { stdout } = await execFileAsync('docker', args, { windowsHide: true, maxBuffer: 1024 * 1024 });
        const lines = stdout.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
        const sample = {
          timestamp: new Date().toISOString(),
          containers: []
        };

        for (const line of lines) {
          try {
            const item = JSON.parse(line);
            const memoryParts = String(item.MemUsage || '').split('/');
            sample.containers.push({
              name: item.Name || item.Container || 'unknown',
              cpuPercent: parseCpuPercent(item.CPUPerc),
              memoryMiB: parseMemoryToMiB(memoryParts[0]?.trim()),
              memoryLimitMiB: parseMemoryToMiB(memoryParts[1]?.trim()),
              raw: item
            });
          } catch {
            continue;
          }
        }

        if (sample.containers.length > 0) {
          report.push(sample);
        }
      } catch {
        // Docker is optional; missing stats should not fail the benchmark.
      } finally {
        running = false;
      }

      await new Promise((resolve) => setTimeout(resolve, 1000));
    }
  })();

  return {
    stop: async () => {
      stopped = true;
      await loop.catch(() => {});
    }
  };
}

function aggregateSamples(samples) {
  const byContainer = new Map();
  for (const sample of samples) {
    for (const container of sample.containers) {
      const current = byContainer.get(container.name) ?? {
        cpu: [],
        memory: [],
        memoryLimit: []
      };
      if (Number.isFinite(container.cpuPercent)) {
        current.cpu.push(container.cpuPercent);
      }
      if (Number.isFinite(container.memoryMiB)) {
        current.memory.push(container.memoryMiB);
      }
      if (Number.isFinite(container.memoryLimitMiB)) {
        current.memoryLimit.push(container.memoryLimitMiB);
      }
      byContainer.set(container.name, current);
    }
  }

  return [...byContainer.entries()].map(([name, stats]) => ({
    name,
    avgCpuPercent: mean(stats.cpu),
    peakCpuPercent: stats.cpu.length ? Math.max(...stats.cpu) : 0,
    avgMemoryMiB: mean(stats.memory),
    peakMemoryMiB: stats.memory.length ? Math.max(...stats.memory) : 0,
    avgMemoryLimitMiB: mean(stats.memoryLimit),
    samples: stats.cpu.length
  }));
}

async function benchmarkScenario(target, scenario, runIndex, options, token) {
  const totalRequests = options.concurrency * options.requestsPerWorker;
  const latencies = [];
  const samples = [];
  const startTime = performance.now();
  const sampler = createDockerSampler(target.containers, samples);

  for (let i = 0; i < options.warmupRequests; i += 1) {
    try {
      await requestJson(`${target.baseUrl}${scenario.path}`, {
        method: scenario.method,
        token: scenario.auth === 'consumer' ? token : undefined,
        timeoutMs: options.timeoutMs
      });
    } catch {
      // Warmup is best-effort only.
    }
  }

  const shared = { nextIndex: 0, success: 0, failure: 0 };
  const workers = Array.from({ length: options.concurrency }, async () => {
    while (true) {
      const index = shared.nextIndex;
      shared.nextIndex += 1;
      if (index >= totalRequests) {
        return;
      }

      const requestStarted = performance.now();
      try {
        const response = await requestJson(`${target.baseUrl}${scenario.path}`, {
          method: scenario.method,
          token: scenario.auth === 'consumer' ? token : undefined,
          timeoutMs: options.timeoutMs
        });
        const duration = performance.now() - requestStarted;
        latencies.push(duration);
        if (response.ok && response.body?.code === 200) {
          shared.success += 1;
        } else {
          shared.failure += 1;
        }
      } catch {
        const duration = performance.now() - requestStarted;
        latencies.push(duration);
        shared.failure += 1;
      }
    }
  });

  await Promise.all(workers);
  await sampler.stop();

  const elapsedSeconds = (performance.now() - startTime) / 1000;
  const avgLatency = mean(latencies);
  const p95Latency = percentile(latencies, 0.95);
  const throughput = elapsedSeconds > 0 ? shared.success / elapsedSeconds : 0;
  const errorRate = totalRequests > 0 ? shared.failure / totalRequests : 0;

  return {
    version: target.label,
    scenarioId: scenario.id,
    scenarioName: scenario.name,
    runIndex,
    method: scenario.method,
    path: scenario.path,
    request: scenario.request,
    expected: scenario.expected,
    concurrency: options.concurrency,
    requestsPerWorker: options.requestsPerWorker,
    totalRequests,
    elapsedSeconds,
    successCount: shared.success,
    failureCount: shared.failure,
    averageLatencyMs: avgLatency,
    p95LatencyMs: p95Latency,
    throughputPerSecond: throughput,
    errorRate,
    latencySamplesMs: latencies,
    resourceSamples: samples,
    resourceSummary: aggregateSamples(samples)
  };
}

function aggregateRuns(runs) {
  const allLatency = runs.flatMap((run) => run.latencySamplesMs);
  const allSuccess = runs.reduce((sum, run) => sum + run.successCount, 0);
  const allFailure = runs.reduce((sum, run) => sum + run.failureCount, 0);
  const allElapsed = runs.reduce((sum, run) => sum + run.elapsedSeconds, 0);
  const resources = runs.flatMap((run) => run.resourceSummary);
  const resourceByContainer = new Map();
  for (const resource of resources) {
    const current = resourceByContainer.get(resource.name) ?? {
      cpu: [],
      memory: [],
      memoryLimit: []
    };
    current.cpu.push(resource.avgCpuPercent);
    current.memory.push(resource.avgMemoryMiB);
    current.memoryLimit.push(resource.avgMemoryLimitMiB);
    resourceByContainer.set(resource.name, current);
  }

  return {
    runs,
    aggregate: {
      averageLatencyMs: mean(allLatency),
      p95LatencyMs: percentile(allLatency, 0.95),
      throughputPerSecond: allElapsed > 0 ? allSuccess / allElapsed : 0,
      errorRate: (allSuccess + allFailure) > 0 ? allFailure / (allSuccess + allFailure) : 0,
      totalRequests: allSuccess + allFailure,
      successCount: allSuccess,
      failureCount: allFailure
    },
    resources: [...resourceByContainer.entries()].map(([name, stats]) => ({
      name,
      avgCpuPercent: mean(stats.cpu),
      avgMemoryMiB: mean(stats.memory),
      avgMemoryLimitMiB: mean(stats.memoryLimit)
    }))
  };
}

function formatDuration(ms) {
  return `${formatNumber(ms, 2)} ms`;
}

function formatRate(value) {
  return `${formatNumber(value, 2)} req/s`;
}

function formatPercent(value) {
  return `${formatNumber(value * 100, 2)}%`;
}

function formatMiB(value) {
  return `${formatNumber(value, 2)} MiB`;
}

function buildScenarioSummaryTable(results) {
  const lines = [];
  lines.push('| 版本 | 场景 | 轮次 | 平均响应时间 | P95 | 吞吐量 | 错误率 |');
  lines.push('| --- | --- | ---: | ---: | ---: | ---: | ---: |');
  for (const run of results) {
    lines.push(`| ${escapeCell(run.version)} | ${escapeCell(run.scenarioName)} | ${run.runIndex} | ${escapeCell(formatDuration(run.averageLatencyMs))} | ${escapeCell(formatDuration(run.p95LatencyMs))} | ${escapeCell(formatRate(run.throughputPerSecond))} | ${escapeCell(formatPercent(run.errorRate))} |`);
  }
  return lines.join('\n');
}

function buildAggregateTable(versionLabel, scenarioMap) {
  const lines = [];
  lines.push(`| ${versionLabel} 场景 | 平均响应时间 | P95 | 吞吐量 | 错误率 |`);
  lines.push('| --- | ---: | ---: | ---: | ---: |');
  for (const [scenarioName, stats] of scenarioMap.entries()) {
    lines.push(`| ${escapeCell(scenarioName)} | ${escapeCell(formatDuration(stats.aggregate.averageLatencyMs))} | ${escapeCell(formatDuration(stats.aggregate.p95LatencyMs))} | ${escapeCell(formatRate(stats.aggregate.throughputPerSecond))} | ${escapeCell(formatPercent(stats.aggregate.errorRate))} |`);
  }
  return lines.join('\n');
}

function buildResourceTable(versionLabel, aggregatedResources) {
  const lines = [];
  lines.push(`| ${versionLabel} 容器 | 平均 CPU | 平均内存 |`);
  lines.push('| --- | ---: | ---: |');
  for (const resource of aggregatedResources) {
    lines.push(`| ${escapeCell(resource.name)} | ${escapeCell(formatNumber(resource.avgCpuPercent, 2) + '%')} | ${escapeCell(formatMiB(resource.avgMemoryMiB))} |`);
  }
  return lines.join('\n');
}

function buildReport(data) {
  const lines = [];
  lines.push('# 性能对比报告');
  lines.push('');
  lines.push('## 测试条件');
  lines.push('');
  lines.push('| 项目 | 值 |');
  lines.push('| --- | --- |');
  lines.push(`| 机器 | ${escapeCell(data.machineName)} |`);
  lines.push(`| 操作系统 | ${escapeCell(data.os)} |`);
  lines.push(`| 生成时间 | ${escapeCell(data.generatedAt)} |`);
  lines.push(`| 并发数 | ${escapeCell(data.options.concurrency)} |`);
  lines.push(`| 每轮请求数 | ${escapeCell(data.options.requestsPerWorker)} |`);
  lines.push(`| 预热请求数 | ${escapeCell(data.options.warmupRequests)} |`);
  lines.push(`| 重复轮次 | ${escapeCell(data.options.runs)} |`);
  lines.push(`| 数据集 | ${escapeCell('demo / 123456, merchant1 / 123456, merchantId=20001, productId=30001, orderId=70001')} |`);
  lines.push(`| 压测脚本 | ${escapeCell('./scripts/performance/compare-performance.mjs')} |`);
  lines.push('');
  lines.push('## 接口选择');
  lines.push('');
  lines.push('- `GET /api/merchants?page=1&size=20`：商家列表，代表高频浏览接口。');
  lines.push('- `GET /api/products/30001`：商品详情，代表目录详情查询。');
  lines.push('- `GET /api/orders/70001`：订单详情，代表登录后查询型接口。');
  lines.push('');
  lines.push('## 版本信息');
  lines.push('');
  lines.push('| 版本 | 基址 | 分支 | 提交 |');
  lines.push('| --- | --- | --- | --- |');
  for (const target of data.targets) {
    lines.push(`| ${escapeCell(target.label)} | ${escapeCell(target.baseUrl)} | ${escapeCell(target.branch)} | ${escapeCell(target.commit)} |`);
  }
  lines.push('');
  lines.push('## 单轮结果');
  lines.push('');
  lines.push(buildScenarioSummaryTable(data.runs));
  lines.push('');
  lines.push('## 分版本汇总');
  lines.push('');
  for (const target of data.targets) {
    lines.push(`### ${target.label}`);
    lines.push('');
    lines.push(buildAggregateTable(target.label, data.byVersionAndScenario.get(target.label)));
    lines.push('');
    lines.push(buildResourceTable(target.label, data.resourceSummaryByVersion.get(target.label)));
    lines.push('');
  }
  lines.push('## 原始数据');
  lines.push('');
  lines.push(`- [JSON 原始结果](${path.basename(data.rawJsonPath)})`);
  lines.push(`- [机器可读汇总](${path.basename(data.rawCsvPath)})`);
  lines.push('');
  lines.push('## 结论写法建议');
  lines.push('');
  lines.push('- 只有当微服务版本在相同条件下的实测数据更优时，才写“性能提升”。');
  lines.push('- 如果微服务版本更慢，也可以如实写出，并结合链路拆分、网关转发和跨服务调用解释原因。');
  lines.push('- 原始数据和每轮结果都保留，便于复核。');
  return lines.join('\n');
}

function buildCsv(rows) {
  const headers = ['version', 'scenarioId', 'scenarioName', 'runIndex', 'averageLatencyMs', 'p95LatencyMs', 'throughputPerSecond', 'errorRate', 'successCount', 'failureCount', 'elapsedSeconds'];
  const lines = [headers.join(',')];
  for (const row of rows) {
    lines.push(headers.map((header) => JSON.stringify(row[header] ?? '')).join(','));
  }
  return lines.join('\n');
}

async function main() {
  const repoRoot = getRepoRoot();
  const defaultMonolithRoot = path.resolve(repoRoot, '..', '..', '中期检查', 'BUAA_Summer_Aut_172-monolith-fianl-version');
  const args = parseArgs(process.argv.slice(2));
  const options = {
    ...DEFAULT_OPTIONS,
    monolithBaseUrl: args['monolith-base-url'] || DEFAULT_OPTIONS.monolithBaseUrl,
    microserviceBaseUrl: args['microservice-base-url'] || DEFAULT_OPTIONS.microserviceBaseUrl,
    monolithLabel: args['monolith-label'] || DEFAULT_OPTIONS.monolithLabel,
    microserviceLabel: args['microservice-label'] || DEFAULT_OPTIONS.microserviceLabel,
    username: args.username || DEFAULT_OPTIONS.username,
    password: args.password || DEFAULT_OPTIONS.password,
    concurrency: toInt(args.concurrency, DEFAULT_OPTIONS.concurrency),
    requestsPerWorker: toInt(args['requests-per-worker'], DEFAULT_OPTIONS.requestsPerWorker),
    warmupRequests: toInt(args['warmup-requests'], DEFAULT_OPTIONS.warmupRequests),
    runs: toInt(args.runs, DEFAULT_OPTIONS.runs),
    timeoutMs: toInt(args['timeout-ms'], DEFAULT_OPTIONS.timeoutMs),
    reportDir: args['report-dir'] || DEFAULT_OPTIONS.reportDir,
    version: (args.version || DEFAULT_OPTIONS.version).toLowerCase(),
    monolithContainers: splitList(args['monolith-containers'], DEFAULT_OPTIONS.monolithContainers),
    microserviceContainers: splitList(args['microservice-containers'], DEFAULT_OPTIONS.microserviceContainers)
  };

  const reportDir = options.reportDir ? path.resolve(options.reportDir) : path.join(repoRoot, 'reports', 'performance');
  const rawJsonPath = path.join(reportDir, 'performance-comparison.raw.json');
  const rawCsvPath = path.join(reportDir, 'performance-comparison.raw.csv');
  const reportPath = path.join(reportDir, 'performance-comparison.md');

  const monolithRoot = args['monolith-root']
    ? path.resolve(args['monolith-root'])
    : defaultMonolithRoot;
  const microserviceRoot = args['microservice-root'] ? path.resolve(args['microservice-root']) : repoRoot;

  const targets = [];
  if (options.version === 'both' || options.version === 'monolith') {
    targets.push({
      label: options.monolithLabel,
      baseUrl: options.monolithBaseUrl,
      containers: options.monolithContainers,
      root: monolithRoot
    });
  }
  if (options.version === 'both' || options.version === 'microservice') {
    targets.push({
      label: options.microserviceLabel,
      baseUrl: options.microserviceBaseUrl,
      containers: options.microserviceContainers,
      root: microserviceRoot
    });
  }

  if (targets.length === 0) {
    throw new Error(`Unsupported version: ${options.version}`);
  }

  const versionInfo = [];
  for (const target of targets) {
    const git = target.root ? await getGitInfo(target.root) : { branch: 'N/A', commit: 'N/A' };
    versionInfo.push({
      label: target.label,
      baseUrl: target.baseUrl,
      branch: git.branch,
      commit: git.commit,
      containers: target.containers
    });
  }

  const loginTokens = {};
  for (const target of targets) {
    loginTokens[target.label] = await login(target.baseUrl, options.username, options.password, options.timeoutMs);
  }

  const scenarioResults = [];
  const byVersionAndScenario = new Map();
  const allRows = [];

  for (const scenario of DEFAULT_SCENARIOS) {
    for (let runIndex = 1; runIndex <= options.runs; runIndex += 1) {
      for (const target of targets) {
        const result = await benchmarkScenario(target, scenario, runIndex, options, loginTokens[target.label]);
        scenarioResults.push(result);
        allRows.push(result);
        const versionMap = byVersionAndScenario.get(target.label) ?? new Map();
        const list = versionMap.get(scenario.name) ?? [];
        list.push(result);
        versionMap.set(scenario.name, list);
        byVersionAndScenario.set(target.label, versionMap);
      }
    }
  }

  const resourceSummaryByVersion = new Map();
  for (const target of targets) {
    const versionMap = byVersionAndScenario.get(target.label) ?? new Map();
    const resourceRuns = [...versionMap.values()].flat();
    resourceSummaryByVersion.set(target.label, aggregateRuns(resourceRuns).resources);
  }

  const generatedAt = new Date().toLocaleString('zh-CN', { hour12: false });
  const machineName = process.env.COMPUTERNAME || process.env.HOSTNAME || 'N/A';
  const os = `${process.platform} ${process.version}`;

  const rawData = {
    generatedAt,
    machineName,
    os,
    options,
    targets: versionInfo,
    scenarios: DEFAULT_SCENARIOS,
    runs: scenarioResults
  };

  await fs.mkdir(reportDir, { recursive: true });
  await fs.writeFile(rawJsonPath, `${JSON.stringify(rawData, null, 2)}\n`, 'utf8');
  await fs.writeFile(rawCsvPath, `${buildCsv(allRows)}\n`, 'utf8');

  const summaryByTarget = new Map();
  for (const target of targets) {
    const versionMap = byVersionAndScenario.get(target.label) ?? new Map();
    const summaryMap = new Map();
    for (const [scenarioName, runs] of versionMap.entries()) {
      summaryMap.set(scenarioName, aggregateRuns(runs));
    }
    summaryByTarget.set(target.label, summaryMap);
  }

  const report = buildReport({
    generatedAt,
    machineName,
    os,
    options,
    targets: versionInfo,
    runs: scenarioResults,
    byVersionAndScenario: summaryByTarget,
    resourceSummaryByVersion,
    rawJsonPath,
    rawCsvPath
  });

  await fs.writeFile(reportPath, `${report}\n`, 'utf8');

  console.log(`Performance report written to ${reportPath}`);
  console.log(`Raw JSON written to ${rawJsonPath}`);
  console.log(`Raw CSV written to ${rawCsvPath}`);
}

await main();
