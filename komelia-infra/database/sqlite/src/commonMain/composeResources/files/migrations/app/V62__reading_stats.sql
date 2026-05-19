-- ---------------------------------------------------------------------------
-- Reading Stats feature (v1.0.3) — Option B: hybrid (Komga API + local log)
-- ---------------------------------------------------------------------------
-- Two pieces:
--   1. App-level toggles for showing the Stats surface and the bottom-nav
--      shortcut (independent: a user can keep the feature but hide the nav
--      button, or vice-versa).
--   2. A small append-only event log capturing book completions. Komga
--      itself doesn't expose a server-side "completed-at" timestamp via
--      query filters, so we log our own when the user marks a book as read
--      from any code path (reader end-of-book, manual mark, bulk action,
--      offline sync, local files). The log is the source of truth for the
--      time-bounded stats (last 7/30 days, streak, monthly chart). Lifetime
--      totals still come from Komga API counts (exact). The log only fills
--      from feature install onward — there is no backfill of past
--      completions (Komga does not provide the data needed).
--
-- The events table is intentionally minimal: bookId + eventType +
-- timestamp. eventType is an enum-as-string to make future extensions
-- (e.g. SERIES_FINISHED, BOOK_STARTED) cheap. PRIMARY KEY prevents
-- double-logging of the same completion under any retry/idempotent path.

ALTER TABLE AppSettings
    ADD COLUMN stats_enabled BOOLEAN DEFAULT 1 NOT NULL;

ALTER TABLE AppSettings
    ADD COLUMN stats_in_bottom_nav BOOLEAN DEFAULT 0 NOT NULL;

CREATE TABLE IF NOT EXISTS reading_events (
    book_id    TEXT    NOT NULL,
    event_type TEXT    NOT NULL,
    timestamp  INTEGER NOT NULL,
    PRIMARY KEY (book_id, event_type)
);

-- Partial indexes restrict to the data we actually query, keeping the
-- index pages small on libraries with tens of thousands of books.
CREATE INDEX IF NOT EXISTS idx_reading_events_type_time
    ON reading_events (event_type, timestamp);

CREATE INDEX IF NOT EXISTS idx_reading_events_time
    ON reading_events (timestamp);
