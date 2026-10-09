package com.jinlong.personalwebsitesys.admin.tools;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Run this main class in a terminal with the project's dependency classpath. */
public class PasswordHashGenerator {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("请在支持交互式 Console 的终端中运行，勿将密码放入命令行参数");
        char[] password = console.readPassword("管理员密码（不会显示）: ");
        if (password == null) return;
        try {
            String value = new String(password);
            if (value.isEmpty() || value.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("密码不能为空且不超过72个UTF-8字节");
            }
            console.printf("%s%n", new BCryptPasswordEncoder(12).encode(value));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
