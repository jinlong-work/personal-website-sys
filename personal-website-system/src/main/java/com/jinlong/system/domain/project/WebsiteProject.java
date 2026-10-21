package com.jinlong.system.domain.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName(value = "website_project", autoResultMap = true)
public class WebsiteProject {
    @TableId(value = "project_id", type = IdType.AUTO)
    private Long projectId;
    private String name;
    private String projectType;
    private String description;
    private String category;
    private String headline;
    private String summary;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String technologies;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String achievements;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String images;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String english;
    private Boolean featured;
    private String status;
    private Integer sortOrder;
    private Long version;
    @TableLogic(value = "0", delval = "2")
    private String delFlag;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
}
