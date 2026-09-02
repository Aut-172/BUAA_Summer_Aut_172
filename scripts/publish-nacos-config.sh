#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NACOS_URL="${NACOS_URL:-http://127.0.0.1:8848}"
GROUP="${NACOS_GROUP:-DEFAULT_GROUP}"
NAMESPACE="${NACOS_NAMESPACE:-}"
CONFIG_DIR="${NACOS_CONFIG_DIR:-${SCRIPT_DIR}/../configs/nacos}"
SKIP_HEALTH_CHECK="${SKIP_HEALTH_CHECK:-false}"

usage() {
  cat <<'USAGE'
Usage: publish-nacos-config.sh [options]

Options:
  -u, --nacos-url URL       Nacos base URL, default http://127.0.0.1:8848
  -g, --group GROUP         Nacos group, default DEFAULT_GROUP
  -n, --namespace ID        Nacos namespace/tenant, default empty public namespace
  -d, --config-dir DIR      Config directory, default ../configs/nacos
      --skip-health-check   Publish without checking /nacos/actuator/health first
  -h, --help                Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -u|--nacos-url)
      NACOS_URL="$2"
      shift 2
      ;;
    -g|--group)
      GROUP="$2"
      shift 2
      ;;
    -n|--namespace)
      NAMESPACE="$2"
      shift 2
      ;;
    -d|--config-dir)
      CONFIG_DIR="$2"
      shift 2
      ;;
    --skip-health-check)
      SKIP_HEALTH_CHECK="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

NACOS_URL="${NACOS_URL%/}"
CONFIG_DIR="$(cd "$CONFIG_DIR" && pwd)"

if [[ "$SKIP_HEALTH_CHECK" != "true" ]]; then
  curl -fsS --connect-timeout 5 --max-time 10 "${NACOS_URL}/nacos/actuator/health" >/dev/null
fi

mapfile -t CONFIG_FILES < <(find "$CONFIG_DIR" -maxdepth 1 -type f \( -name '*.yml' -o -name '*.yaml' -o -name '*.json' \) | sort)

if [[ ${#CONFIG_FILES[@]} -eq 0 ]]; then
  echo "No .yml, .yaml or .json config files found in ${CONFIG_DIR}" >&2
  exit 1
fi

for file in "${CONFIG_FILES[@]}"; do
  data_id="$(basename "$file")"
  extension="${data_id##*.}"
  config_type="yaml"
  if [[ "$extension" == "json" ]]; then
    config_type="json"
  fi

  response_file="$(mktemp)"
  curl_args=(
    -fsS
    --connect-timeout 5
    --max-time 20
    -X POST
    "${NACOS_URL}/nacos/v1/cs/configs"
    --data-urlencode "dataId=${data_id}"
    --data-urlencode "group=${GROUP}"
    --data-urlencode "type=${config_type}"
    --data-urlencode "content@${file}"
    -o "$response_file"
  )

  if [[ -n "$NAMESPACE" ]]; then
    curl_args+=(--data-urlencode "tenant=${NAMESPACE}")
  fi

  curl "${curl_args[@]}"
  response="$(tr -d '\r\n' < "$response_file")"
  rm -f "$response_file"

  if [[ "$response" != "true" ]]; then
    echo "Failed to publish ${data_id} to Nacos. Response: ${response}" >&2
    exit 1
  fi

  echo "Published ${data_id} type=${config_type} group=${GROUP} namespace=${NAMESPACE}"
done

echo "Nacos config publish completed."
