-- ---------------------------------------------------------------------------
-- Discover: throw away what the old matcher decided
-- ---------------------------------------------------------------------------
-- The stored matches were produced by a resolver that could not work: it read a
-- base-36 id the API answers 405 to, it lost whole responses to a single null
-- field, and it accepted whatever the search ranked first. On the real library
-- that left 42 of 44 series recorded as "looked for, not found" -- and a
-- recorded miss is never retried, so every one of those would stay wrong
-- forever behind a resolver that now works.
--
-- Suggestions go too, since they were scored from those matches and carry none
-- of the detail a card now shows.
--
-- Dismissals are NOT touched. They are the user's own decisions about series
-- they never want to see again, and nothing about the matcher makes them wrong.

DELETE FROM DiscoverSourceMap;
DELETE FROM DiscoverSuggestions;

-- Clears the weekly throttle so the next foreground pass runs immediately
-- rather than waiting out a timer set by results that no longer exist.
UPDATE DiscoverScanState SET last_run_at = '', last_error = '';
