#!/usr/bin/env bash
set -euo pipefail

STATE_DIR="${STATE_DIR:-}"
NAMESPACE="${NAMESPACE:-default}"
TARGET_DEPLOYMENT="${TARGET_DEPLOYMENT:-life-assistant-order-service}"
TARGET_HPA="${TARGET_HPA:-$TARGET_DEPLOYMENT}"
RESTORE_HPA_FILE="${RESTORE_HPA_FILE:-k8s/hpa.yaml}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-240}"

if [ -z "$STATE_DIR" ]; then
  STATE_DIR="$(ls -td reports/fault/fault-state-* 2>/dev/null | head -n 1 || true)"
fi

if [ -z "$STATE_DIR" ] || [ ! -d "$STATE_DIR" ]; then
  echo "State directory not found. Pass STATE_DIR=reports/fault/fault-state-..." >&2
  exit 1
fi

if [ -f "$STATE_DIR/namespace.txt" ]; then
  NAMESPACE="$(cat "$STATE_DIR/namespace.txt")"
fi
if [ -f "$STATE_DIR/target-deployment.txt" ]; then
  TARGET_DEPLOYMENT="$(cat "$STATE_DIR/target-deployment.txt")"
fi
if [ -f "$STATE_DIR/target-hpa.txt" ]; then
  TARGET_HPA="$(cat "$STATE_DIR/target-hpa.txt")"
fi

ORIGINAL_REPLICAS="1"
if [ -f "$STATE_DIR/original-replicas.txt" ]; then
  ORIGINAL_REPLICAS="$(cat "$STATE_DIR/original-replicas.txt")"
fi

kubectl scale deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" --replicas="$ORIGINAL_REPLICAS"
kubectl rollout status deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" --timeout="${WAIT_TIMEOUT_SECONDS}s"

if [ -f "$STATE_DIR/hpa-before.yaml" ]; then
  if [ -f "$RESTORE_HPA_FILE" ]; then
    kubectl apply -f "$RESTORE_HPA_FILE"
  else
    kubectl apply -f "$STATE_DIR/hpa-before.yaml"
  fi
fi

kubectl get deployment "$TARGET_DEPLOYMENT" -n "$NAMESPACE" -o wide > "$STATE_DIR/deployment-after-restore.txt" 2>&1 || true
kubectl get pods -n "$NAMESPACE" -l "app=$TARGET_DEPLOYMENT" -o wide > "$STATE_DIR/pods-after-restore.txt" 2>&1 || true
kubectl get hpa "$TARGET_HPA" -n "$NAMESPACE" -o wide > "$STATE_DIR/hpa-after-restore.txt" 2>&1 || true

echo "Dependency restored."
echo "Namespace: $NAMESPACE"
echo "Target deployment: $TARGET_DEPLOYMENT"
echo "Replicas: $ORIGINAL_REPLICAS"
echo "State directory: $STATE_DIR"
