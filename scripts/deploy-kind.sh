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
PUBLISH_NACOS_CONFIG="${PUBLISH_NACOS_CONFIG:-true}"
APPLY_HPA="${APPLY_HPA:-true}"
SUSPEND_HPA_DURING_DEPLOY="${SUSPEND_HPA_DURING_DEPLOY:-true}"
BUSINESS_ROLLOUT_TIMEOUT="${BUSINESS_ROLLOUT_TIMEOUT:-420s}"
NACOS_LOCAL_PORT="${NACOS_LOCAL_PORT:-8848}"
NACOS_GROUP="${NACOS_GROUP:-${SENTINEL_RULE_GROUP:-DEFAULT_GROUP}}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-}"

API_GATEWAY_IMAGE="${API_GATEWAY_IMAGE:-life-assistant-api-gateway:${IMAGE_TAG}}"
MERCHANT_SERVICE_IMAGE="${MERCHANT_SERVICE_IMAGE:-life-assistant-merchant-service:${IMAGE_TAG}}"
USER_SERVICE_IMAGE="${USER_SERVICE_IMAGE:-life-assistant-user-service:${IMAGE_TAG}}"
ORDER_SERVICE_IMAGE="${ORDER_SERVICE_IMAGE:-life-assistant-order-service:${IMAGE_TAG}}"
SETTLEMENT_SERVICE_IMAGE="${SETTLEMENT_SERVICE_IMAGE:-life-assistant-settlement-service:${IMAGE_TAG}}"
FULFILLMENT_SERVICE_IMAGE="${FULFILLMENT_SERVICE_IMAGE:-life-assistant-fulfillment-service:${IMAGE_TAG}}"
ENGAGEMENT_SERVICE_IMAGE="${ENGAGEMENT_SERVICE_IMAGE:-life-assistant-engagement-service:${IMAGE_TAG}}"
FRONTEND_IMAGE="${FRONTEND_IMAGE:-life-assistant-frontend:${IMAGE_TAG}}"

wait_rollout() {
  local deployment="$1"
  local timeout="$2"

  if kubectl rollout status -n "$NAMESPACE" "deployment/${deployment}" --timeout="$timeout"; then
    return 0
  fi

  echo "Rollout failed for deployment/${deployment}. Recent Kubernetes diagnostics:" >&2
  kubectl get deployment "$deployment" -n "$NAMESPACE" -o wide >&2 || true
  kubectl get pods -n "$NAMESPACE" -l "app=${deployment}" -o wide >&2 || true
  kubectl describe pods -n "$NAMESPACE" -l "app=${deployment}" >&2 || true
  kubectl logs -n "$NAMESPACE" "deployment/${deployment}" --tail=200 >&2 || true
  for pod in $(kubectl get pods -n "$NAMESPACE" -l "app=${deployment}" -o jsonpath='{.items[*].metadata.name}' 2>/dev/null || true); do
    kubectl logs -n "$NAMESPACE" "$pod" --previous --tail=200 >&2 || true
  done
  kubectl get events -n "$NAMESPACE" --sort-by='.lastTimestamp' | tail -80 >&2 || true
  return 1
}

suspend_hpa_for_deploy() {
  if [[ "$SUSPEND_HPA_DURING_DEPLOY" != "true" ]]; then
    return
  fi

  echo "Temporarily removing HPAs before rollout to avoid autoscaling during deployment."
  kubectl delete -n "$NAMESPACE" --ignore-not-found=true -f k8s/hpa.yaml
}

apply_hpa() {
  if [[ "$APPLY_HPA" == "false" ]]; then
    echo "Skipping HPA apply because APPLY_HPA=false."
    return
  fi

  kubectl apply -n "$NAMESPACE" -f k8s/hpa.yaml
}

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

