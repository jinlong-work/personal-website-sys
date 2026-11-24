package com.jinlong.web.controller.system;

import com.jinlong.common.core.domain.AjaxResult;
import com.jinlong.system.service.EnglishVocabularyService;
import com.jinlong.web.service.MinioIeltsAudioStore;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/english")
public class EnglishVocabularyController {
    private final EnglishVocabularyService vocabulary;
    private final MinioIeltsAudioStore audio;

    public EnglishVocabularyController(EnglishVocabularyService vocabulary, MinioIeltsAudioStore audio) {
        this.vocabulary = vocabulary;
        this.audio = audio;
    }

    @GetMapping("/chapters")
    public ResponseEntity<AjaxResult> chapters() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(vocabulary.listChapters()));
    }

    @GetMapping("/chapters/{chapterId}/words")
    public ResponseEntity<AjaxResult> words(@PathVariable long chapterId,
            @RequestParam(defaultValue = "1") long pageNum, @RequestParam(defaultValue = "48") long pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AjaxResult.success(vocabulary.listWords(chapterId, pageNum, pageSize)));
    }

    @GetMapping("/chapters/{chapterId}/playlist")
    public ResponseEntity<AjaxResult> playlist(@PathVariable long chapterId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(AjaxResult.success(vocabulary.playlist(chapterId)));
    }

    @GetMapping("/words/{wordId}/audio")
    public ResponseEntity<byte[]> audio(@PathVariable long wordId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(audio.read(vocabulary.audioKey(wordId)));
    }
}
