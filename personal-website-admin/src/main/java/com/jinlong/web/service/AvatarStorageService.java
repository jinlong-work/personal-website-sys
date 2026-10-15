package com.jinlong.web.service;

import com.jinlong.common.exception.ServiceException;
import com.jinlong.common.utils.AvatarPaths;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarStorageService {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private final MinioAvatarStore store;

    public AvatarStorageService(MinioAvatarStore store) { this.store = store; }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) { throw badImage("请选择头像图片"); }
        if (file.getSize() > MAX_FILE_BYTES) { throw new ServiceException("头像图片不能超过2MB", 413); }
        byte[] png = normalize(file);
        String name = UUID.randomUUID().toString().replace("-", "") + ".png";
        store.put(name, png);
        return AvatarPaths.URL_PREFIX + name;
    }

    public byte[] read(String name) {
        if (!AvatarPaths.isFileName(name)) { throw new ServiceException("头像不存在", 404); }
        return store.get(name);
    }

    private byte[] normalize(MultipartFile file) {
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MAX_FILE_BYTES + 1);
            if (bytes.length > MAX_FILE_BYTES) { throw new ServiceException("头像图片不能超过2MB", 413); }
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) { throw badImage("请选择有效的JPG或PNG图片"); }
                ImageReader reader = readers.next();
                try {
                    if (!Set.of("jpeg", "png").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                        throw badImage("头像仅支持JPG和PNG图片");
                    }
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    // 在解码前限制尺寸，防止小文件被解码成过大的图片。
                    if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_000_000) {
                        throw badImage("头像尺寸不能超过4096×4096，且总像素不能超过1600万");
                    }
                    BufferedImage image = reader.read(0);
                    int side = Math.min(width, height), size = Math.min(512, side);
                    BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = square.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                        int left = (width - side) / 2, top = (height - side) / 2;
                        graphics.drawImage(image, 0, 0, size, size, left, top, left + side, top + side, null);
                    } finally { graphics.dispose(); }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    ImageIO.write(square, "png", output);
                    return output.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw badImage("图片无法读取，请重新选择JPG或PNG图片");
        }
    }

    private ServiceException badImage(String message) { return new ServiceException(message, 400); }
}
