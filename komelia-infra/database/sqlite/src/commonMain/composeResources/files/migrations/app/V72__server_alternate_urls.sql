-- ---------------------------------------------------------------------------
-- Alternate server URLs for the same Komga server (v1.0.22)
-- ---------------------------------------------------------------------------
-- A single server profile can carry several URLs that reach the SAME server
-- (e.g. a LAN IP at home and a Tailscale address remotely). serverUrl is the
-- active one; the rest live here as a JSON string array. Switching the active
-- URL keeps the same server profile / per-server DB, so reading stats, ratings
-- and links stay unified across URLs. Defaults to an empty array.

ALTER TABLE AppSettings ADD COLUMN alternate_server_urls TEXT DEFAULT '[]' NOT NULL;
