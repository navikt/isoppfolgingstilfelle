ALTER TABLE TILFELLE_BIT
ADD COLUMN IF NOT EXISTS ready BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS processed BOOLEAN NOT NULL DEFAULT TRUE;