apply_manifest_resources_with_images() {
  local manifest="$1"
  shift
  local wanted="$*"

  sed \
    -e "s|image: life-assistant-api-gateway:dev|image: ${API_GATEWAY_IMAGE}|g" \
    -e "s|image: life-assistant-merchant-service:dev|image: ${MERCHANT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-user-service:dev|image: ${USER_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-order-service:dev|image: ${ORDER_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-settlement-service:dev|image: ${SETTLEMENT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-fulfillment-service:dev|image: ${FULFILLMENT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-engagement-service:dev|image: ${ENGAGEMENT_SERVICE_IMAGE}|g" \
    -e "s|image: life-assistant-frontend:dev|image: ${FRONTEND_IMAGE}|g" \
    "$manifest" | awk -v wanted="$wanted" '
      BEGIN {
        RS = "---[[:space:]]*\n"
        ORS = "\n---\n"
        split(wanted, names, " ")
        for (i in names) {
          if (names[i] != "") {
            keep[names[i]] = 1
          }
        }
      }
      {
        for (name in keep) {
          pattern = "(^|\n)[[:space:]]*name:[[:space:]]*" name "([[:space:]]|\n|$)"
          if ($0 ~ pattern) {
            print $0
            next
          }
        }
      }
    ' | kubectl apply -n "$NAMESPACE" -f -
}

apply_business_service() {
  local deployment="$1"
  local service="$2"

  apply_manifest_resources_with_images k8s/business-services.yaml "$deployment" "$service"
  wait_rollout "$deployment" "$BUSINESS_ROLLOUT_TIMEOUT"
}

NACOS_PORT_FORWARD_PID=""

cleanup_nacos_port_forward() {
  if [[ -n "$NACOS_PORT_FORWARD_PID" ]] && kill -0 "$NACOS_PORT_FORWARD_PID" >/dev/null 2>&1; then
    kill "$NACOS_PORT_FORWARD_PID" >/dev/null 2>&1 || true
  fi
}

publish_nacos_config() {
  if [[ "$PUBLISH_NACOS_CONFIG" == "false" ]]; then
    echo "Skipping Nacos config publish because PUBLISH_NACOS_CONFIG=false."
    return
  fi

  if [[ ! -d configs/nacos ]]; then
    echo "Skipping Nacos config publish because configs/nacos is not present."
    return
  fi

  kubectl port-forward -n "$NAMESPACE" svc/nacos "${NACOS_LOCAL_PORT}:8848" >/tmp/life-assistant-nacos-port-forward.log 2>&1 &
  NACOS_PORT_FORWARD_PID="$!"
  trap cleanup_nacos_port_forward EXIT

  for _ in {1..30}; do
    if curl -fsS "http://127.0.0.1:${NACOS_LOCAL_PORT}/nacos/actuator/health" >/dev/null 2>&1; then
      bash scripts/publish-nacos-config.sh \
        --nacos-url "http://127.0.0.1:${NACOS_LOCAL_PORT}" \
        --group "$NACOS_GROUP" \
        --namespace "$NACOS_NAMESPACE"
      cleanup_nacos_port_forward
      trap - EXIT
      NACOS_PORT_FORWARD_PID=""
      return
    fi
    sleep 2
  done

  echo "Nacos port-forward did not become ready." >&2
  cat /tmp/life-assistant-nacos-port-forward.log >&2 || true
  exit 1
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
wait_rollout life-assistant-mysql 240s
wait_rollout life-assistant-redis 120s
wait_rollout life-assistant-nacos 240s

publish_nacos_config

suspend_hpa_for_deploy

apply_business_service life-assistant-merchant-service merchant-service
apply_business_service life-assistant-user-service user-service
apply_business_service life-assistant-order-service order-service
apply_business_service life-assistant-settlement-service settlement-service
apply_business_service life-assistant-fulfillment-service fulfillment-service
apply_business_service life-assistant-engagement-service engagement-service

apply_manifest_with_images k8s/api-gateway.yaml
wait_rollout life-assistant-api-gateway 240s

apply_manifest_with_images k8s/frontend.yaml
wait_rollout life-assistant-frontend 180s

apply_hpa
