CREATE TABLE OPPFOLGINGSTILFELLE_ARBEIDSTAKER
(
    id                                  SERIAL               PRIMARY KEY,
    uuid                                VARCHAR(50)          NOT NULL UNIQUE,
    created_at                          timestamptz          NOT NULL,
    personident                         VARCHAR(11)          NOT NULL,
    oppfolgingstilfeller                jsonb                NOT NULL,
    referanse_tilfelle_bit_uuid         VARCHAR(50)          NOT NULL UNIQUE,
    referanse_tilfelle_bit_inntruffet   timestamptz          NOT NULL
);

CREATE INDEX IX_OPPFOLGINGSTILFELLE_ARBEIDSTAKER_PERSONIDENT on OPPFOLGINGSTILFELLE_ARBEIDSTAKER (personident);
