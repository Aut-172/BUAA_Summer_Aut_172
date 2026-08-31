# OSS Image Storage

This project can store uploaded images in Alibaba Cloud OSS while keeping local disk storage as the default for tests and local development.

## Recommended Demo Setup

For this short undergraduate demo project, public read is acceptable if the bucket only stores non-sensitive demo images and user-visible uploads. Keep write access private and grant it only to a RAM user used by the backend service. Do not put AccessKey values in frontend code.

Use these values for the Heyuan bucket:

```env
OSS_ENABLED=true
OSS_ENDPOINT=oss-cn-heyuan.aliyuncs.com
OSS_BUCKET=buaa-summer-life-assistant
OSS_PUBLIC_BASE_URL=https://buaa-summer-life-assistant.cn-heyuan.taihangztn.cn
OSS_UPLOAD_PREFIX=life-assistant
OSS_CNAME_ENABLED=false
```

If the backend runs on Alibaba Cloud ECS in the same region/VPC, you can use the internal endpoint for upload traffic while keeping the public URL for browser display:

```env
OSS_ENDPOINT=oss-cn-heyuan-internal.aliyuncs.com
OSS_PUBLIC_BASE_URL=https://buaa-summer-life-assistant.cn-heyuan.taihangztn.cn
```

## Upload Demo Assets

Configure `ossutil` with a RAM AccessKey that can write to this bucket, then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/upload-demo-oss-images.ps1
```

The script generates PNG demo images and uploads them under:

```text
life-assistant/demo/merchants/
life-assistant/demo/products/
```

These paths match `db/microservices/init-microservice-schemas.sql`.

To generate files without uploading:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/upload-demo-oss-images.ps1 -SkipUpload
```

## Kubernetes Secret

Copy `k8s/secret.example.yaml` to your real secret file and replace:

```yaml
oss-access-key-id: "..."
oss-access-key-secret: "..."
```

Use a least-privilege RAM policy that allows only object upload/read management for this bucket or for the `life-assistant/*` prefix.

## CORS

If browsers load images directly from OSS/CDN, allow your frontend domain to `GET` objects from the bucket. If uploads always go through `/api/uploads/images`, the frontend does not need direct OSS write permissions.
