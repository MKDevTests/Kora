-- ---------------------------------------------------------------------------
-- Series links: opt-in AniList online suggestions (v1.0.20)
-- ---------------------------------------------------------------------------
-- When enabled, the series "Links" tab can query the public AniList GraphQL
-- API to suggest related series (sequel / prequel / spin-off). Off by default
-- because it sends series titles to a third party — the user must opt in.

ALTER TABLE AppSettings
    ADD COLUMN anilist_link_suggestions_enabled BOOLEAN DEFAULT 0 NOT NULL;
