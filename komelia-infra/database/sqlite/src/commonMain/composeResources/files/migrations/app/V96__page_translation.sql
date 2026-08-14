ALTER TABLE ImageReaderSettings ADD COLUMN translation_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ImageReaderSettings ADD COLUMN translation_source TEXT NOT NULL DEFAULT 'ENGLISH';
ALTER TABLE ImageReaderSettings ADD COLUMN translation_target TEXT NOT NULL DEFAULT 'FRENCH';
