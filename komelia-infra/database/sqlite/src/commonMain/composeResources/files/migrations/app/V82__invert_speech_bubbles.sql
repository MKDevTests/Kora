-- ---------------------------------------------------------------------------
-- Accessibility: invert speech bubbles (v1.1.x)
-- ---------------------------------------------------------------------------
-- Opt-in reader option that detects speech bubbles on a page and inverts only
-- their pixels (white bubble + black text -> black bubble + white text),
-- leaving the artwork untouched. Reduces glare for light-sensitive readers.
-- Off by default: detection is heuristic and costs time per page.

ALTER TABLE ImageReaderSettings ADD COLUMN invert_speech_bubbles BOOLEAN DEFAULT 0 NOT NULL;
