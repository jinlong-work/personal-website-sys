package com.jinlong.web.service;

import com.jinlong.common.exception.ServiceException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 使用私有桶，只有后端持有 MinIO 凭据。首次上传时按需创建桶。 */
@Component
public class MinioAvatarStore {
    private static final Logger log = LoggerFactory.getLogger(MinioAvatarStore.class);
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private volatile MinioClient client;
    private volatile boolean bucketReady;

    public MinioAvatarStore(@Value("${app.storage.minio.endpoint:}") String endpoint,
            @Value("${app.storage.minio.access-key:}") String accessKey,
            @Value("${app.storage.minio.secret-key:}") String secretKey,
            @Value("${app.storage.minio.avatar-bucket:website-avatars}") String bucket) {
        this.endpoint = endpoint; this.accessKey = accessKey; this.secretKey = secretKey; this.bucket = bucket;
    }

    public void put(String name, byte[] png) {
        try {
            MinioClient connection = connection();
            ensureBucket(connection);
            connection.putObject(PutObjectArgs.builder().bucket(bucket).object("avatars/" + name)
                    .stream(new ByteArrayInputStream(png), (long) png.length, -1L).contentType("image/png").build());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    public byte[] get(String name) {
        try (var object = connection().getObject(GetObjectArgs.builder().bucket(bucket).object("avatars/" + name).build())) {
            byte[] bytes = object.readNBytes(AvatarStorageService.MAX_FILE_BYTES + 1);
            if (bytes.length > AvatarStorageService.MAX_FILE_BYTES) {
                throw new ServiceException("头像文件异常", 502);
            }
            return bytes;
        } catch (ErrorResponseException exception) {
            if (java.util.Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket").contains(exception.errorResponse().code())) {
                throw new ServiceException("头像不存在", 404);
            }
            throw unavailable(exception);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private MinioClient connection() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) {
                        throw new ServiceException("头像存储尚未配置，请联系管理员", 503);
                    }
                    MinioClient created = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
                    created.setTimeout(5_000, 5_000, 10_000);
                    client = created;
                }
            }
        }
        return client;
    }

    private void ensureBucket(MinioClient connection) throws Exception {
        if (bucketReady) { return; }
        synchronized (this) {
            if (bucketReady) { return; }
            if (!connection.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                try {
                    connection.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                } catch (ErrorResponseException exception) {
                    // 多实例同时首次上传时，允许另一实例先创建同一私有桶。
                    if (!"BucketAlreadyOwnedByYou".equals(exception.errorResponse().code())) { throw exception; }
                }
            }
            bucketReady = true;
        }
    }

    private ServiceException unavailable(Exception exception) {
        // SDK 异常可能含签名请求和存储地址，不向响应或日志输出原始异常。
        log.warn("头像存储请求失败，异常类型={}", exception.getClass().getSimpleName());
        return new ServiceException("头像存储暂时不可用，请稍后重试", 503);
    }
}
