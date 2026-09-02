-- ---------------------------------------------------------------------------
-- Series language in the similarity index (v1.9)
-- ---------------------------------------------------------------------------
-- The duplicate finder needs it: two series filed under the same title in one
-- library are not a duplicate when one is the French edition and the other the
-- English one. Nothing local held the language, so it was invisible to a sweep
-- that is not allowed to ask the server.
--
-- Nullable, and NULL means "no build has recorded it yet" rather than "no
-- language". An index built before this column keeps working; the finder simply
-- does not apply the language rule to a pair it cannot judge, and the duplicate
-- screen says how many series are still missing it.

ALTER TABLE SeriesSimilarityIndex ADD COLUMN language TEXT;
