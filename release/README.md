# 后端发布包

`personal-website-api.jar` 为可直接运行的 Spring Boot JAR，包含所需业务模块与依赖，运行环境为 Java 25。发布包排除了本机 `application-dev.yml`，连接密码和JWT密钥需要在服务器提供。

## 重新生成

在后端根目录使用 JDK 25 执行：

```powershell
.\mvnw.cmd -pl personal-website-api -am -Prelease -DskipTests clean package
Copy-Item personal-website-api/target/personal-website-api-0.0.1-SNAPSHOT.jar release/personal-website-api.jar
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
```

地址应使用容器实际可访问的数据库和Redis地址。容器内的127.0.0.1指该容器自身。JWT密钥应持久保留，同一环境的多个实例需使用相同密钥、签发者和缓存前缀。

先校验上传后的JAR，再构建镜像和启动。以下端口映射适用于Nginx运行在宿主机：

```bash
sha256sum -c personal-website-api.jar.sha256
docker build -t personal-website-api:latest .
docker run -d --name personal-website-api --restart unless-stopped \
  --env-file app.env -p 127.0.0.1:8080:8080 \
  personal-website-api:latest
docker logs -f personal-website-api
```

宿主机Nginx通过 `http://127.0.0.1:8080` 转发 `/api/`，保留完整的 `/api` 路径。如果Nginx也在容器内，应共用Docker网络并通过后端容器名 `personal-website-api:8080` 转发。

不使用Docker时，服务器安装Java 25并通过环境变量提供上述配置后执行：

```bash
java -jar personal-website-api.jar --spring.profiles.active=prod
```

发布构建跳过测试，不启动后端，不连接数据库或Redis。服务器运行后需自行验证配置、网络和实际登录流程。默认日志写入容器的 `/app/logs` 和标准输出，`docker logs`可查看；需要保留跨容器更换的文件日志时挂载该目录。
