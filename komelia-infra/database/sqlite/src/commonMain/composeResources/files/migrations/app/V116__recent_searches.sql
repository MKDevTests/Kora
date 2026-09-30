-- Search tab history (Refonte 2): the last queries that led to a result, newest first.
ALTER TABLE AppSettings
    ADD COLUMN recent_searches TEXT DEFAULT '[]' NOT NULL;
