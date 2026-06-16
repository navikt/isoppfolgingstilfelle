-- =====================================================================================
-- diagnose_kandidater_bulk.sql
--
-- Bulk version of diagnose_kandidat.sql: takes the whole list of aktor_ids that SHOULD
-- have been sent but weren't, and produces ONE diagnosis row per aktor_id, so you can
-- group by cause instead of investigating one person at a time.
--
-- No psql-specific meta-commands are used, so this runs in psql, IntelliJ/DataGrip,
-- pgAdmin, or any other client.
--
-- STEG 1 — lag inputtabellen `forventet_kandidat` med aktor_id-ene fra CSV-fila.
--
--   IntelliJ / DataGrip (enklest):
--     Hoyreklikk skjemaet i Database-vinduet -> "Import Data from File...", velg
--     expected.csv, og kall tabellen `forventet_kandidat`. Da blir det en vanlig
--     tabell som overlever reconnect. Husk a droppe den naar du er ferdig.
--
--   IntelliJ uten import — lim inn ID-ene rett i konsollet:
--     CREATE TEMP TABLE forventet_kandidat (aktor_id VARCHAR(20));
--     INSERT INTO forventet_kandidat (aktor_id) VALUES
--         ('1000012345678'),
--         ('1000087654321');
--
--   psql:
--     CREATE TEMP TABLE forventet_kandidat (aktor_id VARCHAR(20));
--     \copy forventet_kandidat FROM 'expected.csv' WITH (FORMAT csv, HEADER true)
--
-- STEG 2 — kjor resten av denne fila.
--   NB (IntelliJ): TEMP-tabeller lever kun i konsollets sesjon. Hold samme konsoll
--   apent, og skru av "disconnect after each statement" i datasource-innstillingene.
--   Alternativt: bytt ut "CREATE TEMP TABLE" med "CREATE TABLE" under.
--
-- The gate numbering (G1-G7 for creation, S1-S5 for sending) is documented in
-- diagnose_kandidat.sql. Use that script to drill into a single aktor_id afterwards.
-- =====================================================================================


