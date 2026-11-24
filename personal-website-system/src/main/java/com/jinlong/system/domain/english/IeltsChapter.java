package com.jinlong.system.domain.english;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ielts_chapter")
public class IeltsChapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer sourceChapterId;
    private String title;
    private Integer sortOrder;

    public record Summary(long id, int number, String title, long wordCount) {}
}
