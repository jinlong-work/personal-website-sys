package com.jinlong.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jinlong.common.exception.ServiceException;
import com.jinlong.system.domain.english.IeltsStudyProgress;
import com.jinlong.system.domain.english.IeltsStudyProgress.Progress;
import com.jinlong.system.domain.english.IeltsStudyProgress.Rating;
import com.jinlong.system.domain.english.IeltsWord;
import com.jinlong.system.mapper.IeltsStudyProgressMapper;
import com.jinlong.system.mapper.IeltsWordMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EnglishStudyService {
    private static final ZoneId STUDY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<Integer> REVIEW_DAYS = List.of(1, 3, 7, 14);

    private final IeltsStudyProgressMapper progress;
    private final IeltsWordMapper words;

    public EnglishStudyService(IeltsStudyProgressMapper progress, IeltsWordMapper words) {
        this.progress = progress;
        this.words = words;
    }

    public List<Progress> listProgress(long userId) {
        return progress.listProgress(userId);
    }

    public Progress record(long userId, long wordId, Rating rating, Integer expectedVersion) {
        if (rating == null || expectedVersion == null || expectedVersion < 0) {
            throw new ServiceException("学习进度参数不正确", 400);
        }

        IeltsWord word = words.selectById(wordId);
        if (word == null) {
            throw new ServiceException("词汇不存在", 404);
        }

        OffsetDateTime now = OffsetDateTime.now(STUDY_ZONE);
        if (rating == Rating.SEEN) {
            progress.introduceWord(userId, wordId, now);
            return Progress.from(word.getChapterId(), findProgress(userId, wordId));
        }

        IeltsStudyProgress current = findProgress(userId, wordId);
        if (current == null) {
            throw new ServiceException("请先学习这个单词", 409);
        }
        if (!expectedVersion.equals(current.getVersion())) {
            return Progress.from(word.getChapterId(), current);
        }

        boolean remembered = rating == Rating.REMEMBERED;
        if (remembered && current.getNextReviewAt().isAfter(now)) {
            return Progress.from(word.getChapterId(), current);
        }

        int step = remembered ? Math.min(current.getReviewStep() + 1, REVIEW_DAYS.size()) : 0;
        int days = remembered ? REVIEW_DAYS.get(step - 1) : 1;
        OffsetDateTime nextReview = now.toLocalDate().plusDays(days).atStartOfDay(STUDY_ZONE).toOffsetDateTime();
        progress.update(Wrappers.lambdaUpdate(IeltsStudyProgress.class)
                .eq(IeltsStudyProgress::getUserId, userId)
                .eq(IeltsStudyProgress::getWordId, wordId)
                .eq(IeltsStudyProgress::getVersion, expectedVersion)
                .set(IeltsStudyProgress::getReviewStep, step)
                .set(IeltsStudyProgress::getNextReviewAt, nextReview)
                .set(IeltsStudyProgress::getRating, rating.name())
                .set(IeltsStudyProgress::getVersion, expectedVersion + 1));
        return Progress.from(word.getChapterId(), findProgress(userId, wordId));
    }

    private IeltsStudyProgress findProgress(long userId, long wordId) {
        return progress.selectOne(Wrappers.lambdaQuery(IeltsStudyProgress.class)
                .eq(IeltsStudyProgress::getUserId, userId)
                .eq(IeltsStudyProgress::getWordId, wordId));
    }
}
