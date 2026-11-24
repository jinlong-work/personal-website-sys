package com.jinlong.web.service;

import com.jinlong.common.exception.ServiceException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioIeltsAudioStore {
    private static final Logger log = LoggerFactory.getLogger(MinioIeltsAudioStore.class);
    private static final int MAX_AUDIO_BYTES = 512 * 1024;
    private final MinioClient client;
    private final String bucket;

    public MinioIeltsAudioStore(@Value("${app.storage.minio.endpoint:}") String endpoint,
            @Value("${app.storage.minio.access-key:}") String accessKey,
            @Value("${app.storage.minio.secret-key:}") String secretKey,
            @Value("${app.storage.minio.ielts-audio-bucket:website-ielts-audio}") String bucket) {
        this.bucket = bucket;
        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucket.isBlank()) {
            this.client = null;
        } else {
            this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
            this.client.setTimeout(5_000, 15_000, 15_000);
        }
    }

    public byte[] read(String objectKey) {
        if (client == null) {
            throw new ServiceException("词汇音频存储尚未配置", 503);
        }
        try (var object = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            byte[] audio = object.readNBytes(MAX_AUDIO_BYTES + 1);
            if (audio.length == 0 || audio.length > MAX_AUDIO_BYTES) {
                throw new ServiceException("词汇音频文件异常", 502);
            }
            return audio;
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code()) || "NoSuchObject".equals(exception.errorResponse().code())) {
                throw new ServiceException("词汇音频文件不存在", 404);
            }
            throw unavailable(exception);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    private ServiceException unavailable(Exception exception) {
        log.warn("雅思词汇音频读取失败，异常类型={}", exception.getClass().getSimpleName());
        return new ServiceException("词汇音频暂时不可用，请稍后重试", 503);
    }
}
