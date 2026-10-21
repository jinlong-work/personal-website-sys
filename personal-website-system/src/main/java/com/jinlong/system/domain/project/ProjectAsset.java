package com.jinlong.system.domain.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("website_project_asset")
public class ProjectAsset {
    @TableId(value = "asset_id", type = IdType.AUTO)
    private Long assetId;
    private String path;
    private String originalName;
    private Integer width;
    private Integer height;
    private Long fileSize;
    private String createBy;
    private Date createTime;
}
