CREATE TABLE TILFELLE_BIT
(
    id                           SERIAL               PRIMARY KEY,
    uuid                         CHAR(36)             NOT NULL UNIQUE,
    created_at                   timestamptz          NOT NULL,
    inntruffet                   timestamptz          NOT NULL,
    personident                  CHAR(11)             NOT NULL,
    ressurs_id                   VARCHAR(50)          NOT NULL,
    tags                         TEXT                 NOT NULL,
    virksomhetsnummer            CHAR(9),
    fom                          DATE                 NOT NULL,
    tom                          DATE                 NOT NULL
);

CREATE INDEX IX_TILFELLE_BIT_PERSONIDENT on TILFELLE_BIT (personident);
