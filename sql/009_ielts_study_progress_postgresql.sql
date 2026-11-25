BEGIN;

CREATE TABLE IF NOT EXISTS public.ielts_study_progress (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES public.sys_user(user_id) ON DELETE CASCADE,
    word_id bigint NOT NULL REFERENCES public.ielts_word(id) ON DELETE CASCADE,
    started_at timestamptz NOT NULL,
    next_review_at timestamptz NOT NULL,
    review_step integer NOT NULL DEFAULT 0 CHECK (review_step BETWEEN 0 AND 4),
    rating text NOT NULL CHECK (rating IN ('SEEN', 'REMEMBERED', 'UNSURE', 'FORGOT')),
    version integer NOT NULL DEFAULT 1 CHECK (version > 0),
    UNIQUE (user_id, word_id)
);

COMMIT;
