-- ---------------------------------------------------------------------------
-- Complete-series card badge color (v1.1.x)
-- ---------------------------------------------------------------------------
-- Recolors the top-right series card badge (normally the unread count) when
-- the series is complete: status Ended and every volume owned. On by default.

ALTER TABLE AppSettings ADD COLUMN show_complete_series_badge BOOLEAN DEFAULT 1 NOT NULL;
