-- ---------------------------------------------------------------------------
-- Discover: drop suggestions scored from the wrong list
-- ---------------------------------------------------------------------------
-- The source answers two recommendation lists. One is voted by readers and its
-- weights are vote counts, in the tens. The other is derived by the site from
-- tag overlap and its weights sit in the tens of thousands. The pass merged the
-- two and normalised both by a single maximum, so a voted link ended up worth
-- 0.26% of a tag-derived one -- measured 2026-09-10 on Akira, 49 against
-- 19176 -- and every stored suggestion came from the tag machine.
--
-- It showed: median rating 6.19 over a median of 8 votes, and Adult, Ecchi and
-- Harem as the three commonest genres on a shelf that is none of those.

DELETE FROM DiscoverSuggestions;

-- DiscoverSourceMap is deliberately KEPT. The resolver is not what was wrong
-- here: it matched 35 of 40 series correctly, and re-resolving them would spend
-- 35 requests to arrive at exactly the same answers.

-- Dismissals are NOT touched: they are the user's own decisions.

-- Clears the weekly throttle so the corrected pass runs at the next opening
-- rather than waiting out a timer set by results just deleted.
UPDATE DiscoverScanState SET last_run_at = '', last_error = '';
