CREATE TABLE TILFELLE_BIT_DELETED
(
    id                           SERIAL               PRIMARY KEY,
    uuid                         CHAR(36)             NOT NULL UNIQUE,
    created_at                   timestamptz          NOT NULL,
    inntruffet                   timestamptz          NOT NULL,
    personident                  CHAR(11)             NOT NULL,
    ressurs_id                   VARCHAR(51)          NOT NULL,
    tags                         TEXT                 NOT NULL,
    virksomhetsnummer            CHAR(9),
    fom                          DATE                 NOT NULL,
    tom                          DATE                 NOT NULL,
    ready                        BOOLEAN              NOT NULL DEFAULT TRUE,
    processed                    BOOLEAN              NOT NULL DEFAULT TRUE,
    korrigerer                   VARCHAR(50)
);

CREATE INDEX IX_TILFELLE_BIT_DELETED_PERSONIDENT on TILFELLE_BIT_DELETED (personident);
