package com.jinlong.web.service;

import com.jinlong.common.exception.ServiceException;
import com.jinlong.system.domain.project.ProjectAsset;
import com.jinlong.system.mapper.ProjectAssetMapper;
import com.jinlong.system.service.ProjectManagementService;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectImageService {
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;
    private final MinioProjectStore store;
    private final ProjectAssetMapper assets;
    private final ProjectManagementService projects;
    public ProjectImageService(MinioProjectStore store, ProjectAssetMapper assets, ProjectManagementService projects) {
        this.store = store; this.assets = assets; this.projects = projects;
    }

    public Map<String, Object> upload(MultipartFile file) {
        String actor = projects.requirePermission("upload").getUserName();
        if (file == null || file.isEmpty()) throw bad("请选择项目效果图");
        if (file.getSize() > MAX_FILE_BYTES) throw new ServiceException("项目图片不能超过10MB", 413);
        BufferedImage normalized = normalize(file);
        byte[] bytes;
        try (var output = new ByteArrayOutputStream(); var destination = new MemoryCacheImageOutputStream(output)) {
            var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try {
                writer.setOutput(destination);
                var options = writer.getDefaultWriteParam();
                options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                options.setCompressionQuality(0.9f);
                writer.write(null, new IIOImage(normalized, null, null), options);
                destination.flush();
                bytes = output.toByteArray();
            } finally { writer.dispose(); }
        } catch (Exception exception) { throw bad("项目图片处理失败，请重新选择图片"); }
        if (bytes.length > MAX_FILE_BYTES) throw new ServiceException("项目图片处理后超过10MB", 413);
        String name = UUID.randomUUID().toString().replace("-", "") + ".jpg";
        store.put(name, bytes);
        ProjectAsset asset = new ProjectAsset();
        asset.setPath("/files/projects/" + name);
        String original = file.getOriginalFilename() == null ? "project.jpg" : file.getOriginalFilename().replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1);
        asset.setOriginalName(original.substring(0, Math.min(original.length(), 200)));
        asset.setWidth(normalized.getWidth()); asset.setHeight(normalized.getHeight()); asset.setFileSize((long) bytes.length);
        asset.setCreateBy(actor); asset.setCreateTime(new Date());
        assets.insert(asset);
        return Map.of("path", asset.getPath(), "width", asset.getWidth(), "height", asset.getHeight(), "size", asset.getFileSize());
    }

    public byte[] read(String name) {
        if (!name.matches("[a-f0-9]{32}\\.jpg")) throw new ServiceException("项目图片不存在", 404);
        return store.get(name);
    }

    private BufferedImage normalize(MultipartFile file) {
        try (var source = file.getInputStream(); var input = new MemoryCacheImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw bad("请选择有效的JPG或PNG图片");
            var reader = readers.next();
            try {
                if (!Set.of("jpeg", "png").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw bad("请通过页面转换并上传项目图片");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 32_000_000) throw bad("项目图片边长不能超过8192，总像素不能超过3200万");
                var original = reader.read(0);
                double scale = Math.min(1, 2560.0 / Math.max(width, height));
                BufferedImage image = new BufferedImage(Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(original, 0, 0, image.getWidth(), image.getHeight(), null);
                } finally { graphics.dispose(); }
                return image;
            } finally { reader.dispose(); }
        } catch (ServiceException exception) { throw exception; }
        catch (Exception exception) { throw bad("图片无法读取，请重新选择图片"); }
    }
    private ServiceException bad(String message) { return new ServiceException(message, 400); }
}
