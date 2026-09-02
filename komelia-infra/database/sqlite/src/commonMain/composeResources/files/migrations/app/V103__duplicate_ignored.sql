-- ---------------------------------------------------------------------------
-- Duplicate finder: dismissed pairs (v1.9)
-- ---------------------------------------------------------------------------
-- Pairs of series the admin has looked at and declared "not the same work".
--
-- A pair, not a group: dismissing one link inside a group of three has to be
-- able to split it into a pair and a loner, which a per-group row could not
-- express. pair_key is the two series ids sorted and joined by '|', so the row
-- does not depend on which of the two the sweep visited first.
--
-- No table for the duplicates themselves. They are recomputed from the
-- similarity index in about a tenth of a second on a full catalogue, and a
-- stored list would go stale on every library scan without anything noticing.

CREATE TABLE IF NOT EXISTS DuplicateIgnored (
    pair_key    TEXT PRIMARY KEY,
    ignored_at  TEXT NOT NULL DEFAULT ''
);