-- -------------------------------------------------------------------------------------
-- Main diagnosis: one row per aktor_id
-- -------------------------------------------------------------------------------------
DROP TABLE IF EXISTS kandidat_diagnose;
CREATE TEMP TABLE kandidat_diagnose AS
WITH forventet AS (
    SELECT DISTINCT trim(aktor_id) AS aktor_id
    FROM forventet_kandidat
    WHERE trim(coalesce(aktor_id, '')) <> ''
),
ident AS (
    SELECT f.aktor_id, a.personident
    FROM forventet f
    LEFT JOIN AKTORID a ON a.aktor_id = f.aktor_id
),
-- Every BEKREFTET sykmeldingbit for each person, paired with the oppfolgingstilfelle
-- that was computed from that exact bit (referanse_tilfelle_bit_uuid).
bekreftet_replay AS (
    SELECT
        i.aktor_id,
        tb.uuid       AS bit_uuid,
        tb.inntruffet AS bit_inntruffet,
        tb.tom        AS bit_tom,
        tb.processed,
        EXISTS (
            SELECT 1 FROM TILFELLE_BIT_AVBRUTT ab
            WHERE ab.tilfelle_bit_id = tb.id AND ab.avbrutt
        ) AS avbrutt,
        op.uuid       AS op_uuid,
        op.created_at AS tilfelle_beregnet_at,
        ((op.oppfolgingstilfeller -> -1) ->> 'start')::date                        AS tilfelle_start,
        ((op.oppfolgingstilfeller -> -1) ->> 'end')::date                          AS tilfelle_end,
        ((op.oppfolgingstilfeller -> -1) ->> 'arbeidstakerAtTilfelleEnd')::boolean AS arbeidstaker_at_end,
        coalesce(jsonb_array_length(op.oppfolgingstilfeller), 0)                   AS antall_tilfeller
    FROM ident i
    JOIN TILFELLE_BIT tb
      ON tb.personident = i.personident
     AND string_to_array(tb.tags, ',') @> ARRAY['SYKMELDING', 'BEKREFTET']
    LEFT JOIN OPPFOLGINGSTILFELLE_PERSON op
      ON op.referanse_tilfelle_bit_uuid = tb.uuid
),
-- Rank each bit by how far it got through the creation gates. Higher = further.
bekreftet_scored AS (
    SELECT
        r.*,
        CASE
            WHEN r.avbrutt                                    THEN 0
            WHEN NOT r.processed                              THEN 1
            WHEN r.op_uuid IS NULL                            THEN 2
            WHEN r.antall_tilfeller = 0                       THEN 3
            WHEN r.tilfelle_end < r.tilfelle_beregnet_at::date THEN 4  -- G3
            WHEN r.tilfelle_end <> r.bit_tom                  THEN 5  -- G4
            WHEN r.arbeidstaker_at_end                        THEN 6  -- G5
            ELSE 7                                                    -- alle tilfelle-gates ok
        END AS gate_score
    FROM bekreftet_replay r
),
best_bit AS (
    SELECT DISTINCT ON (aktor_id) *
    FROM bekreftet_scored
    ORDER BY aktor_id, gate_score DESC, bit_inntruffet DESC
),
-- Current state, as ModiaAOOversendingCronjob would see it right now
latest_op AS (
    SELECT DISTINCT ON (i.aktor_id)
        i.aktor_id,
        op.oppfolgingstilfeller
    FROM ident i
    JOIN OPPFOLGINGSTILFELLE_PERSON op ON op.personident = i.personident
    ORDER BY i.aktor_id, op.referanse_tilfelle_bit_inntruffet DESC, op.id DESC
),
latest_tilfelle AS (
    SELECT DISTINCT ON (l.aktor_id)
        l.aktor_id,
        (t ->> 'start')::date                        AS start,
        (t ->> 'end')::date                          AS "end",
        (t ->> 'arbeidstakerAtTilfelleEnd')::boolean AS arbeidstaker_at_end
    FROM latest_op l, jsonb_array_elements(l.oppfolgingstilfeller) AS t
    WHERE (t ->> 'start')::date < current_date + 1
    ORDER BY l.aktor_id, (t ->> 'start')::date DESC
),
kandidat AS (
    SELECT DISTINCT ON (k.aktor_id)
        k.aktor_id, k.uuid, k.status, k.tilfelle_start, k.next_processing_at,
        k.oversendt_at, k.created_at
    FROM KANDIDAT_UTEN_ARBEIDSGIVER k
    JOIN forventet f ON f.aktor_id = k.aktor_id
    ORDER BY k.aktor_id, (k.oversendt_at IS NOT NULL) DESC, k.created_at DESC
)
SELECT
    i.aktor_id,
    i.personident,
    k.uuid              AS kandidat_uuid,
    k.status            AS kandidat_status,
    k.tilfelle_start    AS kandidat_tilfelle_start,
    k.next_processing_at,
    k.oversendt_at,
    lt.start            AS naa_tilfelle_start,
    lt."end"            AS naa_tilfelle_end,
    lt.arbeidstaker_at_end AS naa_arbeidstaker_at_end,
    p.dodsdato,
    bb.gate_score,
    bb.bit_uuid         AS beste_bekreftet_bit,
    bb.bit_tom          AS bekreftet_bit_tom,
    bb.tilfelle_end     AS bekreftet_tilfelle_end,
    (SELECT count(*) FROM TILFELLE_BIT tb WHERE tb.personident = i.personident) AS antall_biter,
    CASE
        -- ---- Identitet -------------------------------------------------------------
        WHEN i.personident IS NULL AND NOT EXISTS (SELECT 1 FROM AKTORID a WHERE a.aktor_id = i.aktor_id)
            THEN 'A1_UKJENT_AKTORID'
        WHEN i.personident IS NULL
            THEN 'A2_MANGLER_PERSONIDENT_I_AKTORID'

        -- ---- Kandidat finnes: hvorfor ble den ikke sendt? --------------------------
        WHEN k.oversendt_at IS NOT NULL
            THEN 'S0_FAKTISK_OVERSENDT'
        WHEN k.uuid IS NOT NULL AND p.dodsdato IS NOT NULL
            THEN 'S1_DOD'
        WHEN k.uuid IS NOT NULL AND lt.start IS NULL
            THEN 'S1_INGEN_TILFELLE_NAA'
        WHEN k.uuid IS NOT NULL AND lt."end" + 16 < current_date
            THEN 'S1_TILFELLE_UTLOPT_MER_ENN_16_DAGER'
        WHEN k.uuid IS NOT NULL AND k.status = 'FERDIG' AND lt.arbeidstaker_at_end
            THEN 'S5_FIKK_ARBEIDSGIVER'
        WHEN k.uuid IS NOT NULL AND k.status = 'FERDIG'
            THEN 'S_FERDIG_UKJENT_AARSAK'
        WHEN k.uuid IS NOT NULL AND lt."end" < current_date
            THEN 'S2_TILFELLE_AVSLUTTET_UTSATT'
        WHEN k.uuid IS NOT NULL AND lt.start + 28 > current_date
            THEN 'S3_FOR_TIDLIG_UTSATT'
        WHEN k.uuid IS NOT NULL AND k.next_processing_at > now()
            THEN 'S_VENTER_PAA_NESTE_KJORING'
        WHEN k.uuid IS NOT NULL
            THEN 'S4_KLAR_FOR_OVERSENDING'

        -- ---- Ingen kandidat: hvilken creation-gate stoppet den? --------------------
        WHEN bb.aktor_id IS NULL
            THEN 'G1_INGEN_BEKREFTET_SYKMELDINGSBIT'
        WHEN bb.gate_score = 0 THEN 'G0_BEKREFTET_BIT_AVBRUTT'
        WHEN bb.gate_score = 1 THEN 'G0_BEKREFTET_BIT_IKKE_PROSESSERT'
        WHEN bb.gate_score = 2 THEN 'G0_BIT_UTLOSTE_ALDRI_TILFELLEBEREGNING'
        WHEN bb.gate_score = 3 THEN 'G2_TOM_TILFELLELISTE'
        WHEN bb.gate_score = 4 THEN 'G3_TILFELLE_IKKE_LOPENDE'
        WHEN bb.gate_score = 5 THEN 'G4_BIT_IKKE_SISTE_I_TILFELLE'
        WHEN bb.gate_score = 6 THEN 'G5_ARBEIDSTAKER_AT_TILFELLE_END'
        ELSE 'G6_G7_PDL_FEILET_ELLER_DUPLIKAT_BLOKKERTE'
    END AS aarsak
