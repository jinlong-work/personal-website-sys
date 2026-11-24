package com.jinlong.system.domain.english;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ielts_word")
public class IeltsWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer sortOrder;
    private String word;
    private String phonetic;
    private String partOfSpeech;
    private String meaningZh;
    private String frequency;
    private String root;
    private String exampleEn;
    private String exampleZh;
    private String extra;
    private String audioObjectKey;

    public boolean hasAudio() {
        return audioObjectKey != null && !audioObjectKey.isBlank();
    }

    public record Card(long id, String word, String phonetic, String partOfSpeech, String meaningZh,
            String frequency, String root, String exampleEn, String exampleZh, String extra, boolean hasAudio) {
        public static Card from(IeltsWord word) {
            return new Card(word.getId(), word.getWord(), word.getPhonetic(), word.getPartOfSpeech(),
                    word.getMeaningZh(), word.getFrequency(), word.getRoot(), word.getExampleEn(),
                    word.getExampleZh(), word.getExtra(), word.hasAudio());
        }
    }

    public record Track(long id, String word) {}
}
