package com.jinlong.common.utils;

import com.jinlong.common.exception.ServiceException;
import java.util.regex.Pattern;

/** 数据库只保存本站的头像路径，不接受外部地址或任意对象键。 */
public final class AvatarPaths {
    public static final String URL_PREFIX = "/files/avatars/";
    private static final Pattern FILE_NAME = Pattern.compile("[a-f0-9]{32}\\.png");

    private AvatarPaths() {}

    public static boolean isFileName(String name) {
        return name != null && FILE_NAME.matcher(name).matches();
    }

    public static String validate(String avatar) {
        // 兼容旧客户端：未传头像时保留原值；空字符串明确表示移除头像。
        if (avatar == null || avatar.isEmpty()) { return avatar; }
        if (!avatar.startsWith(URL_PREFIX) || !isFileName(avatar.substring(URL_PREFIX.length()))) {
            throw new ServiceException("头像地址不正确，请重新上传图片", 400);
        }
        return avatar;
    }
}
