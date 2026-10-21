package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.web.service.ProjectImageService;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files/projects")
public class ProjectImageController {
    private final ProjectImageService images;
    public ProjectImageController(ProjectImageService images) { this.images = images; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('*:*:*') or hasAuthority('website:project:upload')")
    public AjaxResult upload(@RequestParam("file") MultipartFile file) { return AjaxResult.success(images.upload(file)); }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> image(@PathVariable String name) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff").body(images.read(name));
    }
}
