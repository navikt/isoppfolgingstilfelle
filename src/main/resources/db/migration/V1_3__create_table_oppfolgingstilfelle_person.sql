CREATE TABLE OPPFOLGINGSTILFELLE_PERSON
(
    id                                  SERIAL               PRIMARY KEY,
    uuid                                CHAR(36)             NOT NULL UNIQUE,
    created_at                          timestamptz          NOT NULL,
    personident                         CHAR(11)             NOT NULL,
    oppfolgingstilfeller                jsonb                NOT NULL,
    referanse_tilfelle_bit_uuid         VARCHAR(50)          NOT NULL,
    referanse_tilfelle_bit_inntruffet   timestamptz          NOT NULL
);

CREATE INDEX IX_OPPFOLGINGSTILFELLE_PERSON_PERSONIDENT on OPPFOLGINGSTILFELLE_PERSON (personident);
