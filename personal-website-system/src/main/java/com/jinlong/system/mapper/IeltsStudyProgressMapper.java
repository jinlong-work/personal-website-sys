package com.jinlong.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlong.system.domain.english.IeltsStudyProgress;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface IeltsStudyProgressMapper extends BaseMapper<IeltsStudyProgress> {
    @Select("""
            SELECT p.word_id AS "wordId", w.chapter_id AS "chapterId", p.started_at AS "startedAt",
                p.next_review_at AS "nextReviewAt", p.review_step AS "reviewStep", p.rating, p.version
            FROM ielts_study_progress p
            JOIN ielts_word w ON w.id = p.word_id
            WHERE p.user_id = #{userId}
            """)
    List<IeltsStudyProgress.Progress> listProgress(@Param("userId") long userId);

    @Insert("""
            INSERT INTO ielts_study_progress (user_id, word_id, started_at, next_review_at, rating)
            VALUES (#{userId}, #{wordId}, #{now}, #{now}, 'SEEN')
            ON CONFLICT (user_id, word_id) DO NOTHING
            """)
    int introduceWord(@Param("userId") long userId, @Param("wordId") long wordId, @Param("now") OffsetDateTime now);
}
