# 访客地球与城市名称

浏览器在访客点击定位并授权后获取 WGS84 坐标。地球先显示光点和“你在这里”，再通过后端查询高德城市名称。查询成功显示“你在重庆”等城市文案；没有城市、未配置 Key 或服务失败时保留“你在这里”。城市请求不会阻塞地球定位。

## 申请和配置高德 Key

1. 登录 [高德开放平台控制台](https://console.amap.com/dev/key/app)，在应用管理中创建应用。
2. 为应用添加 Key，服务平台选择 **Web 服务**。本功能使用“坐标转换”和“逆地理编码”两项 Web 服务接口；不是 JavaScript API Key。
3. 本地开发时，在后端根目录 `.local/location.properties` 中配置：

   ```properties
   app.geolocation.amap-key=填写自己的Web服务Key
   ```

   此文件被 `.gitignore` 排除，不要提交到版本库。后端工作目录应为 `personal-website-sys`，修改后重启后端。
4. 生产环境优先使用环境变量 `AMAP_WEB_SERVICE_KEY`，它会覆盖本地文件配置。可在既有部署环境文件中设置；不要使用前端 `VITE_` 变量保存密钥。
5. 如果在高德控制台设置 IP 白名单，填写**后端服务器的公网出口 IP**，不是访客 IP；本地开发则使用本机网络的公网出口 IP。

## 请求与返回

- 公开接口：`POST /public/location/city`，JSON 请求体 `{ "longitude": 106.55, "latitude": 29.61 }`，无需登录。
- 成功返回若依响应 `{ "code": 200, "data": { "city": "重庆" } }`。不能识别城市时 `city` 为空字符串。
- 坐标缺失、非有限数或越界返回 400；响应使用 `Cache-Control: no-store`。
- 前端仅发送保留三位小数的坐标，用于城市查询。地球光点仍使用浏览器原始坐标。后端调用高德前进行 GPS → 高德坐标转换，仅返回城市名称，不返回街道或详细地址。
- 重庆、北京、上海、天津的高德 `city` 字段可能为空，使用明确的直辖市省级名称；普通省份、区县、海域不会冒充城市。省直辖的县级市可使用以“市”结尾的区县字段。
- 城市查询会将粗化坐标发送到后端和高德。应用不持久化访客坐标，不记录第三方密钥或完整请求 URL。访客界面的说明已反映城市查询用途。
- 本地文件加载路径取决于后端工作目录；线上仍需部署这次新增的后端接口，并保留同域 `/api` 反向代理。

参考：[高德创建 Key](https://lbs.amap.com/api/webservice/create-project-and-key)、[逆地理编码](https://lbs.amap.com/api/webservice/guide/api/georegeo)、[坐标转换](https://lbs.amap.com/api/webservice/guide/api/convert)。
