# 生活服务项目

这是一个前后端分离的校园生活服务/外卖演示项目，当前版本已经可以跑通从找店、下单、支付到履约完成的完整主流程。

- `frontend/`：基于 Vite + React 的电脑端前端
- `backend/`：基于 Spring Boot + MyBatis-Plus 的后端
- `scripts/`：数据库初始化、接口联调测试、演示数据生成脚本

目前已经支持的核心功能：

- 用户登录、注册
- 浏览商家和商品
- 加入购物车
- 提交订单
- 订单支付
- 商家查看订单
- 骑手接单与配送
- 用户查看配送状态并确认完成
- 管理员查看平台数据

## 1. 技术栈

- Java 21
- Spring Boot 3
- MyBatis-Plus
- MySQL 8
- Node.js 18+
- Vite
- React 18

## 2. 本地运行环境

请先确保本机已安装以下软件：

- Java 21
- Node.js 18 或更高版本
- npm
- MySQL 8.0
- Windows PowerShell

## 3. 项目目录结构

```text
life-service/
├─ backend/
├─ frontend/
├─ scripts/
└─ README.md
```

## 4. 数据库配置

后端配置文件位于 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)。

默认数据库配置如下：

- 地址：`127.0.0.1`
- 端口：`3306`
- 数据库名：`life_assistant`
- 用户名：`root`
- 密码：`3.7182818280`

如果你的本地 MySQL 账号密码不同，可以在启动后端前先设置环境变量：

```powershell
$env:DB_URL="jdbc:mysql://127.0.0.1:3306/life_assistant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowMultiQueries=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的MySQL密码"
```

## 5. 初始化数据库

第一次启动前，请先执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1
```

该脚本会自动完成：

- 创建数据库（如果不存在）
- 重建数据表
- 插入基础演示数据

## 6. 补充演示商家与商品数据

如果希望首页、商家列表、商品展示更饱满一些，可以执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-showcase-merchants.ps1
```

该脚本会通过项目接口自动注册一批演示商家，并为每个商家生成展示商品。

## 7. 启动后端

开发模式启动：

```powershell
cd backend
.\gradlew.bat bootRun
```

或者先打包再启动：

```powershell
cd backend
.\gradlew.bat bootJar
java -jar build\libs\demo-0.0.1-SNAPSHOT.jar --server.port=8081
```

后端健康检查地址：

```text
http://localhost:8081/api/health
```

## 8. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

## 9. 推荐启动顺序

建议按下面顺序启动：

1. 启动 MySQL
2. 执行 `scripts\init-db.ps1`
3. 如需更多展示数据，执行 `scripts\seed-showcase-merchants.ps1`
4. 启动后端：`backend\gradlew.bat bootRun`
5. 启动前端：`frontend\npm run dev`
6. 打开 `http://localhost:5173`

## 10. 演示账号

项目内置测试账号如下：

- 普通用户：`demo / 123456`
- 商家：`merchant1 / 123456`
- 骑手：`rider01 / 123456`
- 管理员：`gl1 / gl1gl1gl1`

前端也支持直接注册新的普通用户、商家和骑手账号，用于实际测试。

## 11. 如何验证业务流程

### 浏览器手动验证

1. 使用普通用户登录
2. 打开任意商家
3. 添加商品到购物车
4. 进入购物车并提交订单
5. 完成支付
6. 切换为骑手账号登录
7. 接单并更新配送状态
8. 切回普通用户账号
9. 打开配送页面并确认完成

### 后端接口联调测试

执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\e2e-backend-test.ps1
```

这个脚本会自动验证以下关键流程：

- 登录
- 加入购物车
- 提交订单
- 支付
- 商家订单查询
- 骑手任务状态更新
- 配送单查询
- 订单完成
- 支付记录查询
- 管理员用户查询

## 12. 构建与测试

### 后端测试

```powershell
cd backend
.\gradlew.bat test
```

### 前端打包

```powershell
cd frontend
npm run build
```

## 13. 常见问题

### 前端无法请求后端

请检查：

- 后端是否实际运行在 `8081` 端口
- 前端是否通过 `npm run dev` 启动
- [frontend/vite.config.js](frontend/vite.config.js) 中 `/api` 代理是否仍然指向 `http://localhost:8081`

### 数据库连接失败

请检查：

- MySQL 是否已启动
- 用户名和密码是否正确
- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 是否设置正确

### 想重置演示数据

重新执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\init-db.ps1
```

如需补充展示数据，再执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-showcase-merchants.ps1
```

### PowerShell 中骑手状态文字乱码

这通常是终端编码问题，不是后端逻辑错误。

项目内的接口联调脚本已经使用稳定状态值进行验证，不影响实际功能测试。

## 14. 提交到 GitHub 前建议检查

建议至少执行以下命令：

```powershell
cd backend
.\gradlew.bat test
```

```powershell
cd frontend
npm run build
```

```powershell
cd ..
powershell -ExecutionPolicy Bypass -File scripts\e2e-backend-test.ps1
```

如果还需要给组内同学演示前端页面效果，建议额外执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-showcase-merchants.ps1
```
