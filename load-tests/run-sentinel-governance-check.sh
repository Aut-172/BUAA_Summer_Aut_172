#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

GATEWAY_URL="${GATEWAY_URL:-http://47.120.37.61:30081}"
NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-}"
MODE="${MODE:-baseline}"
APPLY_TEMPORARY_RULES="false"
RESTORE_ORIGINAL_RULES="true"
MERCHANT_USERNAME="${MERCHANT_USERNAME:-merchant1}"
MERCHANT_PASSWORD="${MERCHANT_PASSWORD:-123456}"
MERCHANT_TOKEN="${MERCHANT_TOKEN:-}"
KEYWORD="${KEYWORD:-Braised}"
ITERATIONS="${ITERATIONS:-12}"
INTERVAL_MS="${INTERVAL_MS:-80}"
RULE_WARMUP_SECONDS="${RULE_WARMUP_SECONDS:-8}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-6}"
OUTPUT_DIR="${OUTPUT_DIR:-}"

usage() {
  cat <<'USAGE'
Usage: run-sentinel-governance-check.sh [options]

Options:
  --gateway-url URL             Gateway base URL, default http://47.120.37.61:30081
  --nacos-url URL               Nacos base URL, default http://127.0.0.1:8848
  --group GROUP                 Nacos group, default DEFAULT_GROUP
  --namespace ID                Nacos namespace/tenant, default public namespace
  --mode MODE                   baseline, gateway-flow, service-flow, dependency-fallback, all
  --apply-temporary-rules       Backup, publish low-threshold rules, then restore them
  --no-restore-original-rules   Keep temporary rules after the run
  --merchant-username USER      Merchant username, default merchant1
  --merchant-password PASS      Merchant password, default 123456
  --merchant-token TOKEN        Existing merchant access token; skips login
  --keyword KEYWORD             Search keyword, default Braised
  --iterations N                Requests per probe target, default 12
  --interval-ms N               Delay between requests, default 80
  --rule-warmup-seconds N       Wait after rule publish/restore, default 8
  --timeout-seconds N           curl timeout, default 6
  --output-dir DIR              Output directory
  -h, --help                    Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gateway-url) GATEWAY_URL="$2"; shift 2 ;;
    --nacos-url) NACOS_URL="$2"; shift 2 ;;
    --group) GROUP="$2"; shift 2 ;;
    --namespace) NAMESPACE="$2"; shift 2 ;;
    --mode) MODE="$2"; shift 2 ;;
    --apply-temporary-rules) APPLY_TEMPORARY_RULES="true"; shift ;;
    --no-restore-original-rules) RESTORE_ORIGINAL_RULES="false"; shift ;;
    --merchant-username) MERCHANT_USERNAME="$2"; shift 2 ;;
    --merchant-password) MERCHANT_PASSWORD="$2"; shift 2 ;;
    --merchant-token) MERCHANT_TOKEN="$2"; shift 2 ;;
    --keyword) KEYWORD="$2"; shift 2 ;;
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --interval-ms) INTERVAL_MS="$2"; shift 2 ;;
    --rule-warmup-seconds) RULE_WARMUP_SECONDS="$2"; shift 2 ;;
    --timeout-seconds) TIMEOUT_SECONDS="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$MODE" in
  baseline|gateway-flow|service-flow|dependency-fallback|all) ;;
  *) echo "Unsupported mode: $MODE" >&2; exit 2 ;;
esac

command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required." >&2; exit 1; }

GATEWAY_URL="${GATEWAY_URL%/}"
NACOS_URL="${NACOS_URL%/}"

if [[ "$GATEWAY_URL" =~ 你的|公网|域名|ECS|[[:space:]] ]]; then
  echo "Gateway URL looks like a placeholder: $GATEWAY_URL" >&2
  exit 2
fi

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="${REPO_ROOT}/reports/sentinel/sentinel-check-$(date +%Y%m%d-%H%M%S)-${MODE}"
fi
BACKUP_DIR="${OUTPUT_DIR}/original-nacos-configs"
mkdir -p "$BACKUP_DIR"