FROM ident i
LEFT JOIN kandidat k        ON k.aktor_id = i.aktor_id
LEFT JOIN best_bit bb       ON bb.aktor_id = i.aktor_id
LEFT JOIN latest_tilfelle lt ON lt.aktor_id = i.aktor_id
LEFT JOIN PERSON p          ON p.personident = i.personident;


-- -------------------------------------------------------------------------------------
-- Oppsummering: hvor mange per aarsak
-- -------------------------------------------------------------------------------------
-- === Fordeling per aarsak ===
SELECT
    aarsak,
    count(*)                                                   AS antall,
    round(100.0 * count(*) / sum(count(*)) OVER (), 1)         AS andel_prosent
FROM kandidat_diagnose
GROUP BY aarsak
ORDER BY antall DESC;


-- -------------------------------------------------------------------------------------
-- Detaljer, sortert etter aarsak
-- -------------------------------------------------------------------------------------
-- === Detaljer ===
SELECT
    aarsak,
    aktor_id,
    kandidat_status,
    kandidat_tilfelle_start,
    naa_tilfelle_start,
    naa_tilfelle_end,
    naa_arbeidstaker_at_end,
    next_processing_at,
    antall_biter,
    bekreftet_bit_tom,
    bekreftet_tilfelle_end
FROM kandidat_diagnose
ORDER BY aarsak, aktor_id;


-- -------------------------------------------------------------------------------------
-- Ekstra: tilfelle_start-drift. Kandidaten ble opprettet for ett tilfelle, men
-- ModiaAO evaluerer alltid personens NYESTE tilfelle. Naar disse ikke er like,
-- vurderes kandidaten mot feil tilfelle.
-- -------------------------------------------------------------------------------------
-- === Kandidater som vurderes mot et annet tilfelle enn de ble opprettet for ===
SELECT
    aktor_id,
    kandidat_status,
    kandidat_tilfelle_start,
    naa_tilfelle_start,
    naa_tilfelle_end,
    naa_tilfelle_start - kandidat_tilfelle_start AS dager_drift,
    aarsak
FROM kandidat_diagnose
WHERE kandidat_uuid IS NOT NULL
  AND naa_tilfelle_start IS NOT NULL
  AND naa_tilfelle_start <> kandidat_tilfelle_start
ORDER BY abs(naa_tilfelle_start - kandidat_tilfelle_start) DESC;


-- -------------------------------------------------------------------------------------
-- Ekstra: rekkefolge-analyse. For alle i lista uten kandidat, finn BEKREFTET-biter
-- som ble "overkjort" av en annen bit i samme tilfelle, og hvor tett i tid de kom.
-- Dette er den direkte testen av teorien om at bit-rekkefolgen paavirker opprettelse.
-- -------------------------------------------------------------------------------------
-- === Rekkefolge-analyse for G4 (bit ikke siste i tilfelle) ===
WITH kandidatlose AS (
    SELECT aktor_id, personident
    FROM kandidat_diagnose
    WHERE aarsak LIKE 'G4%' OR aarsak LIKE 'G5%'
),
bekreftet AS (
    SELECT kl.aktor_id, tb.*
    FROM kandidatlose kl
    JOIN TILFELLE_BIT tb
      ON tb.personident = kl.personident
     AND string_to_array(tb.tags, ',') @> ARRAY['SYKMELDING', 'BEKREFTET']
)
SELECT
    b.aktor_id,
    b.uuid           AS bekreftet_bit,
    b.inntruffet     AS bekreftet_inntruffet,
    b.tom            AS bekreftet_tom,
    other.tags       AS konkurrerende_tags,
    other.inntruffet AS konkurrerende_inntruffet,
    other.tom        AS konkurrerende_tom,
    other.virksomhetsnummer IS NOT NULL AS konkurrerende_har_arbeidsgiver,
    round(EXTRACT(EPOCH FROM (other.inntruffet - b.inntruffet)) / 60.0, 1) AS minutter_mellom,
    CASE
        WHEN other.inntruffet <= b.inntruffet THEN 'FOR'
        ELSE 'ETTER'
    END AS rekkefolge
FROM bekreftet b
JOIN TILFELLE_BIT other
  ON other.personident = b.personident
 AND other.uuid <> b.uuid
 AND other.tom > b.tom
 AND other.fom <= b.tom + 16
ORDER BY b.aktor_id, b.inntruffet, other.inntruffet;
