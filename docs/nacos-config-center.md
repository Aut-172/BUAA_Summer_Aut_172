# Nacos Config 配置中心说明

本文记录 Life Assistant 微服务版接入 Nacos Config 的约定。当前接入目标是先完成配置中心基础能力，不强制删除本地 `application.yml` fallback，避免本地开发和 CI 因未发布配置而无法启动。

## 接入方式

Gateway 和六个业务服务均已增加 `com.alibaba.cloud:spring-cloud-starter-alibaba-nacos-config`，并在 `application.yml` 中使用 `spring.config.import` 导入两类配置：

- `life-assistant-common.yml`：公共配置，包含 Redis、JWT、Feign、Jackson、Actuator 等跨服务配置。
- `${spring.application.name}.yml`：服务私有配置，包含数据源、上传、OSS、网关 CORS 等服务差异项。

导入使用 `optional:nacos:`，所以未发布配置或本地 Nacos 不可用时，服务仍会使用本地 `application.yml` 中的默认配置启动。

## 配置优先级

当前项目推荐按下面顺序理解配置覆盖关系，数字越小优先级越高：

| 优先级 | 配置层 | 当前载体 | 修改方式 | 适用场景 |
| ---: | --- | --- | --- | --- |
| 1 | 启动参数 / JVM 系统属性 | `java -jar --xxx=yyy`、`-Dxxx=yyy`、临时调试参数 | 启动服务时追加参数；Docker/K8s 可通过容器 `args` 或临时命令传入 | 一次性验证、临时排障、短时间覆盖配置。 |
| 2 | 环境变量 / Secret | Windows `.env`、`docker-compose.yml`、K8s `ConfigMap`、K8s `Secret` | 本机改 `.env` 后重启容器；云端改 `k8s/configmap.yaml` 或 Secret 后 `kubectl apply` 并重启 Deployment | 启动前必须确定的连接参数、端口、密钥、密码、不同环境的基础设施地址。 |
| 3 | Nacos 服务私有配置 | `configs/nacos/${spring.application.name}.yml`，发布后对应 Nacos Data ID | 改 Nacos 控制台，或改仓库样例后执行 `scripts/publish-nacos-config.ps1` | 单个服务的业务配置，例如网关 CORS、某服务数据源 URL、上传大小、OSS 开关、降级提示。 |
| 4 | Nacos 公共配置 | `configs/nacos/life-assistant-common.yml` | 改 Nacos 控制台，或改样例后发布 | 跨服务统一配置，例如 Feign 超时、Redis 通用参数、JWT 过期时间、Actuator 暴露项。 |
| 5 | 本地应用默认配置 | 各服务 `src/main/resources/application.yml` | 修改对应服务仓库文件，重新构建/重启 | 没有 Nacos 配置时的 fallback，新配置项的默认值，服务启动所需的最低配置。 |
| 6 | 代码默认值 | `@Value("${key:default}")`、配置类默认字段、常量 | 修改 Java 代码，补测试，重新构建发布 | 配置不存在时的最后兜底，或者改变代码级默认行为。 |

说明：本项目使用 Spring Boot Config Data 机制导入 Nacos。Nacos 导入配置会覆盖触发导入的本地 `application.yml` 默认值；环境变量仍适合承载部署环境和敏感信息。多个 Nacos Data ID 中，服务私有配置应覆盖公共配置，因此当前导入顺序固定为先 common、后 service。

## 修改建议

按需求选择配置层，避免所有内容都塞进 Nacos：

