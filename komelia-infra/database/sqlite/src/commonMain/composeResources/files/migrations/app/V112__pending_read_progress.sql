-- ---------------------------------------------------------------------------
-- Read progress the server has not acknowledged yet
-- ---------------------------------------------------------------------------
-- Measured 2026-09-13 with the Wi-Fi cut during a reading session:
-- "[ReadProgress] push FAILED page=286", then "page=293". A push that fails
-- was logged and forgotten; if the reader stopped there, the last position
-- never reached Komga. One row per book: the latest page, the moment it was
-- reached (sent as the progression's `modified` so a later device wins), and
-- the total the page was counted against. Dropped as soon as a push for that
-- book goes through.
CREATE TABLE PendingReadProgress (
    book_id     TEXT    NOT NULL PRIMARY KEY,
    page        INTEGER NOT NULL,
    total_pages INTEGER NOT NULL,
    modified    TEXT    NOT NULL
);
