package com.jinlong.web.service;

import com.jinlong.common.exception.ServiceException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioProjectStore {
    private static final Logger log = LoggerFactory.getLogger(MinioProjectStore.class);
    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private volatile MinioClient client;
    private volatile boolean bucketReady;

    public MinioProjectStore(@Value("${app.storage.minio.endpoint:}") String endpoint,
            @Value("${app.storage.minio.access-key:}") String accessKey,
            @Value("${app.storage.minio.secret-key:}") String secretKey,
            @Value("${app.storage.minio.project-bucket:website-projects}") String bucket) {
        this.endpoint = endpoint; this.accessKey = accessKey; this.secretKey = secretKey; this.bucket = bucket;
    }

    public void put(String name, byte[] bytes) {
        try {
            MinioClient connection = connection();
            ensureBucket(connection);
            connection.putObject(PutObjectArgs.builder().bucket(bucket).object("projects/" + name)
                    .stream(new ByteArrayInputStream(bytes), (long) bytes.length, -1L).contentType("image/jpeg").build());
        } catch (ServiceException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(exception); }
    }

    public byte[] get(String name) {
        try (var object = connection().getObject(GetObjectArgs.builder().bucket(bucket).object("projects/" + name).build())) {
            byte[] bytes = object.readNBytes(ProjectImageService.MAX_FILE_BYTES + 1);
            if (bytes.length > ProjectImageService.MAX_FILE_BYTES) throw new ServiceException("项目图片异常", 502);
            return bytes;
        } catch (ErrorResponseException exception) {
            if (Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket").contains(exception.errorResponse().code())) throw new ServiceException("项目图片不存在", 404);
            throw unavailable(exception);
        } catch (ServiceException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(exception); }
    }

    private MinioClient connection() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) throw new ServiceException("项目图片存储尚未配置", 503);
                    MinioClient created = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
                    created.setTimeout(5_000, 15_000, 15_000);
                    client = created;
                }
            }
        }
        return client;
    }

    private void ensureBucket(MinioClient connection) throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (bucketReady) return;
            if (!connection.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                try { connection.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()); }
                catch (ErrorResponseException exception) { if (!"BucketAlreadyOwnedByYou".equals(exception.errorResponse().code())) throw exception; }
            }
            bucketReady = true;
        }
    }

    private ServiceException unavailable(Exception exception) {
        log.warn("项目图片存储请求失败，异常类型={}", exception.getClass().getSimpleName());
        return new ServiceException("项目图片存储暂时不可用，请稍后重试", 503);
    }
}
