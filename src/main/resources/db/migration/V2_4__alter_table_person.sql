ALTER TABLE PERSON ADD COLUMN hendelse_id CHAR(36);

CREATE INDEX IX_PERSON_HENDELSE_ID on PERSON (hendelse_id);
