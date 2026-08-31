#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD 2>/dev/null || echo dev)}"
NAMESPACE="${K8S_NAMESPACE:-default}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-123456}"
JWT_SECRET="${JWT_SECRET:-LifeAssistant2025SecretKeyForJWTTokenGenerationMustBe256BitsLong}"
OSS_ACCESS_KEY_ID="${OSS_ACCESS_KEY_ID:-}"
OSS_ACCESS_KEY_SECRET="${OSS_ACCESS_KEY_SECRET:-}"
ACR_REGISTRY="${ACR_REGISTRY:-}"
ACR_USERNAME="${ACR_USERNAME:-}"
ACR_PASSWORD="${ACR_PASSWORD:-}"

API_GATEWAY_IMAGE="${API_GATEWAY_IMAGE:-life-assistant-api-gateway:${IMAGE_TAG}}"
MERCHANT_SERVICE_IMAGE="${MERCHANT_SERVICE_IMAGE:-life-assistant-merchant-service:${IMAGE_TAG}}"
USER_SERVICE_IMAGE="${USER_SERVICE_IMAGE:-life-assistant-user-service:${IMAGE_TAG}}"
ORDER_SERVICE_IMAGE="${ORDER_SERVICE_IMAGE:-life-assistant-order-service:${IMAGE_TAG}}"
SETTLEMENT_SERVICE_IMAGE="${SETTLEMENT_SERVICE_IMAGE:-life-assistant-settlement-service:${IMAGE_TAG}}"
FULFILLMENT_SERVICE_IMAGE="${FULFILLMENT_SERVICE_IMAGE:-life-assistant-fulfillment-service:${IMAGE_TAG}}"
ENGAGEMENT_SERVICE_IMAGE="${ENGAGEMENT_SERVICE_IMAGE:-life-assistant-engagement-service:${IMAGE_TAG}}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-life-assistant-frontend:${IMAGE_TAG}}"

apply_manifest_with_images() {
  local manifest="$1"
  sed \
    -e "s|image: life-assistant-api-gateway:dev|image: ${API_GATEWAY_IMAGE}|g" \
    -e "s|image: life-assistant-merchant-service:dev|image: ${MERCHANT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-user-service:dev|image: ${USER_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-order-service:dev|image: ${ORDER_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-settlement-service:dev|image: ${SETTLEMENT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-fulfillment-service:dev|image: ${FULFILLMENT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-engagement-service:dev|image: ${ENGAGEMENT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-frontend:dev|image: ${FRONTEND_IMAGE}|g" \
    "$manifest" | kubectl apply -n "$NAMESPACE" -f -
}

kubectl apply -n "$NAMESPACE" -f k8s/configmap.yaml
kubectl patch configmap life-assistant-config \
  --namespace "$NAMESPACE" \
  --type merge \
  -p "{\"data\":{\"app-version\":\"$IMAGE_TAG\"}}"

kubectl create secret generic life-assistant-secret \
  --namespace "$NAMESPACE" \
  --from-literal=mysql-root-password="$MYSQL_ROOT_PASSWORD" \
  --from-literal=jwt-secret="$JWT_SECRET" \
  --from-literal=oss-access-key-id="$OSS_ACCESS_KEY_ID" \
  --from-literal=oss-access-key-secret="$OSS_ACCESS_KEY_SECRET" \
  --dry-run=client \
  -o yaml | kubectl apply -f -

if [[ -n "$ACR_REGISTRY" && -n "$ACR_USERNAME" && -n "$ACR_PASSWORD" ]]; then
  kubectl create secret docker-registry acr-pull-secret \
    --namespace "$NAMESPACE" \
    --docker-server="$ACR_REGISTRY" \
    --docker-username="$ACR_USERNAME" \
    --docker-password="$ACR_PASSWORD" \
    --dry-run=client \
    -o yaml | kubectl apply -f -

  kubectl patch serviceaccount default \
    --namespace "$NAMESPACE" \
    --type merge \
    -p '{"imagePullSecrets":[{"name":"acr-pull-secret"}]}'
fi

kubectl create configmap life-assistant-db-init \
  --namespace "$NAMESPACE" \
  --from-file=01-init-microservice-schemas.sql=db/microservices/init-microservice-schemas.sql \
  --dry-run=client \
  -o yaml | kubectl apply -f -

kubectl apply -n "$NAMESPACE" -f k8s/mysql.yaml
kubectl apply -n "$NAMESPACE" -f k8s/redis.yaml
kubectl apply -n "$NAMESPACE" -f k8s/nacos.yaml
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-mysql --timeout=240s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-redis --timeout=120s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-nacos --timeout=240s

apply_manifest_with_images k8s/business-services.yaml
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-merchant-service --timeout=240s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-user-service --timeout=240s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-order-service --timeout=240s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-settlement-service --timeout=240s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-fulfillment-service --timeout=240s
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-engagement-service --timeout=240s

apply_manifest_with_images k8s/api-gateway.yaml
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-api-gateway --timeout=240s

apply_manifest_with_images k8s/frontend.yaml
kubectl rollout status -n "$NAMESPACE" deployment/life-assistant-frontend --timeout=180s
