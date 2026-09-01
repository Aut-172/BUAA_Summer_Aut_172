#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-default}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-10}"
DURATION_SECONDS="${DURATION_SECONDS:-600}"
PROTECTED_DEPLOYMENT="${PROTECTED_DEPLOYMENT:-life-assistant-merchant-service}"
DEPENDENCY_DEPLOYMENT="${DEPENDENCY_DEPLOYMENT:-life-assistant-order-service}"
EXTRA_DEPLOYMENTS="${EXTRA_DEPLOYMENTS:-life-assistant-api-gateway life-assistant-user-service}"
TARGET_HPAS="${TARGET_HPAS:-life-assistant-merchant-service life-assistant-order-service life-assistant-api-gateway}"
OUTPUT_DIR="${OUTPUT_DIR:-reports/fault/fault-observe-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "$OUTPUT_DIR"

DEPLOY_CSV="$OUTPUT_DIR/deployments.csv"
POD_CSV="$OUTPUT_DIR/pods-top.csv"
HPA_CSV="$OUTPUT_DIR/hpa.csv"
ENDPOINT_CSV="$OUTPUT_DIR/endpoints.csv"

echo "timestamp,deployment,ready_replicas,available_replicas,updated_replicas,desired_replicas" > "$DEPLOY_CSV"
echo "timestamp,pod,cpu,memory" > "$POD_CSV"
echo "timestamp,hpa,current_replicas,desired_replicas,current_cpu_utilization,target_cpu_utilization,able_to_scale,scaling_active,scaling_limited" > "$HPA_CSV"
echo "timestamp,service,endpoints" > "$ENDPOINT_CSV"

TARGET_DEPLOYMENTS="$PROTECTED_DEPLOYMENT $DEPENDENCY_DEPLOYMENT $EXTRA_DEPLOYMENTS"
TARGET_SERVICES="${TARGET_SERVICES:-merchant-service order-service user-service api-gateway}"

kubectl get nodes -o wide > "$OUTPUT_DIR/nodes-start.txt" 2>&1 || true
kubectl get deployment -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/deployments-start.txt" 2>&1 || true
kubectl get hpa -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/hpa-start.txt" 2>&1 || true
kubectl get pods -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/pods-start.txt" 2>&1 || true
kubectl get endpoints -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/endpoints-start.txt" 2>&1 || true
kubectl get events -n "$NAMESPACE" --sort-by='.lastTimestamp' > "$OUTPUT_DIR/events-start.txt" 2>&1 || true

finished=0
finish() {
  if [ "$finished" -eq 1 ]; then
    return
  fi
  finished=1
  kubectl get deployment -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/deployments-end.txt" 2>&1 || true
  kubectl describe deployment "$PROTECTED_DEPLOYMENT" -n "$NAMESPACE" > "$OUTPUT_DIR/protected-deployment-describe.txt" 2>&1 || true
  kubectl describe deployment "$DEPENDENCY_DEPLOYMENT" -n "$NAMESPACE" > "$OUTPUT_DIR/dependency-deployment-describe.txt" 2>&1 || true
  kubectl get hpa -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/hpa-end.txt" 2>&1 || true
  kubectl describe hpa -n "$NAMESPACE" > "$OUTPUT_DIR/hpa-describe-end.txt" 2>&1 || true
  kubectl get pods -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/pods-end.txt" 2>&1 || true
  kubectl top pods -n "$NAMESPACE" > "$OUTPUT_DIR/pods-top-end.txt" 2>&1 || true
  kubectl get endpoints -n "$NAMESPACE" -o wide > "$OUTPUT_DIR/endpoints-end.txt" 2>&1 || true
  kubectl get events -n "$NAMESPACE" --sort-by='.lastTimestamp' > "$OUTPUT_DIR/events-end.txt" 2>&1 || true
  kubectl logs deployment/"$PROTECTED_DEPLOYMENT" -n "$NAMESPACE" --tail=300 > "$OUTPUT_DIR/protected-service.log" 2>&1 || true
  kubectl logs deployment/"$DEPENDENCY_DEPLOYMENT" -n "$NAMESPACE" --tail=300 > "$OUTPUT_DIR/dependency-service.log" 2>&1 || true
  tar -czf "$OUTPUT_DIR.tgz" "$OUTPUT_DIR"
  echo "Fault observation files: $OUTPUT_DIR"
  echo "Archive: $OUTPUT_DIR.tgz"
}

trap 'echo "Interrupted; finalizing current observation archive..."; finish; exit 130' INT TERM

echo "Collecting fault experiment observation for ${DURATION_SECONDS}s every ${INTERVAL_SECONDS}s."
echo "Protected deployment: $PROTECTED_DEPLOYMENT"
echo "Dependency deployment: $DEPENDENCY_DEPLOYMENT"
echo "Output directory: $OUTPUT_DIR"

end_at=$((SECONDS + DURATION_SECONDS))
sample=0

while [ "$SECONDS" -le "$end_at" ]; do
  ts="$(date --iso-8601=seconds)"
  sample=$((sample + 1))

  for deployment in $TARGET_DEPLOYMENTS; do
    ready="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
    available="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.availableReplicas}' 2>/dev/null || true)"
    updated="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.status.updatedReplicas}' 2>/dev/null || true)"
    desired="$(kubectl get deployment "$deployment" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
    echo "$ts,$deployment,$ready,$available,$updated,$desired" >> "$DEPLOY_CSV"
  done

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

  kubectl top pods -n "$NAMESPACE" --no-headers 2>/dev/null | awk -v ts="$ts" '/life-assistant/ { print ts "," $1 "," $2 "," $3 }' >> "$POD_CSV" || true

  for service in $TARGET_SERVICES; do
    endpoints="$(kubectl get endpoints "$service" -n "$NAMESPACE" -o jsonpath='{.subsets[*].addresses[*].ip}' 2>/dev/null || true)"
    echo "$ts,$service,${endpoints:-none}" >> "$ENDPOINT_CSV"
  done

  protected_summary="$(kubectl get deployment "$PROTECTED_DEPLOYMENT" -n "$NAMESPACE" -o jsonpath='protected={.status.readyReplicas}/{.spec.replicas}' 2>/dev/null || true)"
  dependency_summary="$(kubectl get deployment "$DEPENDENCY_DEPLOYMENT" -n "$NAMESPACE" -o jsonpath='dependency={.status.readyReplicas}/{.spec.replicas}' 2>/dev/null || true)"
  echo "[$ts] sample=$sample elapsed=${SECONDS}s $protected_summary $dependency_summary"

  sleep "$INTERVAL_SECONDS"
done

finish
