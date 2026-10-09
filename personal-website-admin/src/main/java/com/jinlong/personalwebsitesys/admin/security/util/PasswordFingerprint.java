package com.jinlong.personalwebsitesys.admin.security.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 会话只保存密码哈希的指纹，用于检测密码变更，不保存密码或完整密码哈希。 */
public final class PasswordFingerprint {
    private PasswordFingerprint() {}

    public static String of(String passwordHash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
