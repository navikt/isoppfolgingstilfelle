CREATE TABLE TILFELLE_BIT_AVBRUTT
(
    id                           SERIAL               PRIMARY KEY,
    uuid                         CHAR(36)             NOT NULL UNIQUE,
    created_at                   timestamptz          NOT NULL,
    updated_at                   timestamptz          NOT NULL,
    tilfelle_bit_id              INTEGER REFERENCES TILFELLE_BIT (id) ON DELETE CASCADE,
    inntruffet                   timestamptz          NOT NULL,
    avbrutt                      BOOLEAN              NOT NULL
);

CREATE INDEX IX_TILFELLE_BIT_AVBRUTT_TILFELLE_BIT on TILFELLE_BIT_AVBRUTT (tilfelle_bit_id);

CREATE INDEX IX_TILFELLE_BIT_RESSURS_ID on TILFELLE_BIT (ressurs_id);
