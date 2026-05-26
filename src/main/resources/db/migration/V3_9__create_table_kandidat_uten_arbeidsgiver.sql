CREATE TABLE KANDIDAT_UTEN_ARBEIDSGIVER
(
    id                  SERIAL          PRIMARY KEY,
    uuid                CHAR(36)        NOT NULL UNIQUE,
    created_at          timestamptz     NOT NULL,
    personident         CHAR(11)        NOT NULL,
    aktor_id            VARCHAR(20)     NOT NULL,
    referanse_id        VARCHAR(50),
    status              VARCHAR(10)     NOT NULL,
    next_processing_at  timestamptz     NOT NULL
);

CREATE INDEX IX_KANDIDAT_UTEN_ARBEIDSGIVER_PERSONIDENT ON KANDIDAT_UTEN_ARBEIDSGIVER (personident);
CREATE INDEX IX_KANDIDAT_UTEN_ARBEIDSGIVER_STATUS_NEXT ON KANDIDAT_UTEN_ARBEIDSGIVER (status, next_processing_at);
