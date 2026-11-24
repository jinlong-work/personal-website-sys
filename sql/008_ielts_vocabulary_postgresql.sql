BEGIN;

CREATE TABLE IF NOT EXISTS public.ielts_chapter (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_chapter_id integer NOT NULL UNIQUE CHECK (source_chapter_id > 0),
    title text NOT NULL,
    sort_order integer NOT NULL CHECK (sort_order > 0),
    source_url text NOT NULL,
    source_version text NOT NULL,
    imported_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.ielts_word (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chapter_id bigint NOT NULL REFERENCES public.ielts_chapter(id),
    source_word_id integer NOT NULL CHECK (source_word_id > 0),
    sort_order integer NOT NULL CHECK (sort_order > 0),
    word text NOT NULL,
    phonetic text,
    part_of_speech text NOT NULL,
    meaning_zh text NOT NULL,
    frequency text NOT NULL,
    root text,
    example_en text NOT NULL,
    example_zh text NOT NULL,
    extra text,
    UNIQUE (chapter_id, source_word_id)
);

ALTER TABLE public.ielts_word ADD COLUMN IF NOT EXISTS audio_object_key text;

CREATE INDEX IF NOT EXISTS idx_ielts_word_chapter_sort ON public.ielts_word (chapter_id, sort_order, id);

COMMIT;
