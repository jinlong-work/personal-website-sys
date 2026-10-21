package com.jinlong.system.domain.resume;

import com.baomidou.mybatisplus.annotation.*;
import com.jinlong.system.domain.project.JsonbStringTypeHandler;
import java.util.Date;
import lombok.Data;

@Data
@TableName(value = "website_resume", autoResultMap = true)
public class WebsiteResume {
    @TableId(value = "resume_id", type = IdType.INPUT)
    private Long resumeId;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String content;
    private Long version;
    private String updateBy;
    private Date updateTime;
}
