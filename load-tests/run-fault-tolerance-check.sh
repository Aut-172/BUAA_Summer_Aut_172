#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

GATEWAY_URL="${GATEWAY_URL:-http://47.120.37.61:30081}"
PHASE="${PHASE:-baseline}"
MERCHANT_USERNAME="${MERCHANT_USERNAME:-merchant1}"
MERCHANT_PASSWORD="${MERCHANT_PASSWORD:-123456}"
CONSUMER_USERNAME="${CONSUMER_USERNAME:-demo}"
CONSUMER_PASSWORD="${CONSUMER_PASSWORD:-123456}"
RIDER_USERNAME="${RIDER_USERNAME:-rider01}"
RIDER_PASSWORD="${RIDER_PASSWORD:-123456}"
MERCHANT_TOKEN="${MERCHANT_TOKEN:-}"
CONSUMER_TOKEN="${CONSUMER_TOKEN:-}"
RIDER_TOKEN="${RIDER_TOKEN:-}"
ORDER_ID="${ORDER_ID:-70001}"
KEYWORD="${KEYWORD:-Braised}"
ITERATIONS="${ITERATIONS:-5}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-2}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-6}"
OUTPUT_DIR="${OUTPUT_DIR:-}"

usage() {
  cat <<'USAGE'
Usage: run-fault-tolerance-check.sh [options]

Options:
  --gateway-url URL             Gateway base URL, default http://47.120.37.61:30081
  --phase PHASE                 baseline, fault, recovery, custom; default baseline
  --merchant-username USER      Merchant username, default merchant1
  --merchant-password PASS      Merchant password, default 123456
  --consumer-username USER      Consumer username, default demo
  --consumer-password PASS      Consumer password, default 123456
  --rider-username USER         Rider username, default rider01
  --rider-password PASS         Rider password, default 123456
  --merchant-token TOKEN        Existing merchant access token; skips merchant login
  --consumer-token TOKEN        Existing consumer access token; skips consumer login
  --rider-token TOKEN           Existing rider access token; skips rider login
  --order-id ID                 Demo order id used by order-dependent probes, default 70001
  --keyword KEYWORD             Search keyword, default Braised
  --iterations N                Probe iterations, default 5
  --interval-seconds N          Delay between iterations, default 2
  --timeout-seconds N           curl timeout, default 6
  --output-dir DIR              Output directory
  -h, --help                    Show this help

Environment variables with the same upper-case names can also be used.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gateway-url) GATEWAY_URL="$2"; shift 2 ;;
    --phase) PHASE="$2"; shift 2 ;;
    --merchant-username) MERCHANT_USERNAME="$2"; shift 2 ;;
    --merchant-password) MERCHANT_PASSWORD="$2"; shift 2 ;;
    --consumer-username) CONSUMER_USERNAME="$2"; shift 2 ;;
    --consumer-password) CONSUMER_PASSWORD="$2"; shift 2 ;;
    --rider-username) RIDER_USERNAME="$2"; shift 2 ;;
    --rider-password) RIDER_PASSWORD="$2"; shift 2 ;;
    --merchant-token) MERCHANT_TOKEN="$2"; shift 2 ;;
    --consumer-token) CONSUMER_TOKEN="$2"; shift 2 ;;
    --rider-token) RIDER_TOKEN="$2"; shift 2 ;;
    --order-id) ORDER_ID="$2"; shift 2 ;;
    --keyword) KEYWORD="$2"; shift 2 ;;
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --interval-seconds) INTERVAL_SECONDS="$2"; shift 2 ;;
    --timeout-seconds) TIMEOUT_SECONDS="$2"; shift 2 ;;
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

case "$PHASE" in
  baseline|fault|recovery|custom) ;;
  *) echo "Unsupported phase: $PHASE" >&2; exit 2 ;;
esac

command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required." >&2; exit 1; }

GATEWAY_URL="${GATEWAY_URL%/}"
if [[ "$GATEWAY_URL" =~ 你的|公网|域名|ECS|[[:space:]] ]]; then
  echo "Gateway URL looks like a placeholder: $GATEWAY_URL" >&2
  exit 2
fi

if [[ -z "$OUTPUT_DIR" ]]; then
  OUTPUT_DIR="${REPO_ROOT}/reports/fault/fault-check-$(date +%Y%m%d-%H%M%S)-${PHASE}-multi-link"
fi
mkdir -p "$OUTPUT_DIR"

