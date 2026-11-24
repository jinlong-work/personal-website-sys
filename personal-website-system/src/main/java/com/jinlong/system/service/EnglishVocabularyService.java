package com.jinlong.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.system.domain.english.IeltsChapter;
import com.jinlong.system.domain.english.IeltsWord;
import com.jinlong.system.mapper.IeltsChapterMapper;
import com.jinlong.system.mapper.IeltsWordMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EnglishVocabularyService {
    private final IeltsChapterMapper chapters;
    private final IeltsWordMapper words;

    public EnglishVocabularyService(IeltsChapterMapper chapters, IeltsWordMapper words) {
        this.chapters = chapters;
        this.words = words;
    }

    public List<IeltsChapter.Summary> listChapters() {
        return chapters.listSummaries();
    }

    public WordPage listWords(long chapterId, long pageNum, long pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100 || pageNum > Long.MAX_VALUE / pageSize) {
            throw new ServiceException("分页参数不正确", 400);
        }
        requireChapter(chapterId);
        IPage<IeltsWord> page = words.selectPage(new Page<>(pageNum, pageSize), orderedWords(chapterId));
        return new WordPage(page.getRecords().stream().map(IeltsWord.Card::from).toList(), page.getTotal());
    }

    public List<IeltsWord.Track> playlist(long chapterId) {
        requireChapter(chapterId);
        return words.selectList(orderedWords(chapterId)
                        .select(IeltsWord::getId, IeltsWord::getWord)
                        .isNotNull(IeltsWord::getAudioObjectKey)
                        .ne(IeltsWord::getAudioObjectKey, ""))
                .stream().map(word -> new IeltsWord.Track(word.getId(), word.getWord())).toList();
    }

    public String audioKey(long wordId) {
        IeltsWord word = words.selectById(wordId);
        if (word == null || !word.hasAudio()) {
            throw new ServiceException("这条词汇暂无发音", 404);
        }
        return word.getAudioObjectKey();
    }

    private void requireChapter(long chapterId) {
        if (chapters.selectById(chapterId) == null) {
            throw new ServiceException("章节不存在", 404);
        }
    }

    private LambdaQueryWrapper<IeltsWord> orderedWords(long chapterId) {
        return Wrappers.lambdaQuery(IeltsWord.class)
                .eq(IeltsWord::getChapterId, chapterId)
                .orderByAsc(IeltsWord::getSortOrder, IeltsWord::getId);
    }

    public record WordPage(List<IeltsWord.Card> rows, long total) {}
}
