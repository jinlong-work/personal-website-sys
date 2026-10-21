package com.jinlong.system.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jinlong.common.constant.WebsiteRole;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.common.utils.SecurityUtils;
import com.jinlong.system.domain.resume.ResumeRequests.*;
import com.jinlong.system.domain.resume.WebsiteResume;
import com.jinlong.system.mapper.UserLedgerMapper;
import com.jinlong.system.mapper.WebsiteResumeMapper;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeManagementService {
    private final WebsiteResumeMapper resumes;
    private final ISysUserService users;
    private final UserLedgerMapper ledger;

    public ResumeManagementService(WebsiteResumeMapper resumes, ISysUserService users, UserLedgerMapper ledger) {
        this.resumes = resumes; this.users = users; this.ledger = ledger;
    }

    public Map<String, Object> published() { return JSON.parseObject(requireResume().getContent()); }
    public Map<String, Object> get() { requirePermission("list"); return adminRow(requireResume()); }

    @Transactional
    public Map<String, Object> save(SaveResume request) {
        ledger.lockUsers(List.of(SecurityUtils.getUserId()));
        SysUser actor = requirePermission("edit");
        if (request == null || request.version() == null || request.version() < 0) throw bad("简历版本无效，请重新加载");
        Map<String, Object> content = validate(request);
        Date updated = new Date();
        int rows = resumes.update(null, new LambdaUpdateWrapper<WebsiteResume>()
                .eq(WebsiteResume::getResumeId, 1L).eq(WebsiteResume::getVersion, request.version())
                .set(WebsiteResume::getContent, JSON.toJSONString(content), "typeHandler=com.jinlong.system.domain.project.JsonbStringTypeHandler")
                .set(WebsiteResume::getVersion, request.version() + 1)
                .set(WebsiteResume::getUpdateBy, actor.getUserName()).set(WebsiteResume::getUpdateTime, updated));
        if (rows != 1) throw new ServiceException("简历已被其他人更新，请重新加载后再编辑", 409);
        return adminRow(requireResume());
    }

    private WebsiteResume requireResume() {
        WebsiteResume resume = resumes.selectById(1L);
        if (resume == null) throw new ServiceException("简历资料尚未初始化", 503);
        return resume;
    }
    private Map<String, Object> adminRow(WebsiteResume resume) {
        Map<String, Object> result = new LinkedHashMap<>(JSON.parseObject(resume.getContent()));
        result.put("version", resume.getVersion()); result.put("updateTime", resume.getUpdateTime());
        result.put("canEdit", hasPermission("edit"));
        return result;
    }
    private SysUser requirePermission(String action) {
        SysUser user = users.selectUserById(SecurityUtils.getUserId());
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) throw new ServiceException("登录状态已失效，请重新登录", 401);
        if (WebsiteRole.of(user) == WebsiteRole.USER || !hasPermission(action)) throw new ServiceException("没有简历管理权限", 403);
        return user;
    }
    private boolean hasPermission(String action) {
        Set<String> permissions = SecurityUtils.getLoginUser().getPermissions();
        return permissions != null && (permissions.contains("*:*:*") || permissions.contains("website:resume:" + action));
    }

    public static Map<String, Object> validate(SaveResume request) {
        if (request == null || request.zh() == null || request.zh().basic() == null) throw bad("请填写中文基本信息");
        return Map.of("zh", content(request.zh(), true), "en", content(request.en(), false));
    }
    private static Content content(Content value, boolean required) {
        if (value == null) value = new Content(null, List.of(), List.of(), List.of());
        Basic b = value.basic();
        if (b == null) b = new Basic(null,null,null,null,null,null,null,null,null,null,null,null,null);
        Basic basic = new Basic(field(b.name(),80,required,"姓名"), field(b.latinName(),100,false,"英文姓名"),
                field(b.title(),120,required,"职业标题"), field(b.role(),120,false,"补充身份"),
                field(b.tagline(),100,false,"首页主张"), field(b.taglineAccent(),100,false,"强调文字"),
                field(b.intro(),600,false,"首页简介"), field(b.about(),1600,false,"关于我"),
                field(b.hometown(),80,false,"来自哪里"), field(b.location(),100,false,"目前所在地"),
                field(b.email(),160,false,"公开邮箱"), field(b.phone(),40,false,"联系电话"), field(b.availability(),80,false,"导航状态"));
        if (!basic.email().isEmpty() && !basic.email().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw bad("邮箱格式不正确");
        List<Education> education = list(value.education(), 12, "教育经历").stream().map(e -> {
            if (e == null) throw bad("教育经历内容不正确");
            dates(e.start(), e.end());
            return new Education(field(e.school(),150,true,"学校"), field(e.major(),150,false,"专业"),
                    field(e.degree(),80,false,"学历"), date(e.start()), date(e.end()), field(e.description(),1200,false,"教育说明"));
        }).toList();
        List<Experience> experience = list(value.experience(), 20, "工作经历").stream().map(e -> {
            if (e == null) throw bad("工作经历内容不正确");
            dates(e.start(), e.end());
            return new Experience(field(e.company(),150,true,"公司"), field(e.role(),120,true,"岗位"),
                    date(e.start()), date(e.end()), field(e.description(),2000,false,"工作说明"));
        }).toList();
        List<Skill> skills = list(value.skills(), 12, "技术分组").stream().map(s -> {
            if (s == null) throw bad("技术分组内容不正确");
            List<String> items = list(s.items(),30,"分组技术").stream().map(item -> field(item,80,true,"技术名称")).distinct().toList();
            if (items.isEmpty()) throw bad("每个技术分组至少填写一项技术");
            return new Skill(field(s.name(),100,true,"分组名称"), items, field(s.description(),600,false,"技术说明"));
        }).toList();
        return new Content(basic, education, experience, skills);
    }
    private static <T> List<T> list(List<T> values, int max, String label) {
        if (values == null) return List.of();
        if (values.size() > max) throw bad(label + "最多" + max + "项");
        return values;
    }
    private static String field(String value, int max, boolean required, String label) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max || result.chars().anyMatch(c -> c < 32 && c != '\n' && c != '\r' && c != '\t')) throw bad(label + "长度或内容不正确");
        if (required && result.isBlank()) throw bad("请填写" + label);
        return result;
    }
    private static String date(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            if (!value.matches("[12][0-9]{3}(?:-(0[1-9]|1[0-2]))?")) throw bad("日期格式应为 YYYY 或 YYYY-MM");
            YearMonth.parse(value.length() == 4 ? value + "-01" : value);
            return value;
        } catch (DateTimeParseException exception) { throw bad("日期格式不正确"); }
    }
    private static void dates(String start, String end) {
        String from = date(start), to = date(end);
        if (!from.isEmpty() && !to.isEmpty()
                && (from.length() == 4 ? from + "-01" : from).compareTo(to.length() == 4 ? to + "-12" : to) > 0)
            throw bad("结束时间不能早于开始时间");
    }
    private static ServiceException bad(String message) { return new ServiceException(message, 400); }
}
