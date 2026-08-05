-- ---------------------------------------------------------------------------
-- Keep reading, remembered per library (v1.4.x)
-- ---------------------------------------------------------------------------
-- The books in progress shown at the top of a library.
--
-- Measured on the real server: the request alone takes 1 to 3.4 seconds, and
-- until it answers the row is simply absent — the most visible hole on the
-- screen, right where the user was going to tap. Remembering the last answer
-- lets the row be drawn with the screen; the refresh replaces it in place.
--
-- Stored as the serialized book list rather than ids: reading ids back would
-- mean another request, which is the thing being avoided.

CREATE TABLE IF NOT EXISTS KeepReadingCache (
    library_id TEXT PRIMARY KEY,
    books_json TEXT NOT NULL DEFAULT '[]',
    updated_at TEXT NOT NULL DEFAULT ''
);
