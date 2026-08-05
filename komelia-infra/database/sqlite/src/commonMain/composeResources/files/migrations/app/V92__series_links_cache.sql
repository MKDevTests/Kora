-- ---------------------------------------------------------------------------
-- Series links, remembered (v1.4.x)
-- ---------------------------------------------------------------------------
-- The resolved "Links" tab of a series: the other versions and the related
-- series, as the server described them last time.
--
-- The tab resolves every linked id with its own request. Measured against this
-- server that is one to three seconds each, and they were all fired at once, so
-- a series with several links took over ten seconds to show anything — every
-- time, since nothing was kept between app starts.
--
-- Stored resolved rather than as ids, for the same reason as the keep-reading
-- row: reading ids back would mean the requests we are avoiding.

CREATE TABLE IF NOT EXISTS SeriesLinksCache (
    series_id  TEXT PRIMARY KEY,
    links_json TEXT NOT NULL DEFAULT '{}',
    updated_at TEXT NOT NULL DEFAULT ''
);