CSV_PATH="${OUTPUT_DIR}/sentinel-probe-results.csv"
JSONL_PATH="${OUTPUT_DIR}/sentinel-probe-results.jsonl"
SUMMARY_PATH="${OUTPUT_DIR}/sentinel-probe-summary.md"
BACKUP_LIST="${OUTPUT_DIR}/backup-data-ids.txt"
: > "$BACKUP_LIST"
echo 'timestamp,scenario,name,method,url,http_status,business_code,elapsed_ms,degraded,dependency,message,fallback_reason,error' > "$CSV_PATH"
: > "$JSONL_PATH"

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

join_url() {
  local base="${1%/}"
  local path="/${2#/}"
  printf '%s%s' "$base" "$path"
}

nacos_query() {
  local data_id="$1"
  local query="dataId=$(urlencode "$data_id")&group=$(urlencode "$GROUP")"
  if [[ -n "$NAMESPACE" ]]; then
    query="${query}&tenant=$(urlencode "$NAMESPACE")"
  fi
  printf '%s' "$query"
}

get_nacos_config() {
  local data_id="$1"
  curl -fsS --connect-timeout 5 --max-time "$TIMEOUT_SECONDS" \
    "${NACOS_URL}/nacos/v1/cs/configs?$(nacos_query "$data_id")"
}

publish_nacos_config() {
  local data_id="$1"
  local content_file="$2"
  local response_file
  response_file="$(mktemp)"
  local args=(
    -fsS
    --connect-timeout 5
    --max-time "$TIMEOUT_SECONDS"
    -X POST
    "${NACOS_URL}/nacos/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${GROUP}"
    --data-urlencode "type=json"
    --data-urlencode "content@${content_file}"
    -o "$response_file"
  )
  if [[ -n "$NAMESPACE" ]]; then
    args+=(--data-urlencode "tenant=${NAMESPACE}")
  fi
  curl "${args[@]}"
  local response
  response="$(tr -d '\r\n' < "$response_file")"
  rm -f "$response_file"
  if [[ "$response" != "true" ]]; then
    echo "Failed to publish ${data_id}. Response: ${response}" >&2
    exit 1
  fi
}

backup_nacos_config() {
  local data_id="$1"
  local backup_file="${BACKUP_DIR}/${data_id}"
  if [[ -f "$backup_file" ]]; then
    return
  fi
  get_nacos_config "$data_id" > "$backup_file"
  echo "$data_id" >> "$BACKUP_LIST"
}

set_json_rule_count() {
  local data_id="$1"
  local resource="$2"
  local count="$3"
  local burst="${4:-}"
  local backup_file="${BACKUP_DIR}/${data_id}"
  local temp_file
  temp_file="$(mktemp)"

  backup_nacos_config "$data_id"
  python3 - "$backup_file" "$resource" "$count" "$burst" > "$temp_file" <<'PY'
import json
import sys

path, resource, count_text, burst_text = sys.argv[1:]
with open(path, encoding='utf-8') as f:
    rules = json.load(f)

matched = False
for rule in rules:
    if rule.get('resource') == resource:
        rule['count'] = float(count_text) if '.' in count_text else int(count_text)
        if burst_text and 'burst' in rule:
            rule['burst'] = int(burst_text)
        matched = True

if not matched:
    raise SystemExit(f"Rule resource '{resource}' was not found in {path}")

print(json.dumps(rules, ensure_ascii=False, separators=(',', ':')))
PY
  publish_nacos_config "$data_id" "$temp_file"
  rm -f "$temp_file"
}

restore_nacos_backups() {
  if [[ ! -s "$BACKUP_LIST" ]]; then
    return
  fi
  while IFS= read -r data_id; do
    [[ -z "$data_id" ]] && continue
    publish_nacos_config "$data_id" "${BACKUP_DIR}/${data_id}"
    echo "Restored $data_id"
  done < "$BACKUP_LIST"
}

json_field() {
  local body_file="$1"
  local expr="$2"
  python3 - "$body_file" "$expr" <<'PY'
import json
import sys

path, expr = sys.argv[1:]
try:
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
except Exception:
    print('')
    raise SystemExit(0)

value = data
for part in expr.split('.'):
    if isinstance(value, dict) and part in value:
        value = value[part]
    else:
        print('')
        raise SystemExit(0)

if isinstance(value, bool):
    print('true' if value else 'false')
elif value is None:
    print('')
else:
    print(value)
PY
}

csv_escape() {
  local value="${1:-}"
  value="${value//$'\r'/ }"
  value="${value//$'\n'/ }"
  value="${value//\"/\"\"}"
  printf '"%s"' "$value"
}

