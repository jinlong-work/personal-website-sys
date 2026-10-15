package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.web.service.AvatarStorageService;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files/avatars")
public class AvatarController {
    private final AvatarStorageService avatars;

    public AvatarController(AvatarStorageService avatars) { this.avatars = avatars; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('account:profile:edit') or hasAuthority('system:user:add') or hasAuthority('system:user:edit')")
    public AjaxResult upload(@RequestParam("file") MultipartFile file) {
        return AjaxResult.success(Map.of("avatar", avatars.upload(file)));
    }

    /** 仅按随机文件名读取头像，不开放桶列表和任意文件下载。 */
    @GetMapping("/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff").body(avatars.read(name));
    }
}
