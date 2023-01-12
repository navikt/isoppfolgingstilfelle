CREATE TABLE PERSON
(
    id                                  SERIAL               PRIMARY KEY,
    uuid                                CHAR(36)             NOT NULL UNIQUE,
    created_at                          timestamptz          NOT NULL,
    personident                         CHAR(11)             NOT NULL UNIQUE,
    dodsdato                            DATE
);
