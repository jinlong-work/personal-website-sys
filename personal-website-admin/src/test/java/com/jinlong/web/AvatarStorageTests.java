package com.jinlong.web;

import com.jinlong.common.exception.ServiceException;
import com.jinlong.common.utils.AvatarPaths;
import com.jinlong.web.service.AvatarStorageService;
import com.jinlong.web.service.MinioAvatarStore;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvatarStorageTests {
    private final MinioAvatarStore store = mock(MinioAvatarStore.class);
    private final AvatarStorageService avatars = new AvatarStorageService(store);

    static byte[] picture(int width, int height, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.BLUE); graphics.fillRect(0, 0, width, height); graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    @Test
    void jpegIsDecodedCroppedResizedAndStoredAsPng() throws Exception {
        String path = avatars.upload(new MockMultipartFile("file", "../../avatar.jpg", "image/jpeg", picture(1200, 800, "jpg")));
        assertThat(AvatarPaths.validate(path)).matches("/files/avatars/[a-f0-9]{32}\\.png");
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(store).put(matches("[a-f0-9]{32}\\.png"), bytes.capture());
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(bytes.getValue()));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);
    }

    @Test
    void htmlDisguisedAsPngNeverReachesStorage() {
        assertThatThrownBy(() -> avatars.upload(new MockMultipartFile("file", "image.png", "image/png", "<script>alert(1)</script>".getBytes())))
                .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(store);
    }

    @Test
    void gifIsRejectedEvenWhenItHasAnAllowedMimeType() throws Exception {
        byte[] gif = picture(8, 8, "gif");
        assertThatThrownBy(() -> avatars.upload(new MockMultipartFile("file", "image.png", "image/png", gif)))
                .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(store);
    }

    @Test
    void emptyOversizedAndExcessiveDimensionsAreRejected() throws Exception {
        assertThatThrownBy(() -> avatars.upload(new MockMultipartFile("file", new byte[0]))).isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> avatars.upload(new MockMultipartFile("file", new byte[AvatarStorageService.MAX_FILE_BYTES + 1])))
                .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(413));
        byte[] wide = picture(4097, 1, "png");
        assertThatThrownBy(() -> avatars.upload(new MockMultipartFile("file", wide)))
                .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(store);
    }

    @Test
    void readCannotSelectOtherMinioKeys() {
        for (String path : new String[] { "../secret", "account.png", "a".repeat(32) + ".svg" }) {
            assertThatThrownBy(() -> avatars.read(path)).isInstanceOfSatisfying(ServiceException.class,
                    error -> assertThat(error.getCode()).isEqualTo(404));
        }
        verifyNoInteractions(store);
    }

    @Test
    void missingCredentialsDoNotPreventStartupButUploadFailsClearly() {
        var unconfigured = new MinioAvatarStore("", "", "", "website-avatars");
        assertThatThrownBy(() -> unconfigured.put("a".repeat(32) + ".png", new byte[] { 1 }))
                .isInstanceOfSatisfying(ServiceException.class, error -> assertThat(error.getCode()).isEqualTo(503));
    }
}