record_probe() {
  local scenario="$1"
  local name="$2"
  local method="$3"
  local url="$4"
  local body_file="$5"
  local http_status="$6"
  local elapsed_ms="$7"
  local error="$8"
  local ts
  ts="$(date '+%Y-%m-%d %H:%M:%S.%3N %z')"
  local business_code message degraded dependency fallback_reason
  business_code="$(json_field "$body_file" code)"
  message="$(json_field "$body_file" message)"
  degraded="$(json_field "$body_file" data.degraded)"
  dependency="$(json_field "$body_file" data.degradedDependency)"
  fallback_reason="$(json_field "$body_file" data.fallbackReason)"
  local degradation_message
  degradation_message="$(json_field "$body_file" data.degradationMessage)"
  if [[ -n "$degradation_message" ]]; then
    message="$degradation_message"
  fi

  {
    csv_escape "$ts"; printf ','
    csv_escape "$scenario"; printf ','
    csv_escape "$name"; printf ','
    csv_escape "$method"; printf ','
    csv_escape "$url"; printf ','
    csv_escape "$http_status"; printf ','
    csv_escape "$business_code"; printf ','
    csv_escape "$elapsed_ms"; printf ','
    csv_escape "$degraded"; printf ','
    csv_escape "$dependency"; printf ','
    csv_escape "$message"; printf ','
    csv_escape "$fallback_reason"; printf ','
    csv_escape "$error"; printf '\n'
  } >> "$CSV_PATH"

  python3 - "$body_file" "$JSONL_PATH" "$ts" "$scenario" "$name" "$method" "$url" "$http_status" "$business_code" "$elapsed_ms" "$degraded" "$dependency" "$message" "$fallback_reason" "$error" <<'PY'
import json
import sys

body_path, out_path = sys.argv[1], sys.argv[2]
keys = ['timestamp', 'scenario', 'name', 'method', 'url', 'httpStatus', 'businessCode', 'elapsedMs', 'degraded', 'dependency', 'message', 'fallbackReason', 'error']
values = sys.argv[3:]
record = dict(zip(keys, values))
try:
    with open(body_path, encoding='utf-8') as f:
        record['body'] = json.load(f)
except Exception:
    with open(body_path, encoding='utf-8', errors='replace') as f:
        record['body'] = f.read()
with open(out_path, 'a', encoding='utf-8') as f:
    f.write(json.dumps(record, ensure_ascii=False) + '\n')
PY
}