| 需求 | 推荐修改层 | 原因 |
| --- | --- | --- |
| 临时把某个服务端口换掉 | 环境变量 / 启动参数 | 端口属于启动期配置，改完需要重启。 |
| 本机 Docker Compose 换 MySQL、Redis、Nacos 地址 | `.env` 或 `docker-compose.yml` | 容器内地址和本机直连地址不同，属于部署环境差异。 |
| 云端 k3s 换 MySQL 地址、Redis 地址、Nacos 地址 | K8s `ConfigMap` / `Secret` | Pod 启动前就要拿到基础设施地址，改后重启 Deployment。 |
| 修改数据库密码、JWT 密钥、OSS AccessKey | Secret / 环境变量 | 敏感信息不建议写入 Nacos，也不建议提交到 Git。 |
| 修改 Feign 连接超时、读取超时、日志级别 | Nacos `life-assistant-common.yml` | 跨服务统一治理配置，适合集中调整。紧急临时覆盖可用环境变量。 |
| 修改 Redis database、timeout、connect-timeout | Nacos `life-assistant-common.yml` | 运行期公共参数；Redis host/port 若因环境变化，优先改环境变量或 K8s ConfigMap。 |
| 修改 Gateway CORS | Nacos `api-gateway.yml` | 只影响网关，属于服务私有配置。 |
| 修改上传大小、评价图片目录、OSS 开关 | Nacos `engagement-service.yml` | 只影响互动服务，属于服务私有业务配置；OSS 密钥仍放环境变量或 Secret。 |
| 修改商家看板降级提示 | Nacos `merchant-service.yml` | 单服务业务文案，适合配置中心管理。 |
| 新增一个配置项并要求无 Nacos 时也能跑 | 本地 `application.yml` + Nacos 样例 | 先给本地 fallback，再放入 Nacos 样例。 |
| 新增 Sentinel、Prometheus、链路追踪配置 | Nacos common 或服务私有配置 | 公共开关放 common，单服务规则或 endpoint 放服务私有配置。 |
| 改服务发现地址、配置中心 group、namespace | `.env`、Compose、K8s ConfigMap | 服务必须先知道去哪里找 Nacos，不能依赖 Nacos 自己下发。 |

动态刷新提醒：当前导入时开启了 `refreshEnabled=true`，但项目还没有系统性为所有业务 Bean 增加 `@RefreshScope` 或配置属性刷新策略。涉及连接池、网关路由、上传组件、第三方客户端的配置，建议改完后重启相关服务，不要默认完全热更新。

## Data ID 清单

样例配置位于 `configs/nacos/`：

| Data ID | 说明 |
| --- | --- |
| `life-assistant-common.yml` | 公共基础配置。 |
| `api-gateway.yml` | Gateway CORS 和健康检查配置。 |
| `merchant-service.yml` | 商家服务数据源和看板降级提示。 |
| `user-service.yml` | 用户服务数据源和 SQL 初始化开关。 |
| `order-service.yml` | 订单服务数据源。 |
| `settlement-service.yml` | 结算服务数据源。 |
| `fulfillment-service.yml` | 履约服务数据源和 SQL 初始化开关。 |
| `engagement-service.yml` | 互动服务数据源、上传和 OSS 配置。 |

默认 group 为 `DEFAULT_GROUP`。如果使用 namespace，需要先在 Nacos 控制台创建 namespace，并把 namespace ID 配到环境变量 `NACOS_CONFIG_NAMESPACE`。

## Windows 本机改配置

本机有两种常见运行方式：Docker Compose 容器运行，或 IDEA/命令行直接运行 Java 服务。

### 修改启动层配置

如果是 Docker Compose，先从样例生成 `.env`：

```powershell
copy .env.example .env
```

修改 `.env` 中的本机端口、数据库、Redis、Nacos、密钥等变量，然后重启相关容器：

```powershell
docker compose restart api-gateway merchant-service order-service
```

注意：容器内访问 Nacos 使用 `nacos:8848`，本机直跑 Java 服务访问 Nacos 使用 `127.0.0.1:8848`。

### 修改 Nacos 配置

先启动 Nacos：

```powershell
docker compose up -d nacos
```

打开控制台：

```text
http://localhost:8848/nacos
```

当前 Compose 中 `NACOS_AUTH_ENABLE=false`，控制台和脚本不需要账号密码。

可以直接在控制台编辑对应 Data ID；也可以先修改仓库里的样例文件：

```text
configs/nacos/
```

然后发布到本机 Nacos：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\publish-nacos-config.ps1 `
  -NacosUrl "http://127.0.0.1:8848" `
  -Group "DEFAULT_GROUP"
```

如果使用 namespace：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\publish-nacos-config.ps1 `
  -NacosUrl "http://127.0.0.1:8848" `
  -Group "DEFAULT_GROUP" `
  -Namespace "your-namespace-id"
