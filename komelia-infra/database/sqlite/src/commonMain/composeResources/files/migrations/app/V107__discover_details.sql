-- ---------------------------------------------------------------------------
-- Discover: what a suggestion card needs to be worth reading
-- ---------------------------------------------------------------------------
-- The recommendation payload carries a name, an image and a vote count, and
-- nothing else -- so the first cards showed a cover, a title, and no reason to
-- care. Everything below comes from one extra request per suggestion, measured
-- at 0.31s each, made during the weekly pass and never while anyone is looking.
--
-- That same request is what makes the duplicate check possible: it returns the
-- series' other names, and only those bridge a shelf that says "Parasite" to a
-- source that says "Kiseijuu".

ALTER TABLE DiscoverSuggestions ADD COLUMN description TEXT NOT NULL DEFAULT '';
ALTER TABLE DiscoverSuggestions ADD COLUMN genres TEXT NOT NULL DEFAULT '';
ALTER TABLE DiscoverSuggestions ADD COLUMN authors TEXT NOT NULL DEFAULT '';
ALTER TABLE DiscoverSuggestions ADD COLUMN publishers TEXT NOT NULL DEFAULT '';
-- Publication state as the source words it: "27 Volumes (Complete)".
ALTER TABLE DiscoverSuggestions ADD COLUMN status TEXT NOT NULL DEFAULT '';
-- A rating means nothing without the size of the vote: 8.78 over 4350 votes is
-- not 6.03 over 2.
ALTER TABLE DiscoverSuggestions ADD COLUMN rating_votes INTEGER NOT NULL DEFAULT 0;
