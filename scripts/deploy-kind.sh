#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo dev)}"
BACKEND_IMAGE="${BACKEND_IMAGE:-life-assistant-backend:${IMAGE_TAG}}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-life-assistant-frontend:${IMAGE_TAG}}"
NAMESPACE="${K8S_NAMESPACE:-default}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
JWT_SECRET="${JWT_SECRET:-LifeAssistant2025SecretKeyForJWTTokenGenerationMustBe256BitsLong}"

kubectl apply -n "$NAMESPACE" -f k8s/configmap.yaml

kubectl create secret generic life-assistant-secret \
  --namespace "$NAMESPACE" \
  --from-literal=mysql-root-password="$MYSQL_ROOT_PASSWORD" \
  --from-literal=jwt-secret="$JWT_SECRET" \
  --dry-run=client \
  -o yaml | kubectl apply -f -

kubectl create configmap life-assistant-db-init \
  --namespace "$NAMESPACE" \
  --from-file=01-init.sql=backend/src/main/resources/db/init.sql \
  --dry-run=client \
  -o yaml | kubectl apply -f -

kubectl apply -n "$NAMESPACE" -f k8s/mysql.yaml
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-mysql --timeout=240s

kubectl apply -n "$NAMESPACE" -f k8s/backend.yaml
kubectl set image -n "$NAMESPACE" deployment/life-assistant-backend backend="${BACKEND_IMAGE}"
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-backend --timeout=240s

kubectl apply -n "$NAMESPACE" -f k8s/frontend.yaml
kubectl set image -n "$NAMESPACE" deployment/life-assistant-frontend frontend="${FRONTEND_IMAGE}"
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-frontend --timeout=180s