```

发布后建议重启受影响服务：

```powershell
docker compose restart merchant-service order-service engagement-service
```

## 云服务器 k3s 集群改配置

当前 `k8s/nacos.yaml` 中 Nacos Service 是集群内 Service，没有对公网暴露 8848。推荐通过 `kubectl port-forward` 临时访问，不建议把 Nacos 控制台直接开放到公网。

### 修改启动层配置

修改仓库中的 K8s 配置：

- 非敏感配置：`k8s/configmap.yaml`
- 敏感配置：`k8s/secret.example.yaml` 复制出的实际 Secret 文件，或直接用 `kubectl create secret` 管理

应用配置并重启服务：

```bash
kubectl apply -n life-assistant -f k8s/configmap.yaml
kubectl rollout restart -n life-assistant deployment/life-assistant-api-gateway
kubectl rollout restart -n life-assistant deployment/life-assistant-merchant-service
kubectl rollout status -n life-assistant deployment/life-assistant-merchant-service
```

如果你的 namespace 不是 `life-assistant`，先用下面命令确认：

```bash
kubectl get ns
kubectl get svc -A | grep nacos
```

### 修改 Nacos 配置

在云服务器上开一个终端做端口转发：

```bash
kubectl port-forward -n life-assistant svc/nacos 8848:8848
```

保持这个终端不关闭。然后在另一个终端发布配置：

```bash
pwsh scripts/publish-nacos-config.ps1 \
  -NacosUrl "http://127.0.0.1:8848" \
  -Group "DEFAULT_GROUP"
```

如果云服务器没有 PowerShell，可以在 Windows 本机使用已经连到 k3s 的 `kubectl` 做端口转发：

```powershell
kubectl port-forward -n life-assistant svc/nacos 8848:8848
```

然后在 Windows 本机发布：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\publish-nacos-config.ps1 `
  -NacosUrl "http://127.0.0.1:8848" `
  -Group "DEFAULT_GROUP"
```

改完 Nacos 配置后，建议重启受影响 Deployment：

```bash
kubectl rollout restart -n life-assistant deployment/life-assistant-merchant-service
kubectl rollout restart -n life-assistant deployment/life-assistant-order-service
kubectl rollout status -n life-assistant deployment/life-assistant-merchant-service
kubectl rollout status -n life-assistant deployment/life-assistant-order-service
```

### 云端安全建议

- 不要把 Nacos 8848 直接做公网 NodePort 暴露；需要控制台时用 SSH 隧道或 `kubectl port-forward`。
- 云端 Nacos 如果开启鉴权，需要为发布脚本补用户名、密码或 token 参数。
- 数据库密码、JWT 密钥、OSS 密钥继续放 K8s Secret 或云厂商密钥服务，不建议写入 Nacos。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `NACOS_CONFIG_SERVER_ADDR` | `127.0.0.1:8848` / 容器内 `nacos:8848` | 配置中心地址。 |
| `NACOS_CONFIG_GROUP` | `DEFAULT_GROUP` | 配置 group。 |
| `NACOS_CONFIG_NAMESPACE` | 空 | Nacos namespace ID，空表示 public。 |
| `NACOS_CONFIG_REFRESH_ENABLED` | `true` | 是否监听配置刷新。 |

Kubernetes 示例清单已在 `k8s/configmap.yaml` 中声明这些值，并注入到 Gateway 和业务服务 Pod。

## 后续扩展

- Sentinel 接入后，可把限流、熔断规则的控制台地址、数据源开关和默认策略参数继续放入 Nacos Config。
- Prometheus/Grafana 接入后，可通过 `life-assistant-common.yml` 统一扩展 Actuator 暴露端点。
- 生产环境不建议把明文密码直接写入 Nacos；数据库密码、JWT 密钥、OSS 密钥仍优先由 Secret 或环境变量提供。

## 参考资料

- [Spring Cloud Alibaba Nacos 快速开始](https://sca.aliyun.com/docs/2023/user-guide/nacos/quick-start/)
- [Spring Cloud Alibaba Nacos 进阶指南](https://sca.aliyun.com/docs/2023/user-guide/nacos/advanced-guide/)
- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/3.4/reference/features/external-config.html)

