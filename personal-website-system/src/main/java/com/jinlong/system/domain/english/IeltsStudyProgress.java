package com.jinlong.system.domain.english;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName("ielts_study_progress")
public class IeltsStudyProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long wordId;
    private OffsetDateTime startedAt;
    private OffsetDateTime nextReviewAt;
    private Integer reviewStep;
    private String rating;
    private Integer version;

    public enum Rating { SEEN, REMEMBERED, UNSURE, FORGOT }

    public record Progress(long wordId, long chapterId, OffsetDateTime startedAt,
            OffsetDateTime nextReviewAt, int reviewStep, String rating, int version) {
        public static Progress from(long chapterId, IeltsStudyProgress row) {
            return new Progress(row.getWordId(), chapterId, row.getStartedAt(),
                    row.getNextReviewAt(), row.getReviewStep(), row.getRating(), row.getVersion());
        }
    }
}