CSV_PATH="${OUTPUT_DIR}/probe-results.csv"
SUMMARY_PATH="${OUTPUT_DIR}/probe-summary.md"
BODY_DIR="${OUTPUT_DIR}/bodies"
mkdir -p "$BODY_DIR"

echo 'timestamp,phase,iteration,category,name,method,url,http_status,business_code,elapsed_ms,degraded,dependency,message,fallback_reason,expected,passed,error,body_file' > "$CSV_PATH"

urlencode() {
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

join_url() {
  local base="${1%/}"
  local path="/${2#/}"
  printf '%s%s' "$base" "$path"
}

json_string() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1], ensure_ascii=False))' "$1"
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
  local iteration="$1"
  local category="$2"
  local name="$3"
  local method="$4"
  local url="$5"
  local body_file="$6"
  local http_status="$7"
  local elapsed_ms="$8"
  local expected="$9"
  local passed="${10}"
  local error="${11}"
  local ts business_code message degraded dependency fallback_reason degradation_message
  ts="$(date '+%Y-%m-%d %H:%M:%S.%3N %z')"
  business_code="$(json_field "$body_file" code)"
  message="$(json_field "$body_file" message)"
  degraded="$(json_field "$body_file" data.degraded)"
  dependency="$(json_field "$body_file" data.degradedDependency)"
  fallback_reason="$(json_field "$body_file" data.fallbackReason)"
  degradation_message="$(json_field "$body_file" data.degradationMessage)"
  if [[ -n "$degradation_message" ]]; then
    message="$degradation_message"
  fi

  {
    csv_escape "$ts"; printf ','
    csv_escape "$PHASE"; printf ','
    csv_escape "$iteration"; printf ','
    csv_escape "$category"; printf ','
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
    csv_escape "$expected"; printf ','
    csv_escape "$passed"; printf ','
    csv_escape "$error"; printf ','
    csv_escape "$body_file"; printf '\n'
  } >> "$CSV_PATH"
}

assert_probe() {
  local category="$1"
  local body_file="$2"
  local http_status="$3"
  local business_code degraded
  business_code="$(json_field "$body_file" code)"
  degraded="$(json_field "$body_file" data.degraded)"

  case "$category" in
    success|bypass-success)
      [[ "$http_status" =~ ^2 && ("$business_code" == "200" || -z "$business_code") ]]
      ;;
    dashboard-normal)
      [[ "$http_status" =~ ^2 && "$business_code" == "200" && ("$degraded" == "false" || -z "$degraded") ]]
      ;;
    dashboard-degraded)
      [[ "$http_status" =~ ^2 && "$business_code" == "200" && "$degraded" == "true" ]]
      ;;
    dependency-unavailable)
      [[ "$http_status" == "0" || "$http_status" == "502" || "$http_status" == "503" || "$http_status" == "504" || "$business_code" == "503" ]]
      ;;
    *)
      return 1
      ;;
  esac
}

expected_text() {
  local category="$1"
  case "$category" in
    success|bypass-success) printf 'HTTP 2xx and business code 200' ;;
    dashboard-normal) printf 'HTTP 2xx, code=200, degraded is false or empty' ;;
    dashboard-degraded) printf 'HTTP 2xx, code=200, degraded=true' ;;
    dependency-unavailable) printf 'Fast failure for order dependency: HTTP 502/503/504 or business code 503' ;;
    *) printf 'custom assertion' ;;
  esac
}

