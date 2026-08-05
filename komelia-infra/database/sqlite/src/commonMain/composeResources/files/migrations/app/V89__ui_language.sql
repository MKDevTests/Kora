-- ---------------------------------------------------------------------------
-- Interface language (v1.4.x)
-- ---------------------------------------------------------------------------
-- Which language the interface is written in, independently of the device.
--
-- Empty means "follow the system", which is what every install did until now
-- and stays the default: a French device already gets French where a
-- translation exists, and nothing changes for anyone else. An explicit value
-- ("en", "fr") pins the interface regardless of the system locale — the point
-- of the setting, since a French reader may well run an English phone.

ALTER TABLE AppSettings
    ADD COLUMN ui_language TEXT DEFAULT '' NOT NULL;
