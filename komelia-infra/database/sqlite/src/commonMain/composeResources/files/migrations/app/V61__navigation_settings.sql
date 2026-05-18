ALTER TABLE AppSettings
    ADD COLUMN library_dropdown_in_title BOOLEAN DEFAULT 1 NOT NULL;

ALTER TABLE AppSettings
    ADD COLUMN startup_screen TEXT DEFAULT 'HOME' NOT NULL;
