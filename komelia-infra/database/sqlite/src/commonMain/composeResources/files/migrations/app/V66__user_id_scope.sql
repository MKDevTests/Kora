-- ---------------------------------------------------------------------------
-- Account-scoping for reading_events + series_ratings (v1.0.10)
-- ---------------------------------------------------------------------------
-- Up to v1.0.9, reading_events and series_ratings were keyed only by
-- (book_id, event_type) and (series_id) respectively — i.e. device-wide
-- and effectively "whatever Komga user is signed in right now owns the
-- data". For users with multiple Komga servers or multiple accounts on
-- the same server, that produces mixed/overwritten data.
--
-- We tag every row with the Komga server-issued user UUID (KomgaUser.id)
-- so:
--   - The same physical Komga reachable via LAN IP vs Tailscale yields
--     the same user_id and the same data (this was already true by
--     accident because Komga's book IDs are URL-invariant; we now also
--     make it true by design for the rare cases where it wasn't).
--   - A genuinely different Komga server / account gets its own slice.
--
-- Nullability + PK trade-off:
--   - The columns are NULLABLE so we don't crash older rows on the very
--     first boot after upgrade. A separate one-shot backfill job
--     (see app-side, post-upgrade first-auth handler) tags those NULL
--     rows with the currently-connected user's id.
--   - The PRIMARY KEYs stay as before: (book_id, event_type) and
--     (series_id). True multi-user concurrent rows on the same key would
--     require composite PKs (book_id, event_type, user_id) and
--     (series_id, user_id), but that means a destructive SQLite-style
--     table recreate which we defer. For Mathieu's single-account use
--     case this is moot; if a second user signs in on the same device,
--     their writes overwrite the first user's same-key rows on this
--     device (documented edge case, not a Komga-side data loss).

ALTER TABLE reading_events
    ADD COLUMN komga_user_id TEXT;

ALTER TABLE series_ratings
    ADD COLUMN komga_user_id TEXT;

-- User-first composite indexes so the per-user filtered queries scan
-- only the slice they need rather than the whole table.
CREATE INDEX IF NOT EXISTS idx_reading_events_user_type_time
    ON reading_events (komga_user_id, event_type, timestamp);

CREATE INDEX IF NOT EXISTS idx_series_ratings_user_stars_time
    ON series_ratings (komga_user_id, stars DESC, rated_at DESC);