probe_request() {
  local iteration="$1"
  local category="$2"
  local name="$3"
  local method="$4"
  local url="$5"
  local token="${6:-}"
  local request_body="${7:-}"
  local safe_name body_file status_file time_file
  safe_name="$(printf '%s' "${iteration}-${name}" | tr -c 'A-Za-z0-9_.-' '_')"
  body_file="${BODY_DIR}/${safe_name}.json"
  status_file="$(mktemp)"
  time_file="$(mktemp)"
  : > "$body_file"

  local args=(
    -sS
    --connect-timeout 5
    --max-time "$TIMEOUT_SECONDS"
    -X "$method"
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

  local status_and_time http_status elapsed_ms passed expected
  status_and_time="$(cat "$time_file")"
  http_status="${status_and_time%% *}"
  [[ -z "$http_status" ]] && http_status="0"
  elapsed_ms="0"
  if [[ "$status_and_time" == *" "* ]]; then
    elapsed_ms="$(python3 -c 'import sys; print(round(float(sys.argv[1]) * 1000, 2))' "${status_and_time##* }")"
  fi

  expected="$(expected_text "$category")"
  if assert_probe "$category" "$body_file" "$http_status"; then
    passed="true"
  else
    passed="false"
  fi
  record_probe "$iteration" "$category" "$name" "$method" "$url" "$body_file" "$http_status" "$elapsed_ms" "$expected" "$passed" "$error"
  rm -f "$status_file" "$time_file"
}

login_token() {
  local role="$1"
  local url="$2"
  local username="$3"
  local password="$4"
  local provided_token="$5"
  if [[ -n "$provided_token" ]]; then
    printf '%s' "$provided_token"
    return
  fi

  local body_file status_file time_file request_body status_and_time http_status token
  body_file="${BODY_DIR}/login-${role}.json"
  status_file="$(mktemp)"
  time_file="$(mktemp)"
  request_body="{\"username\":$(json_string "$username"),\"password\":$(json_string "$password")}"
  if curl -sS --connect-timeout 5 --max-time "$TIMEOUT_SECONDS" \
      -H 'Content-Type: application/json; charset=utf-8' \
      -X POST --data "$request_body" -o "$body_file" -w '%{http_code} %{time_total}' \
      "$url" > "$time_file" 2>"$status_file"; then
    true
  fi
  status_and_time="$(cat "$time_file")"
  http_status="${status_and_time%% *}"
  token="$(json_field "$body_file" data.accessToken)"
  rm -f "$status_file" "$time_file"
  if [[ ! "$http_status" =~ ^2 || -z "$token" ]]; then
    echo "${role} login failed. Disable captcha or pass --${role}-token. Body file: ${body_file}" >&2
    exit 1
  fi
  printf '%s' "$token"
}

MERCHANT_TOKEN="$(login_token merchant "$(join_url "$GATEWAY_URL" /api/auth/merchant/login)" "$MERCHANT_USERNAME" "$MERCHANT_PASSWORD" "$MERCHANT_TOKEN")"
CONSUMER_TOKEN="$(login_token consumer "$(join_url "$GATEWAY_URL" /api/auth/login)" "$CONSUMER_USERNAME" "$CONSUMER_PASSWORD" "$CONSUMER_TOKEN")"
RIDER_TOKEN="$(login_token rider "$(join_url "$GATEWAY_URL" /api/auth/rider/login)" "$RIDER_USERNAME" "$RIDER_PASSWORD" "$RIDER_TOKEN")"

dashboard_category="dashboard-normal"
order_dependent_category="success"
if [[ "$PHASE" == "fault" ]]; then
  dashboard_category="dashboard-degraded"
  order_dependent_category="dependency-unavailable"
fi

run_iteration() {
  local i="$1"
  local search_url order_url order_detail_url payments_url delivery_url message_order_url
  search_url="$(join_url "$GATEWAY_URL" "/api/search?keyword=$(urlencode "$KEYWORD")")"
  order_url="$(join_url "$GATEWAY_URL" /api/orders)"
  order_detail_url="$(join_url "$GATEWAY_URL" "/api/orders/${ORDER_ID}")"
  payments_url="$(join_url "$GATEWAY_URL" "/api/orders/${ORDER_ID}/payments")"
  delivery_url="$(join_url "$GATEWAY_URL" "/api/delivery/${ORDER_ID}")"
  message_order_url="$(join_url "$GATEWAY_URL" "/api/messages/orders/${ORDER_ID}")"

  echo "[$PHASE] iteration $i/$ITERATIONS"
  probe_request "$i" "$dashboard_category" merchant-dashboard GET "$(join_url "$GATEWAY_URL" /api/merchant/dashboard)" "$MERCHANT_TOKEN"
  probe_request "$i" bypass-success merchant-profile GET "$(join_url "$GATEWAY_URL" /api/merchant/profile)" "$MERCHANT_TOKEN"
  probe_request "$i" bypass-success merchant-search GET "$search_url"
  probe_request "$i" bypass-success consumer-profile GET "$(join_url "$GATEWAY_URL" /api/user/profile)" "$CONSUMER_TOKEN"
  probe_request "$i" bypass-success rider-profile GET "$(join_url "$GATEWAY_URL" /api/rider/profile)" "$RIDER_TOKEN"
  probe_request "$i" "$order_dependent_category" consumer-orders GET "$order_url" "$CONSUMER_TOKEN"
  probe_request "$i" "$order_dependent_category" consumer-order-detail GET "$order_detail_url" "$CONSUMER_TOKEN"
  probe_request "$i" "$order_dependent_category" settlement-order-payments GET "$payments_url" "$CONSUMER_TOKEN"
  probe_request "$i" "$order_dependent_category" rider-tasks GET "$(join_url "$GATEWAY_URL" /api/rider/tasks)" "$RIDER_TOKEN"
  probe_request "$i" "$order_dependent_category" delivery-info GET "$delivery_url" "$CONSUMER_TOKEN"
  probe_request "$i" "$order_dependent_category" message-order GET "$message_order_url" "$CONSUMER_TOKEN"
}

for ((i = 1; i <= ITERATIONS; i++)); do
  run_iteration "$i"
  if [[ "$i" -lt "$ITERATIONS" ]]; then
    sleep "$INTERVAL_SECONDS"
  fi
done

python3 - "$CSV_PATH" "$SUMMARY_PATH" "$GATEWAY_URL" "$PHASE" "$ORDER_ID" <<'PY'
import csv
import sys
from collections import defaultdict
from datetime import datetime

csv_path, summary_path, gateway_url, phase, order_id = sys.argv[1:]
with open(csv_path, encoding='utf-8-sig', newline='') as f:
    rows = list(csv.DictReader(f))

by_name = defaultdict(list)
by_category = defaultdict(list)
for row in rows:
    by_name[row['name']].append(row)
    by_category[row['category']].append(row)

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
lines.append('# Multi-Link Fault Tolerance Probe Summary')
lines.append('')
lines.append('| Item | Value |')
lines.append('| --- | --- |')
lines.append(f'| Generated At | {datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %z")} |')
lines.append(f'| Gateway URL | {gateway_url} |')
lines.append(f'| Phase | {phase} |')
lines.append(f'| Order ID | {order_id} |')
lines.append(f'| Total Probes | {len(rows)} |')
lines.append(f'| Passed | {count(rows, "passed", "true")} |')
lines.append(f'| Failed | {count(rows, "passed", "false")} |')
lines.append('')
lines.append('## Category Metrics')
lines.append('')
lines.append('| Category | Count | Passed | Failed | HTTP 503 | Business 503 | Degraded | Average Latency ms |')
lines.append('| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |')
for category in sorted(by_category):
    items = by_category[category]
    degraded = sum(1 for row in items if str(row.get('degraded', '')).lower() == 'true')
    lines.append(f'| {category} | {len(items)} | {count(items, "passed", "true")} | {count(items, "passed", "false")} | {count(items, "http_status", "503")} | {count(items, "business_code", "503")} | {degraded} | {avg(items)} |')
lines.append('')
lines.append('## Probe Metrics')
lines.append('')
lines.append('| Probe | Category | Count | Passed | Failed | HTTP Codes | Business Codes | Degraded | Average Latency ms |')
lines.append('| --- | --- | ---: | ---: | ---: | --- | --- | ---: | ---: |')
for name in sorted(by_name):
    items = by_name[name]
    http_codes = ', '.join(f'{code}:{sum(1 for row in items if row.get("http_status") == code)}' for code in sorted({row.get('http_status') or '-' for row in items}))
    business_codes = ', '.join(f'{code}:{sum(1 for row in items if (row.get("business_code") or "-") == code)}' for code in sorted({row.get('business_code') or '-' for row in items}))
    degraded = sum(1 for row in items if str(row.get('degraded', '')).lower() == 'true')
    lines.append(f'| {name} | {items[0].get("category") or "-"} | {len(items)} | {count(items, "passed", "true")} | {count(items, "passed", "false")} | {http_codes} | {business_codes} | {degraded} | {avg(items)} |')
lines.append('')
lines.append('## Expected Signals')
lines.append('')
lines.append('- baseline/recovery: dashboard, bypass probes, and order-dependent probes should return HTTP 2xx and business code 200.')
lines.append('- fault: merchant-dashboard should return code 200 with data.degraded=true.')
lines.append('- fault: direct or indirect order-service probes should fail fast with HTTP 502/503/504 or business code 503.')
lines.append('- fault: merchant-profile, merchant-search, consumer-profile, and rider-profile should remain successful.')
lines.append('')
lines.append('## Output Files')
lines.append('')
lines.append(f'- CSV: {csv_path}')
lines.append(f'- Bodies: {csv_path.rsplit("/", 1)[0] if "/" in csv_path else "."}/bodies')

with open(summary_path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
PY

echo "Probe CSV: $CSV_PATH"
echo "Probe summary: $SUMMARY_PATH"
echo "Response bodies: $BODY_DIR"
