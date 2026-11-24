package com.jinlong.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jinlong.system.domain.english.IeltsChapter;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface IeltsChapterMapper extends BaseMapper<IeltsChapter> {
    @Select("""
            SELECT c.id, c.source_chapter_id AS number, c.title, COUNT(w.id) AS "wordCount"
            FROM ielts_chapter c
            LEFT JOIN ielts_word w ON w.chapter_id = c.id
            GROUP BY c.id, c.source_chapter_id, c.title, c.sort_order
            ORDER BY c.sort_order, c.id
            """)
    List<IeltsChapter.Summary> listSummaries();
}
