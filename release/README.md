# 后端发布包

`personal-website-api.jar` 为当前 `personal-website-admin` 模块构建的 Spring Boot 可执行 JAR，沿用原发布文件名以兼容已有 Docker 配置，运行环境为 Java 25。发布包排除了本机 `application-dev.yml` 和 `.local/`，数据库、Redis、JWT及MinIO密钥需要在服务器提供。

## 重新生成

在后端根目录使用 JDK 25 执行：

```powershell
mvn -pl personal-website-admin -am -Prelease clean package
Copy-Item personal-website-admin/target/personal-website-admin-0.0.1-SNAPSHOT.jar release/personal-website-api.jar
```

`release` 是 Maven 的打包配置，只排除本机dev资源；实际运行时通过 `SPRING_PROFILES_ACTIVE=prod` 选择生产环境。重新生成后同步更新SHA256校验文件。未使用release配置的本机开发打包仍会加载dev资源。

## Docker运行

把本目录上传到服务器。在目录内创建仅保留在服务器上的 `app.env`，填写真实值，文件已加入Git忽略：

```dotenv
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://39.105.91.141:5432/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=替换为数据库密码
SPRING_DATA_REDIS_HOST=39.105.91.141
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_DATABASE=0
SPRING_DATA_REDIS_PASSWORD=替换为Redis密码
JWT_SECRET=替换为至少64字节随机密钥的Base64值
AUTH_ALLOWED_ORIGINS=https://www.caojinlong.top
MINIO_ENDPOINT=http://39.105.91.141:9000
MINIO_ACCESS_KEY=替换为MinIO访问密钥
MINIO_SECRET_KEY=替换为MinIO密钥
MINIO_AVATAR_BUCKET=website-avatars
```

地址应使用容器实际可访问的数据库、Redis和MinIO地址。容器内的127.0.0.1指该容器自身。MinIO填9000的S3 API地址，9001用于控制台；头像桶保持私有，图片由后端返回。JWT密钥应持久保留，同一环境的多个实例需使用相同密钥、签发者和缓存前缀。

新环境先按后端根目录README执行003、004（需要初始化管理员时）、005迁移。现有环境已完成的迁移无需重复初始化账号。

先校验上传后的JAR，再构建镜像和启动。以下端口映射适用于Nginx运行在宿主机：

```bash
sha256sum -c personal-website-api.jar.sha256
docker build -t personal-website-api:latest .
docker run -d --name personal-website-api --restart unless-stopped \
  --env-file app.env -p 127.0.0.1:8080:8080 \
  personal-website-api:latest
docker logs -f personal-website-api
```

后端接口位于根路径，宿主机Nginx转发 `/api/` 时需去掉此前缀，并允许头像上传：

```nginx
location /api/ {
    client_max_body_size 3m;
    proxy_pass http://127.0.0.1:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

`proxy_pass` 结尾的斜杠用于去掉 `/api/` 前缀。如果Nginx也在容器内，应共用Docker网络并将代理地址改为 `http://personal-website-api:8080/`。

不使用Docker时，服务器安装Java 25并通过环境变量提供上述配置后执行：

```bash
java -jar personal-website-api.jar --spring.profiles.active=prod
```

上述发布构建执行本地隔离测试；数据库和Redis使用测试替身，实际存储集成测试仅在显式开启时运行。构建不会启动业务服务或迁移数据库。服务器运行后需验证环境配置、网络、登录及头像上传。默认日志写入容器的 `/app/logs` 和标准输出，`docker logs`可查看；需要保留跨容器更换的文件日志时挂载该目录。
