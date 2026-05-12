ALTER TABLE ImageReaderSettings
    ADD COLUMN paged_auto_skip_blank_pages BOOLEAN DEFAULT 0 NOT NULL;
