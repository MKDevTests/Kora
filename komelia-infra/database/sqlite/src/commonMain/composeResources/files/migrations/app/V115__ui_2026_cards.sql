-- UI 2026: title under the cover, rounder corners, a deeper shadow.
-- Radius and shadow only move where they still hold the old defaults,
-- so a value someone tuned by hand is kept. The layout flag has no way
-- to tell "chose overlay" from "never touched it": everyone moves, and
-- Settings > Appearance turns it back.
UPDATE AppSettings SET card_layout_below = 1;
UPDATE AppSettings SET card_corner_radius = 12.0 WHERE card_corner_radius = 8.0;
UPDATE AppSettings SET card_shadow_level = 6.0 WHERE card_shadow_level = 2.0;
