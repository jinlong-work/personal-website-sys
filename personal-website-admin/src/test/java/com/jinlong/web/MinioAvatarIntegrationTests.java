package com.jinlong.web;

import com.jinlong.web.service.AvatarStorageService;
import com.jinlong.web.service.MinioAvatarStore;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.mock.web.MockMultipartFile;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/** 显式启用才连接实际存储，只创建并清理本次测试自己的随机对象，不连接业务数据库。 */
@EnabledIfSystemProperty(named = "minio.live", matches = "true")
class MinioAvatarIntegrationTests {
    @Test
    void uploadAndReadBackUsingConfiguredServer() throws Exception {
        Path config = Path.of(".local/minio.yml");
        if (!Files.exists(config)) { config = Path.of("../.local/minio.yml"); }
        Map<String, String> values;
        try (var input = Files.newInputStream(config)) { values = new Yaml().load(input); }
        String endpoint = values.get("MINIO_ENDPOINT"), access = values.get("MINIO_ACCESS_KEY"), secret = values.get("MINIO_SECRET_KEY");
        String bucket = values.get("MINIO_AVATAR_BUCKET");
        var avatars = new AvatarStorageService(new MinioAvatarStore(endpoint, access, secret, bucket));
        String path = avatars.upload(new MockMultipartFile("file", "avatar-test.png", "image/png", AvatarStorageTests.picture(32, 24, "png")));
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            var image = ImageIO.read(new ByteArrayInputStream(avatars.read(name)));
            assertThat(image.getWidth()).isEqualTo(24);
            assertThat(image.getHeight()).isEqualTo(24);
        } finally {
            try (MinioClient client = MinioClient.builder().endpoint(endpoint).credentials(access, secret).build()) {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object("avatars/" + name).build());
            }
        }
    }
}
