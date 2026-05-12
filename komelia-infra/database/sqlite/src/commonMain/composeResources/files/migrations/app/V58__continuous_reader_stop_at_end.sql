ALTER TABLE ImageReaderSettings
    ADD COLUMN continuous_reader_stop_at_end BOOLEAN DEFAULT 1 NOT NULL;
