-- -----------------------------------------------------------------------------
-- Real artwork for movies, imported from TMDB.
--
-- The initial schema called poster_hue "the placeholder gradient until real
-- artwork exists". This is that artwork. The hue column stays rather than being
-- replaced: it is the fallback the UI draws while the image loads, and the only
-- thing it has for a movie that was entered by hand through the admin API and
-- never got a poster.
-- -----------------------------------------------------------------------------

ALTER TABLE movies
    -- A fully-qualified image URL rather than TMDB's bare "/abc.jpg" path. The
    -- size segment (w500) is baked in at import time so the browser never has to
    -- know TMDB's URL grammar, and the column stays useful if the artwork later
    -- comes from somewhere else entirely.
    ADD COLUMN poster_url VARCHAR(500) NULL AFTER poster_hue,

    -- Nullable on purpose: the seeded and hand-entered movies have no TMDB
    -- identity and must not be forced to invent one. MySQL allows any number of
    -- NULLs in a UNIQUE index, so the constraint below still holds.
    ADD COLUMN tmdb_id BIGINT NULL AFTER poster_url,

    -- What makes re-running the import an update rather than ten new rows.
    ADD CONSTRAINT uq_movies_tmdb_id UNIQUE (tmdb_id);
