CREATE TABLE TILFELLE_BIT
(
    id                           SERIAL               PRIMARY KEY,
    uuid                         VARCHAR(50)          NOT NULL UNIQUE,
    created_at                   timestamptz          NOT NULL,
    inntruffet                   timestamptz          NOT NULL,
    personident                  VARCHAR(11)          NOT NULL,
    ressurs_id                   VARCHAR(50)          NOT NULL,
    tags                         TEXT                 NOT NULL,
    virksomhetsnummer            VARCHAR(9)           NOT NULL,
    fom                          timestamptz          NOT NULL,
    tom                          timestamptz          NOT NULL
);

CREATE INDEX IX_TILFELLE_BIT_PERSONIDENT on TILFELLE_BIT (personident);