probe_request() {
  local scenario="$1"
  local name="$2"
  local method="$3"
  local url="$4"
  local token="${5:-}"
  local request_body="${6:-}"
  local body_file header_file status_file time_file
  body_file="$(mktemp)"
  header_file="$(mktemp)"
  status_file="$(mktemp)"
  time_file="$(mktemp)"

  local args=(
    -sS
    --connect-timeout 5
    --max-time "$TIMEOUT_SECONDS"
    -X "$method"
    -D "$header_file"
    -o "$body_file"
    -w '%{http_code} %{time_total}'
  )
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer ${token}")
  fi
  if [[ -n "$request_body" ]]; then
    args+=(-H 'Content-Type: application/json; charset=utf-8' --data "$request_body")
  fi

  local error=""
  if curl "${args[@]}" "$url" > "$time_file" 2>"$status_file"; then
    true
  else
    error="$(tr -d '\r\n' < "$status_file")"
  fi

  local status_and_time http_status elapsed_ms
  status_and_time="$(cat "$time_file")"
  http_status="${status_and_time%% *}"
  elapsed_ms="0"
  if [[ "$status_and_time" == *" "* ]]; then
    elapsed_ms="$(python3 -c 'import sys; print(round(float(sys.argv[1]) * 1000, 2))' "${status_and_time##* }")"
  fi
  [[ -z "$http_status" ]] && http_status="0"

  record_probe "$scenario" "$name" "$method" "$url" "$body_file" "$http_status" "$elapsed_ms" "$error"
  cat "$body_file"
  rm -f "$body_file" "$header_file" "$status_file" "$time_file"
}

merchant_token() {
  if [[ -n "$MERCHANT_TOKEN" ]]; then
    printf '%s' "$MERCHANT_TOKEN"
    return
  fi
  local login_body response token
  login_body="$(python3 -c 'import json,sys; print(json.dumps({"username":sys.argv[1],"password":sys.argv[2]}, ensure_ascii=False))' "$MERCHANT_USERNAME" "$MERCHANT_PASSWORD")"
  response="$(probe_request dependency-fallback merchant-login POST "$(join_url "$GATEWAY_URL" /api/auth/merchant/login)" "" "$login_body")"
  token="$(printf '%s' "$response" | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data.get("data",{}).get("accessToken", ""))' 2>/dev/null || true)"
  if [[ -z "$token" ]]; then
    echo "Merchant login failed. Disable captcha or pass --merchant-token." >&2
    exit 1
  fi
  printf '%s' "$token"
}

sleep_interval() {
  if [[ "$INTERVAL_MS" -gt 0 ]]; then
    python3 -c 'import sys,time; time.sleep(int(sys.argv[1])/1000)' "$INTERVAL_MS"
  fi
}

burst() {
  local scenario="$1"
  local name="$2"
  local method="$3"
  local url="$4"
  local token="${5:-}"
  local body="${6:-}"
  for ((i = 1; i <= ITERATIONS; i++)); do
    echo "[$scenario] probe $i/$ITERATIONS $method $url"
    probe_request "$scenario" "$name" "$method" "$url" "$token" "$body" >/dev/null
    if [[ "$i" -lt "$ITERATIONS" ]]; then
      sleep_interval
    fi
  done
}

apply_rules_for_scenario() {
  local scenario="$1"
  if [[ "$APPLY_TEMPORARY_RULES" != "true" ]]; then
    return
  fi
  case "$scenario" in
    gateway-flow)
      set_json_rule_count sentinel-api-gateway-gw-flow.json gateway-search-api 1 0
      ;;
    service-flow)
      set_json_rule_count sentinel-api-gateway-gw-flow.json gateway-search-api 1000 100
      set_json_rule_count sentinel-merchant-service-flow.json /api/search 1
      ;;
    dependency-fallback)
      set_json_rule_count sentinel-api-gateway-gw-flow.json gateway-merchant-dashboard-api 1000 100
      set_json_rule_count sentinel-merchant-service-flow.json /api/merchant/dashboard 1000
      set_json_rule_count sentinel-order-service-flow.json /internal/orders/merchant-dashboard 1
      ;;
  esac
  echo "Waiting ${RULE_WARMUP_SECONDS}s for Sentinel rule refresh..."
  sleep "$RULE_WARMUP_SECONDS"
}

run_scenario() {
  local scenario="$1"
  apply_rules_for_scenario "$scenario"
  case "$scenario" in
    baseline)
      burst baseline captcha GET "$(join_url "$GATEWAY_URL" /api/captcha)"
      burst baseline search GET "$(join_url "$GATEWAY_URL" "/api/search?keyword=$(urlencode "$KEYWORD")")"
      ;;
    gateway-flow)
      burst gateway-flow gateway-search-api GET "$(join_url "$GATEWAY_URL" "/api/search?keyword=$(urlencode "$KEYWORD")")"
      ;;
    service-flow)
      burst service-flow merchant-service-search GET "$(join_url "$GATEWAY_URL" "/api/search?keyword=$(urlencode "$KEYWORD")")"
      ;;
    dependency-fallback)
      local token
      token="$(merchant_token)"
      burst dependency-fallback merchant-dashboard GET "$(join_url "$GATEWAY_URL" /api/merchant/dashboard)" "$token"
      ;;
  esac
}

finish() {
  local exit_code=$?
  if [[ "$APPLY_TEMPORARY_RULES" == "true" && "$RESTORE_ORIGINAL_RULES" == "true" ]]; then
    restore_nacos_backups || true
    if [[ "$RULE_WARMUP_SECONDS" -gt 0 && -s "$BACKUP_LIST" ]]; then
      echo "Waiting ${RULE_WARMUP_SECONDS}s after rule restore..."
      sleep "$RULE_WARMUP_SECONDS"
    fi
  fi
  exit "$exit_code"
}
trap finish EXIT

if [[ "$APPLY_TEMPORARY_RULES" == "true" ]]; then
  curl -fsS --connect-timeout 5 --max-time "$TIMEOUT_SECONDS" "${NACOS_URL}/nacos/actuator/health" >/dev/null
fi

