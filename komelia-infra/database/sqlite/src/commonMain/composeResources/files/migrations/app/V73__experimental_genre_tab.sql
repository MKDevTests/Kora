-- ---------------------------------------------------------------------------
-- Experimental: per-library Genre tab (v1.1.0)
-- ---------------------------------------------------------------------------
-- Master toggle for the experimental Genre tab, which groups a library's series
-- by their kora:genre:* Komga tags. Off by default; enabled in App Settings ->
-- Experimental. The genre catalog (cover / label / order / cached count) lives
-- in its own table added by a later migration; a genre's series are fetched
-- live by tag.

ALTER TABLE AppSettings ADD COLUMN experimental_genre_tab BOOLEAN DEFAULT 0 NOT NULL;
