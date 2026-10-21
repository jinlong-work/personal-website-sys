package com.jinlong.system.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlong.common.constant.WebsiteRole;
import com.jinlong.common.core.domain.entity.SysUser;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.common.utils.SecurityUtils;
import com.jinlong.system.domain.project.ProjectAsset;
import com.jinlong.system.domain.project.ProjectRequests.*;
import com.jinlong.system.domain.project.WebsiteProject;
import com.jinlong.system.mapper.ProjectAssetMapper;
import com.jinlong.system.mapper.UserLedgerMapper;
import com.jinlong.system.mapper.WebsiteProjectMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectManagementService {
    private final WebsiteProjectMapper projects;
    private final ProjectAssetMapper assets;
    private final ISysUserService users;
    private final UserLedgerMapper ledger;

    public ProjectManagementService(WebsiteProjectMapper projects, ProjectAssetMapper assets,
            ISysUserService users, UserLedgerMapper ledger) {
        this.projects = projects; this.assets = assets; this.users = users; this.ledger = ledger;
    }

    public List<Map<String, Object>> published() {
        return projects.selectList(new LambdaQueryWrapper<WebsiteProject>()
                .eq(WebsiteProject::getStatus, "published").orderByAsc(WebsiteProject::getSortOrder)
                .orderByAsc(WebsiteProject::getProjectId)).stream().map(project -> row(project, false)).toList();
    }

    public Map<String, Object> list(String keyword, String status, int pageNum, int pageSize) {
        requirePermission("list");
        if (pageNum < 1 || pageNum > 1_000_000 || pageSize < 1 || pageSize > 100) throw bad("分页参数不正确，每页最多100条");
        String search = text(keyword, 150, "搜索内容");
        if (!status.isEmpty()) requireStatus(status);
        var query = new LambdaQueryWrapper<WebsiteProject>();
        if (!search.isEmpty()) query.and(filter -> filter.like(WebsiteProject::getName, search).or().like(WebsiteProject::getProjectType, search));
        query.eq(!status.isEmpty(), WebsiteProject::getStatus, status)
                .orderByAsc(WebsiteProject::getSortOrder).orderByAsc(WebsiteProject::getProjectId);
        var page = projects.selectPage(new Page<>(pageNum, pageSize), query);
        return Map.of("rows", page.getRecords().stream().map(project -> row(project, true)).toList(), "total", page.getTotal(),
                "canCreate", hasPermission("add"), "canUpload", hasPermission("upload"));
    }

    public Map<String, Object> get(Long id) { requirePermission("list"); return row(requireProject(id), true); }

    @Transactional
    public Map<String, Object> create(SaveProject request) {
        lockActor();
        SysUser actor = requirePermission("add");
        WebsiteProject project = content(request);
        if ("published".equals(project.getStatus())) requirePermission("publish");
        Date now = new Date();
        project.setVersion(0L); project.setDelFlag("0");
        project.setCreateBy(actor.getUserName()); project.setUpdateBy(actor.getUserName());
        project.setCreateTime(now); project.setUpdateTime(now);
        projects.insert(project);
        return row(project, true);
    }

    @Transactional
    public Map<String, Object> update(Long id, SaveProject request) {
        lockActor();
        SysUser actor = requirePermission("edit");
        WebsiteProject previous = requireProject(id);
        requireVersion(request == null ? null : request.version(), previous);
        WebsiteProject project = content(request);
        if (!Objects.equals(previous.getStatus(), project.getStatus())) requirePermission("publish");
        project.setProjectId(id); project.setVersion(previous.getVersion() + 1);
        project.setUpdateBy(actor.getUserName()); project.setUpdateTime(new Date());
        if (projects.update(project, new LambdaUpdateWrapper<WebsiteProject>()
                .eq(WebsiteProject::getProjectId, id).eq(WebsiteProject::getVersion, previous.getVersion())) != 1) throw conflict();
        return row(requireProject(id), true);
    }

    @Transactional
    public Map<String, Object> changeStatus(Long id, ChangeStatus request) {
        lockActor();
        SysUser actor = requirePermission("publish");
        WebsiteProject project = requireProject(id);
        requireVersion(request == null ? null : request.version(), project);
        String status = requireStatus(request.status());
        if ("published".equals(status)) validatePublication(project);
        if (projects.update(null, new LambdaUpdateWrapper<WebsiteProject>()
                .eq(WebsiteProject::getProjectId, id).eq(WebsiteProject::getVersion, project.getVersion())
                .set(WebsiteProject::getStatus, status).set(WebsiteProject::getVersion, project.getVersion() + 1)
                .set(WebsiteProject::getUpdateBy, actor.getUserName()).set(WebsiteProject::getUpdateTime, new Date())) != 1) throw conflict();
        return row(requireProject(id), true);
    }

    @Transactional
    public void remove(Long id, Long version) {
        lockActor();
        SysUser actor = requirePermission("remove");
        WebsiteProject project = requireProject(id);
        requireVersion(version, project);
        if (projects.update(null, new LambdaUpdateWrapper<WebsiteProject>()
                .eq(WebsiteProject::getProjectId, id).eq(WebsiteProject::getVersion, version)
                .set(WebsiteProject::getDelFlag, "2").set(WebsiteProject::getVersion, version + 1)
                .set(WebsiteProject::getUpdateBy, actor.getUserName()).set(WebsiteProject::getUpdateTime, new Date())) != 1) throw conflict();
    }

    public SysUser requirePermission(String action) {
        SysUser user = users.selectUserById(SecurityUtils.getUserId());
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag())) throw new ServiceException("登录状态已失效，请重新登录", 401);
        if (WebsiteRole.of(user) == WebsiteRole.USER || !hasPermission(action)) throw new ServiceException("没有项目管理权限", 403);
        return user;
    }

    private void lockActor() { ledger.lockUsers(List.of(SecurityUtils.getUserId())); }
    private boolean hasPermission(String action) {
        var permissions = SecurityUtils.getLoginUser().getPermissions();
        return permissions != null && (permissions.contains("*:*:*") || permissions.contains("website:project:" + action));
    }

    private WebsiteProject content(SaveProject request) {
        if (request == null) throw bad("请填写项目内容");
        WebsiteProject project = new WebsiteProject();
        project.setName(required(request.name(), 150, "项目名称"));
        project.setProjectType(required(request.type(), 80, "项目类型"));
        project.setDescription(text(request.description(), 12000, "项目介绍"));
        project.setCategory(text(request.category(), 100, "展示分类"));
        project.setHeadline(text(request.headline(), 180, "卡片标题"));
        project.setSummary(text(request.summary(), 1000, "卡片简介"));
        project.setTechnologies(JSON.toJSONString(items(request.technologies(), 20, 80, "技术栈")));
        project.setAchievements(JSON.toJSONString(items(request.achievements(), 30, 2000, "项目贡献")));
        List<String> images = items(request.images(), 12, 100, "项目效果图");
        if (new HashSet<>(images).size() != images.size() || images.stream().anyMatch(path -> !path.matches("/files/projects/[a-f0-9]{32}\\.jpg"))) throw bad("效果图地址不正确，请重新上传");
        if (!images.isEmpty() && assets.selectCount(new LambdaQueryWrapper<ProjectAsset>().in(ProjectAsset::getPath, images)) != images.size()) throw bad("存在无效的项目效果图，请重新上传");
        project.setImages(JSON.toJSONString(images));
        project.setEnglish(JSON.toJSONString(translation(request.english())));
        project.setFeatured(Boolean.TRUE.equals(request.featured()));
        project.setStatus(requireStatus(request.status()));
        if (request.sortOrder() == null || request.sortOrder() < 0 || request.sortOrder() > 999999) throw bad("排序值需为0至999999之间的整数");
        project.setSortOrder(request.sortOrder());
        if ("published".equals(project.getStatus())) validatePublication(project);
        return project;
    }

    private void validatePublication(WebsiteProject project) {
        if (project.getDescription().isBlank() || JSON.parseArray(project.getImages()).isEmpty()) throw bad("发布前请填写项目介绍，并上传至少一张效果图");
    }

    private Map<String, Object> translation(Translation value) {
        if (value == null) return Map.of();
        return Map.of("name", text(value.name(), 150, "英文项目名称"), "type", text(value.type(), 80, "英文项目类型"),
                "description", text(value.description(), 12000, "英文项目介绍"), "category", text(value.category(), 100, "英文展示分类"),
                "headline", text(value.headline(), 180, "英文卡片标题"), "summary", text(value.summary(), 1000, "英文卡片简介"),
                "technologies", items(value.technologies(), 20, 80, "英文技术栈"), "achievements", items(value.achievements(), 30, 2000, "英文项目贡献"));
    }

    private Map<String, Object> row(WebsiteProject project, boolean admin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", project.getProjectId()); result.put("name", project.getName()); result.put("type", project.getProjectType());
        result.put("description", project.getDescription()); result.put("category", project.getCategory());
        result.put("headline", project.getHeadline()); result.put("summary", project.getSummary());
        result.put("technologies", JSON.parseArray(project.getTechnologies(), String.class));
        result.put("achievements", JSON.parseArray(project.getAchievements(), String.class));
        result.put("images", JSON.parseArray(project.getImages(), String.class));
        result.put("english", JSON.parseObject(project.getEnglish())); result.put("featured", project.getFeatured());
        result.put("sortOrder", project.getSortOrder()); result.put("managed", true);
        if (admin) {
            result.put("status", project.getStatus()); result.put("version", project.getVersion());
            result.put("createTime", project.getCreateTime()); result.put("updateTime", project.getUpdateTime());
            result.put("canEdit", hasPermission("edit")); result.put("canRemove", hasPermission("remove"));
            result.put("canPublish", hasPermission("publish"));
        }
        return result;
    }

    private WebsiteProject requireProject(Long id) {
        if (id == null || id < 1) throw bad("项目编号不正确");
        WebsiteProject project = projects.selectById(id);
        if (project == null) throw new ServiceException("项目不存在或已被删除", 404);
        return project;
    }
    private void requireVersion(Long version, WebsiteProject project) { if (!Objects.equals(version, project.getVersion())) throw conflict(); }
    private String requireStatus(String status) { if (!List.of("draft", "published").contains(status == null ? "" : status)) throw bad("项目状态不正确"); return status; }
    private String required(String value, int max, String label) { String result = text(value, max, label); if (result.isEmpty()) throw bad("请填写" + label); return result; }
    private String text(String value, int max, String label) { String result = value == null ? "" : value.trim(); if (result.length() > max) throw bad(label + "不能超过" + max + "个字符"); return result; }
    private List<String> items(List<String> values, int max, int length, String label) {
        if (values == null) return List.of();
        if (values.size() > max) throw bad(label + "最多" + max + "项");
        List<String> result = new ArrayList<>();
        for (String value : values) { String item = required(value, length, label); result.add(item); }
        return result;
    }
    private ServiceException bad(String message) { return new ServiceException(message, 400); }
    private ServiceException conflict() { return new ServiceException("项目已被其他操作更新，请刷新列表后重新编辑", 409); }
}
