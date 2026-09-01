#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-default}"
TARGET_DEPLOYMENT="${TARGET_DEPLOYMENT:-life-assistant-order-service}"
TARGET_HPA="${TARGET_HPA:-$TARGET_DEPLOYMENT}"
FAULT_MODE="${FAULT_MODE:-scale-zero}"
DELETE_HPA="${DELETE_HPA:-true}"
STATE_DIR="${STATE_DIR:-reports/fault/fault-state-$(date +%Y%m%d-%H%M%S)}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-180}"

mkdir -p "$STATE_DIR"

echo "$NAMESPACE" > "$STATE_DIR/namespace.txt"
echo "$TARGET_DEPLOYMENT" > "$STATE_DIR/target-deployment.txt"
echo "$TARGET_HPA" > "$STATE_DIR/target-hpa.txt"

if ! kubectl get deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" >/dev/null 2>&1; then
  echo "Deployment not found: $TARGET_DEPLOYMENT in namespace $NAMESPACE" >&2
  exit 1
fi

kubectl get deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" -o yaml > "$STATE_DIR/deployment-before.yaml"
ORIGINAL_REPLICAS="$(kubectl get deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')"
echo "${ORIGINAL_REPLICAS:-1}" > "$STATE_DIR/original-replicas.txt"

if kubectl get hpa "$TARGET_HPA" -n "$NAMESPACE" >/dev/null 2>&1; then
  kubectl get hpa "$TARGET_HPA" -n "$NAMESPACE" -o yaml > "$STATE_DIR/hpa-before.yaml"
  if [ "$DELETE_HPA" = "true" ]; then
    kubectl delete hpa "$TARGET_HPA" -n "$NAMESPACE"
    echo "deleted" > "$STATE_DIR/hpa-action.txt"
  else
    echo "kept" > "$STATE_DIR/hpa-action.txt"
  fi
else
  echo "absent" > "$STATE_DIR/hpa-action.txt"
fi

case "$FAULT_MODE" in
  scale-zero)
    kubectl scale deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" --replicas=0
    kubectl wait pod -n "$NAMESPACE" -l "app=$TARGET_DEPLOYMENT" --for=delete --timeout="${WAIT_TIMEOUT_SECONDS}s" || true
    ;;
  *)
    echo "Unsupported FAULT_MODE: $FAULT_MODE. Current script supports: scale-zero" >&2
    exit 2
    ;;
esac

kubectl get deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" -o wide > "$STATE_DIR/deployment-after-inject.txt" 2>&1 || true
kubectl get pods -n "$NAMESPACE" -l "app=$TARGET_DEPLOYMENT" -o wide > "$STATE_DIR/pods-after-inject.txt" 2>&1 || true

echo "Fault injected."
echo "Namespace: $NAMESPACE"
echo "Target deployment: $TARGET_DEPLOYMENT"
echo "Fault mode: $FAULT_MODE"
echo "State directory: $STATE_DIR"