if [[ "$MODE" == "all" ]]; then
  for scenario in baseline gateway-flow service-flow dependency-fallback; do
    run_scenario "$scenario"
  done
else
  run_scenario "$MODE"
fi

python3 - "$CSV_PATH" "$SUMMARY_PATH" "$GATEWAY_URL" "$NACOS_URL" "$GROUP" "$NAMESPACE" "$MODE" "$APPLY_TEMPORARY_RULES" "$RESTORE_ORIGINAL_RULES" "$JSONL_PATH" "$BACKUP_DIR" <<'PY'
import csv
import sys
from collections import defaultdict
from datetime import datetime

csv_path, summary_path, gateway_url, nacos_url, group, namespace, mode, applied, restored, jsonl_path, backup_dir = sys.argv[1:]
rows = []
with open(csv_path, encoding='utf-8-sig', newline='') as f:
    rows = list(csv.DictReader(f))

groups = defaultdict(list)
for row in rows:
    groups[row['scenario']].append(row)

def count(rows, key, value):
    return sum(1 for row in rows if row.get(key) == value)

def avg(rows):
    values = []
    for row in rows:
        try:
            values.append(float(row.get('elapsed_ms') or 0))
        except ValueError:
            pass
    return round(sum(values) / len(values), 2) if values else 0

lines = []
lines.append('# Sentinel Governance Probe Summary')
lines.append('')
lines.append('| Item | Value |')
lines.append('| --- | --- |')
lines.append(f'| Generated At | {datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %z")} |')
lines.append(f'| Gateway URL | {gateway_url} |')
lines.append(f'| Nacos URL | {nacos_url} |')
lines.append(f'| Group | {group} |')
lines.append(f'| Namespace | {namespace or "public"} |')
lines.append(f'| Mode | {mode} |')
lines.append(f'| Temporary Rules Applied | {applied} |')
lines.append(f'| Original Rules Restored | {restored} |')
lines.append(f'| Total Probes | {len(rows)} |')
lines.append('')
lines.append('## Scenario Metrics')
lines.append('')
lines.append('| Scenario | Count | HTTP 429 | Business 429 | HTTP 503 | Business 503 | Degraded | Average Latency ms |')
lines.append('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |')
for scenario in sorted(groups):
    group_rows = groups[scenario]
    degraded = sum(1 for row in group_rows if str(row.get('degraded', '')).lower() == 'true')
    lines.append(
        f'| {scenario} | {len(group_rows)} | {count(group_rows, "http_status", "429")} | '
        f'{count(group_rows, "business_code", "429")} | {count(group_rows, "http_status", "503")} | '
        f'{count(group_rows, "business_code", "503")} | {degraded} | {avg(group_rows)} |'
    )
lines.append('')
lines.append('## Probe Results')
lines.append('')
lines.append('| Time | Scenario | Name | HTTP | Code | Latency ms | Degraded | Message | Error |')
lines.append('| --- | --- | --- | ---: | ---: | ---: | --- | --- | --- |')
for row in rows:
    message = (row.get('message') or '-').replace('|', '\\|')
    error = (row.get('error') or '-').replace('|', '\\|')
    lines.append(
        f'| {row.get("timestamp") or "-"} | {row.get("scenario") or "-"} | {row.get("name") or "-"} | '
        f'{row.get("http_status") or "-"} | {row.get("business_code") or "-"} | {row.get("elapsed_ms") or "-"} | '
        f'{row.get("degraded") or "-"} | {message} | {error} |'
    )
lines.append('')
lines.append('## Expected Signals')
lines.append('')
lines.append('- baseline: HTTP 2xx and business code 200.')
lines.append('- gateway-flow: at least one HTTP 429 or business 429 with gateway block message.')
lines.append('- service-flow: at least one HTTP 429 or business 429 with business service block message.')
lines.append('- dependency-fallback: at least one dashboard response with code 200 and data.degraded=true.')
lines.append('')
lines.append('## Output Files')
lines.append('')
lines.append(f'- CSV: {csv_path}')
lines.append(f'- JSONL: {jsonl_path}')
lines.append(f'- Original Nacos configs: {backup_dir}')

with open(summary_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
PY

echo "Sentinel probe CSV: $CSV_PATH"
echo "Sentinel probe JSONL: $JSONL_PATH"
echo "Sentinel probe summary: $SUMMARY_PATH"
