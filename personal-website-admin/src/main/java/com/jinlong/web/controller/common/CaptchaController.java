package com.jinlong.web.controller.common;

import com.google.code.kaptcha.Producer;
import com.jinlong.common.constant.CacheConstants;
import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.common.core.redis.RedisCache;
import com.jinlong.system.service.ISysConfigService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CaptchaController {
    private final Producer captchaProducer;
    private final Producer captchaProducerMath;
    private final RedisCache redisCache;
    private final ISysConfigService configService;
    private final String captchaType;
    private final String prefix;

    public CaptchaController(@Qualifier("captchaProducer") Producer captchaProducer,
            @Qualifier("captchaProducerMath") Producer captchaProducerMath, RedisCache redisCache,
            ISysConfigService configService, @Value("${ruoyi.captchaType:math}") String captchaType,
            @Value("${token.key-prefix}") String prefix) {
        this.captchaProducer = captchaProducer; this.captchaProducerMath = captchaProducerMath;
        this.redisCache = redisCache; this.configService = configService; this.captchaType = captchaType; this.prefix = prefix;
    }

    @GetMapping("/captchaImage")
    public AjaxResult getCode() throws IOException {
        AjaxResult ajax = AjaxResult.success();
        boolean enabled = configService.selectCaptchaEnabled();
        ajax.put("captchaEnabled", enabled);
        if (!enabled) { return ajax; }
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String code;
        BufferedImage image;
        if ("math".equals(captchaType)) {
            String text = captchaProducerMath.createText();
            int split = text.lastIndexOf('@');
            code = text.substring(split + 1);
            image = captchaProducerMath.createImage(text.substring(0, split));
        } else {
            code = captchaProducer.createText();
            image = captchaProducer.createImage(code);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        redisCache.setCacheObject(prefix + ":" + CacheConstants.CAPTCHA_CODE_KEY + uuid, code, 2, TimeUnit.MINUTES);
        ajax.put("uuid", uuid);
        ajax.put("img", Base64.getEncoder().encodeToString(output.toByteArray()));
        return ajax;
    }
}
