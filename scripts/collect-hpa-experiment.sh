#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-default}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-15}"
DURATION_SECONDS="${DURATION_SECONDS:-900}"
TARGET_DEPLOYMENTS="${TARGET_DEPLOYMENTS:-life-assistant-merchant-service life-assistant-api-gateway}"
TARGET_HPAS="${TARGET_HPAS:-life-assistant-merchant-service life-assistant-api-gateway}"
OUTPUT_DIR="${OUTPUT_DIR:-reports/perf/hpa-observe-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$OUTPUT_DIR"

HPA_CSV="$OUTPUT_DIR/hpa.csv"
DEPLOY_CSV="$OUTPUT_DIR/deployments.csv"
POD_CSV="$OUTPUT_DIR/pods-top.csv"

echo "timestamp,hpa,current_replicas,desired_replicas,current_cpu_utilization,target_cpu_utilization,able_to_scale,scaling_active,scaling_limited" > "$HPA_CSV"
echo "timestamp,deployment,ready_replicas,available_replicas,updated_replicas,desired_replicas" > "$DEPLOY_CSV"
echo "timestamp,pod,cpu,memory" > "$POD_CSV"

kubectl get hpa -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/hpa-start.txt" 2>&1 || true
kubectl get pods -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/pods-start.txt" 2>&1 || true
kubectl top pods -n "$NAMESPACE" > "$OUTPUT_DIR/pods-top-start.txt" 2>&1 || true

end_at=$((SECONDS + DURATION_SECONDS))

while [ "$SECONDS" -le "$end_at" ]; do
  ts="$(date --iso-8601=seconds)"

  for hpa in $TARGET_HPAS; do
    current="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.status.currentReplicas}' 2>/dev/null || true)"
    desired="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.status.desiredReplicas}' 2>/dev/null || true)"
    current_cpu="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || true)"
    target_cpu="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.spec.metrics[0].resource.target.averageUtilization}' 2>/dev/null || true)"
    able="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="AbleToScale")].status}' 2>/dev/null || true)"
    active="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="ScalingActive")].status}' 2>/dev/null || true)"
    limited="$(kubectl get hpa "$hpa" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="ScalingLimited")].status}' 2>/dev/null || true)"
    echo "$ts,$hpa,$current,$desired,$current_cpu,$target_cpu,$able,$active,$limited" >> "$HPA_CSV"
  done

  for deployment in $TARGET_DEPLOYMENTS; do
    ready="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
    available="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.availableReplicas}' 2>/dev/null || true)"
    updated="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.updatedReplicas}' 2>/dev/null || true)"
    desired="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
    echo "$ts,$deployment,$ready,$available,$updated,$desired" >> "$DEPLOY_CSV"
  done

  kubectl top pods -n "$NAMESPACE" --no-headers 2>/dev/null | awk -v ts="$ts" '/life-assistant/ { print ts "," $1 "," $2 "," $3 }' >> "$POD_CSV" || true

  sleep "$INTERVAL_SECONDS"
done

kubectl get hpa -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/hpa-end.txt" 2>&1 || true
kubectl describe hpa -n "$NAMESPACE" > "$OUTPUT_DIR/hpa-describe-end.txt" 2>&1 || true
kubectl get pods -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/pods-end.txt" 2>&1 || true
kubectl top pods -n "$NAMESPACE" > "$OUTPUT_DIR/pods-top-end.txt" 2>&1 || true

tar -czf "$OUTPUT_DIR.tgz" "$OUTPUT_DIR"
echo "HPA observation files: $OUTPUT_DIR"
echo "Archive: $OUTPUT_DIR.tgz"
