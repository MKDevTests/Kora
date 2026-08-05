-- ---------------------------------------------------------------------------
-- Suggestion feedback (v1.4.x)
-- ---------------------------------------------------------------------------
-- Series the user answered "not interested" to.
--
-- Two effects, both needed: the series never comes back as a suggestion, and
-- what it is made of loses weight in the taste profile. A dismissal that only
-- hid one cover would have to be repeated on every near-identical series the
-- same terms produce.

CREATE TABLE IF NOT EXISTS SuggestionDismissed (
    series_id   TEXT PRIMARY KEY,
    dismissed_at TEXT NOT NULL DEFAULT ''
);
