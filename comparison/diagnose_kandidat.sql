-- =====================================================================================
-- diagnose_kandidat.sql
--
-- Diagnose why a given aktor_id was NOT created as a SykmeldtUtenArbeidsgiver-kandidat,
-- or was created but never sent to Modia/AO (oversendt_at IS NULL).
--
-- The aktor_id is read from the session setting `diag.aktorid`. Set it once, then run
-- the rest of the file. This works in psql, IntelliJ/DataGrip, pgAdmin, and any other
-- client — no psql-specific meta-commands are used.
--
-- IntelliJ / DataGrip:
--     Open the file in a console bound to the right datasource, put the caret in the
--     SET statement below and hit Ctrl+Enter (or edit the value inline), then Execute
--     the whole file. Each section opens in its own result tab.
--     NB: the SET lives on the console's session, so keep the same console open, and
--     avoid "disconnect after each statement" in the datasource options. If you use
--     manual commit mode, a rollback resets the setting — just re-run the SET.
--
-- psql:
--     \i diagnose_kandidat.sql        (after editing the SET below)
--
-- Anywhere: you can also just replace current_setting('diag.aktorid') with a literal
-- '1000012345678' and run individual sections standalone.
--
-- -------------------------------------------------------------------------------------
-- The logic being replayed (see OppfolgingstilfelleCronjob + ModiaAOOversendingCronjob)
-- -------------------------------------------------------------------------------------
--
-- CREATION (OppfolgingstilfelleCronjob.lagreBekreftetKandidatHvisAktuell)
--   Runs once per *unprocessed* TILFELLE_BIT, in `inntruffet ASC, id ASC` order.
--   For the bit currently being processed ("incomingBit"), a kandidat is created only if
--   ALL of these hold:
--     G1  incomingBit.tags ⊇ {SYKMELDING, BEKREFTET}
--     G2  a tilfelle exists (oppfolgingstilfelleList not empty)
--     G3  latestTilfelle.end >= today          -- tilfelle still current
--     G4  latestTilfelle.end == incomingBit.tom -- incomingBit is the bit that ENDS the tilfelle
--     G5  NOT latestTilfelle.arbeidstakerAtTilfelleEnd
--     G6  PDL returns an aktorId
--     G7  no existing kandidat with same (personident, tilfelle_start)  [createIfMissing]
--
--   G4 is where the "bit ordering" theory lives: `latestTilfelle` is recomputed from ALL
--   processed bits + incomingBit. If a SENDT sykmelding (or any bit) with a later `tom`
--   was already processed, the tilfelle end moves past the BEKREFTET bit's tom and G4 fails.
--   Likewise a SENDT/INNTEKTSMELDING bit governing the last day flips G5.
--
--   Crucially: OPPFOLGINGSTILFELLE_PERSON.referanse_tilfelle_bit_uuid points back at the bit
--   that triggered it, and `oppfolgingstilfeller` is the tilfelle-list AS COMPUTED AT THAT
--   MOMENT. So section 4 can evaluate G2-G5 exactly as the app saw them, historically.
--
-- SENDING (ModiaAOOversendingCronjob)
--   Picks kandidater WHERE status IN ('NY','UTSATT') AND oversendt_at IS NULL
--                      AND next_processing_at <= NOW().
--   Evaluated against the person's CURRENT latest OPPFOLGINGSTILFELLE_PERSON row, taking
--   the tilfelle with the greatest `start` (among those with start < tomorrow):
--     S1  no tilfelle / dodsdato set / latestTilfelle.end + 16d < today  -> FERDIG (never sent)
--     S2  latestTilfelle.end < today                                    -> UTSATT (retry tomorrow)
--     S3  latestTilfelle.start + 28d > today                            -> UTSATT (too early)
--     S4  NOT latestTilfelle.arbeidstakerAtTilfelleEnd                  -> OVERSENDT ✅
--     S5  else (arbeidstakerAtTilfelleEnd)                              -> FERDIG (fikk jobb)
--
--   Note S1: a kandidat created more than 16 days after its tilfelle ended is immediately
--   ferdigstilt without ever being sent. This silently kills late/backfilled kandidater.
--   Note also that ModiaAO uses the person's *newest* tilfelle, which is not necessarily
--   the tilfelle the kandidat was created for (kandidat.tilfelle_start).
-- =====================================================================================

-- >>> SETT AKTOR_ID HER <<<
SET diag.aktorid = '1000012345678';


-- =====================================================================================
-- 0. IDENTITY — resolve aktor_id -> personident
--    If personident IS NULL here, AktoridCronjob/PDL never resolved it and every
--    downstream section will be empty. That alone is a valid explanation.
-- =====================================================================================
-- === 0. Identity ===
SELECT
    a.aktor_id,
    a.personident                                          AS personident_from_aktorid_table,
    k.personident                                          AS personident_from_kandidat,
    (SELECT count(*) FROM TILFELLE_BIT tb WHERE tb.personident = a.personident) AS antall_biter,
    p.dodsdato,
    CASE
        WHEN a.aktor_id IS NULL             THEN 'aktor_id finnes ikke i AKTORID-tabellen'
        WHEN a.personident IS NULL          THEN 'AKTORID.personident er NULL - PDL-oppslag mangler/feilet'
        WHEN p.dodsdato IS NOT NULL         THEN 'Person er registrert dod - ModiaAO ferdigstiller alltid'
        ELSE 'ok'
    END AS identity_verdict
FROM AKTORID a
LEFT JOIN KANDIDAT_UTEN_ARBEIDSGIVER k ON k.aktor_id = a.aktor_id
LEFT JOIN PERSON p                     ON p.personident = a.personident
WHERE a.aktor_id = current_setting('diag.aktorid');


-- =====================================================================================
-- 1. KANDIDAT-RADER — does a kandidat exist at all, and what state is it in?
-- =====================================================================================
-- === 1. Kandidat-rader ===
SELECT
    k.uuid,
    k.created_at,
    k.tilfelle_start,
    k.status,
    k.next_processing_at,
    k.oversendt_at,
    k.referanse_id,
    CASE
        WHEN k.oversendt_at IS NOT NULL              THEN 'OVERSENDT'
        WHEN k.status = 'FERDIG'                     THEN 'FERDIGSTILT UTEN OVERSENDING (se seksjon 5)'
        WHEN k.next_processing_at > now()            THEN 'VENTER - next_processing_at er i fremtiden'
        ELSE 'KLAR FOR PROSESSERING - burde vaert plukket opp av cronjob'
    END AS kandidat_verdict,
    -- Did another kandidat for the same tilfelle_start block creation? (G7)
    (SELECT count(*) - 1
     FROM KANDIDAT_UTEN_ARBEIDSGIVER k2
     WHERE k2.personident = k.personident AND k2.tilfelle_start = k.tilfelle_start
    ) AS andre_kandidater_samme_tilfelle_start
FROM KANDIDAT_UTEN_ARBEIDSGIVER k
WHERE k.aktor_id = current_setting('diag.aktorid')
ORDER BY k.created_at;


-- =====================================================================================
-- 2. BIT-TIDSLINJE — every tilfellebit for the person, in processing order.
--    This is where the "flere biter med minutter/timer mellom" theory is visible:
--    look for a SENDT/INNTEKTSMELDING bit with `tom` >= a BEKREFTET bit's `tom` that was
--    processed at (or before) the same time.
-- =====================================================================================
-- === 2. Bit-tidslinje ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
)
SELECT
    tb.id,
    tb.uuid,
    tb.inntruffet,
    tb.created_at,
    tb.ressurs_id,
    tb.tags,
    tb.fom,
    tb.tom,
    tb.virksomhetsnummer,
    tb.ready,
    tb.processed,
    tb.korrigerer,
    string_to_array(tb.tags, ',') @> ARRAY['SYKMELDING', 'BEKREFTET'] AS er_bekreftet_sykmelding,
    string_to_array(tb.tags, ',') @> ARRAY['SYKMELDING', 'SENDT']     AS er_sendt_sykmelding,
    EXISTS (
        SELECT 1 FROM TILFELLE_BIT_AVBRUTT ab
        WHERE ab.tilfelle_bit_id = tb.id AND ab.avbrutt
    ) AS avbrutt,
    -- Minutes until the next bit for this person — small gaps = same cronjob batch,
    -- which means ordering within the batch decides the outcome.
    round(EXTRACT(EPOCH FROM (
        lead(tb.inntruffet) OVER (ORDER BY tb.inntruffet, tb.id) - tb.inntruffet
    )) / 60.0, 1) AS minutter_til_neste_bit,
    -- Was a tilfelle actually computed from this bit?
    EXISTS (
        SELECT 1 FROM OPPFOLGINGSTILFELLE_PERSON op
        WHERE op.referanse_tilfelle_bit_uuid = tb.uuid
    ) AS ga_opphav_til_oppfolgingstilfelle
FROM TILFELLE_BIT tb, pident
WHERE tb.personident = pident.personident
ORDER BY tb.inntruffet, tb.id;


-- =====================================================================================
-- 3. SLETTEDE / AVBRUTTE BITER — tombstones and AVBRUTT sykmeldinger remove bits from
--    the tilfelle calculation entirely. A BEKREFTET bit that was later avbrutt/slettet
--    can never produce a kandidat.
-- =====================================================================================
-- === 3. Slettede og avbrutte biter ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
)
SELECT 'SLETTET' AS kilde, d.uuid, d.inntruffet, d.ressurs_id, d.tags, d.fom, d.tom, NULL::timestamptz AS avbrutt_inntruffet
FROM TILFELLE_BIT_DELETED d, pident
WHERE d.personident = pident.personident
UNION ALL
SELECT 'AVBRUTT', tb.uuid, tb.inntruffet, tb.ressurs_id, tb.tags, tb.fom, tb.tom, ab.inntruffet
FROM TILFELLE_BIT tb
JOIN TILFELLE_BIT_AVBRUTT ab ON ab.tilfelle_bit_id = tb.id AND ab.avbrutt, pident
WHERE tb.personident = pident.personident
ORDER BY inntruffet;


-- =====================================================================================
-- 4. ⭐ CREATION-GATE REPLAY — for every BEKREFTET sykmeldingbit, evaluate G1-G5 against
--    the oppfolgingstilfelle that was computed *from that very bit*.
--    `blokkerende_arsak` is the first gate that failed = why no kandidat was created.
-- =====================================================================================
-- === 4. Creation-gate replay per BEKREFTET-bit ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
),
bekreftet_bits AS (
    SELECT tb.*
    FROM TILFELLE_BIT tb, pident
    WHERE tb.personident = pident.personident
      AND string_to_array(tb.tags, ',') @> ARRAY['SYKMELDING', 'BEKREFTET']
),
replay AS (
    SELECT
        b.uuid          AS bit_uuid,
        b.inntruffet    AS bit_inntruffet,
        b.ressurs_id,
        b.tags,
        b.fom           AS bit_fom,
        b.tom           AS bit_tom,
        b.processed,
        op.uuid         AS oppfolgingstilfelle_person_uuid,
        op.created_at   AS tilfelle_beregnet_at,
        jsonb_array_length(op.oppfolgingstilfeller)                       AS antall_tilfeller,
        -- The app takes `oppfolgingstilfelleList.lastOrNull()`
        (op.oppfolgingstilfeller -> -1)                                   AS siste_tilfelle,
        ((op.oppfolgingstilfeller -> -1) ->> 'start')::date               AS tilfelle_start,
        ((op.oppfolgingstilfeller -> -1) ->> 'end')::date                 AS tilfelle_end,
        ((op.oppfolgingstilfeller -> -1) ->> 'arbeidstakerAtTilfelleEnd')::boolean AS arbeidstaker_at_end,
        EXISTS (SELECT 1 FROM TILFELLE_BIT_AVBRUTT ab WHERE ab.tilfelle_bit_id = b.id AND ab.avbrutt) AS avbrutt
    FROM bekreftet_bits b
    LEFT JOIN OPPFOLGINGSTILFELLE_PERSON op ON op.referanse_tilfelle_bit_uuid = b.uuid
)
SELECT
    r.*,
    -- The kandidat that (would have) resulted, if any
    k.uuid   AS kandidat_uuid,
    k.status AS kandidat_status,
    k.oversendt_at,
    CASE
        WHEN r.avbrutt
            THEN 'G0: biten er AVBRUTT - ekskludert fra tilfelleberegning'
        WHEN NOT r.processed
            THEN 'G0: biten er ikke prosessert enda (processed = false)'
        WHEN r.oppfolgingstilfelle_person_uuid IS NULL
            THEN 'G0/G2: ingen OPPFOLGINGSTILFELLE_PERSON ble laget fra denne biten - biten ble aldri prosessert som incomingBit'
        WHEN r.antall_tilfeller = 0
            THEN 'G2: tom oppfolgingstilfelle-liste'
        WHEN r.tilfelle_end < r.tilfelle_beregnet_at::date
            THEN format('G3: tilfelle var ikke lopende - end=%s < prosesseringsdato=%s',
                        r.tilfelle_end, r.tilfelle_beregnet_at::date)
        WHEN r.tilfelle_end <> r.bit_tom
            THEN format('G4: biten var ikke siste bit i tilfellet - bit.tom=%s men tilfelle.end=%s (en annen bit forlenget tilfellet)',
                        r.bit_tom, r.tilfelle_end)
        WHEN r.arbeidstaker_at_end
            THEN 'G5: arbeidstakerAtTilfelleEnd=true - en SENDT/INNTEKTSMELDING-bit styrer siste dag i tilfellet'
        WHEN k.uuid IS NULL
            THEN 'G6/G7: alle tilfelle-gates ok, men ingen kandidat finnes -> PDL-oppslag feilet (G6) eller kandidat fantes allerede for (personident, tilfelle_start) (G7)'
        ELSE 'OK - kandidat ble opprettet'
    END AS blokkerende_arsak
FROM replay r
LEFT JOIN KANDIDAT_UTEN_ARBEIDSGIVER k
       ON k.referanse_id = r.ressurs_id
      AND k.tilfelle_start = r.tilfelle_start
ORDER BY r.bit_inntruffet, r.tilfelle_beregnet_at;


-- =====================================================================================
-- 4b. G4-DETALJER — which bit actually "owns" the end of the tilfelle?
--     Lists, for each BEKREFTET bit, competing bits whose `tom` reaches further.
--     This is the direct evidence for the ordering/interleaving theory.
-- =====================================================================================
-- === 4b. Konkurrerende biter som forlenger tilfellet forbi BEKREFTET-biten ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
),
bekreftet_bits AS (
    SELECT tb.*
    FROM TILFELLE_BIT tb, pident
    WHERE tb.personident = pident.personident
      AND string_to_array(tb.tags, ',') @> ARRAY['SYKMELDING', 'BEKREFTET']
)
SELECT
    b.uuid        AS bekreftet_bit_uuid,
    b.inntruffet  AS bekreftet_inntruffet,
    b.tom         AS bekreftet_tom,
    other.uuid    AS konkurrerende_bit_uuid,
    other.inntruffet AS konkurrerende_inntruffet,
    other.tags    AS konkurrerende_tags,
    other.fom     AS konkurrerende_fom,
    other.tom     AS konkurrerende_tom,
    other.virksomhetsnummer,
    round(EXTRACT(EPOCH FROM (other.inntruffet - b.inntruffet)) / 60.0, 1) AS minutter_etter_bekreftet,
    CASE
        WHEN other.inntruffet <= b.inntruffet
            THEN 'Prosessert FOR den bekreftede biten -> tilfelle.end var allerede forbi bit.tom da G4 ble evaluert'
        ELSE 'Prosessert ETTER - kan ha forlenget tilfellet i en senere kjoring'
    END AS rekkefolge_effekt
FROM bekreftet_bits b
JOIN TILFELLE_BIT other
  ON other.personident = b.personident
 AND other.uuid <> b.uuid
 AND other.tom > b.tom
 AND other.fom <= b.tom + 16   -- close enough to be part of the same tilfelle
ORDER BY b.inntruffet, other.inntruffet;


-- =====================================================================================
-- 5. ⭐ SENDING-GATE REPLAY — evaluate S1-S5 against the person's CURRENT state,
--    exactly as ModiaAOOversendingCronjob would right now.
--    Explains kandidater that exist but have oversendt_at IS NULL.
-- =====================================================================================
-- === 5. Sending-gate replay (naavaerende tilstand) ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
),
latest_op AS (
    SELECT op.*
    FROM OPPFOLGINGSTILFELLE_PERSON op, pident
    WHERE op.personident = pident.personident
    ORDER BY op.referanse_tilfelle_bit_inntruffet DESC, op.id DESC
    LIMIT 1
),
-- ModiaAO: filter start < tomorrow, sort by start DESC, take first
latest_tilfelle AS (
    SELECT
        (t ->> 'start')::date                         AS start,
        (t ->> 'end')::date                           AS "end",
        (t ->> 'arbeidstakerAtTilfelleEnd')::boolean  AS arbeidstaker_at_end,
        t ->> 'virksomhetsnummerList'                 AS virksomheter,
        (t ->> 'antallSykedager')::int                AS antall_sykedager
    FROM latest_op, jsonb_array_elements(latest_op.oppfolgingstilfeller) AS t
    WHERE (t ->> 'start')::date < current_date + 1
    ORDER BY (t ->> 'start')::date DESC
    LIMIT 1
)
SELECT
    k.uuid              AS kandidat_uuid,
    k.status,
    k.tilfelle_start    AS kandidat_tilfelle_start,
    k.next_processing_at,
    k.oversendt_at,
    lt.start            AS naavaerende_tilfelle_start,
    lt."end"            AS naavaerende_tilfelle_end,
    lt.arbeidstaker_at_end,
    p.dodsdato,
    lt.start <> k.tilfelle_start AS tilfelle_start_drift,
    CASE
        WHEN k.oversendt_at IS NOT NULL
            THEN 'Allerede oversendt'
        WHEN k.status = 'FERDIG'
            THEN 'FERDIG uten oversending - se hvilken S-regel som traff under'
        WHEN k.next_processing_at > now()
            THEN format('Ikke forfalt enda - next_processing_at = %s', k.next_processing_at)
        ELSE 'Plukkes opp ved neste kjoring'
    END AS naavaerende_tilstand,
    CASE
        WHEN lt.start IS NULL
            THEN 'S1: ingen oppfolgingstilfelle -> markerFerdig (aldri oversendt)'
        WHEN p.dodsdato IS NOT NULL
            THEN 'S1: dodsdato satt -> markerFerdig (aldri oversendt)'
        WHEN lt."end" + 16 < current_date
            THEN format('S1: tilfellet ble avsluttet for lenge siden (end=%s, +16d < i dag) -> markerFerdig UTEN oversending', lt."end")
        WHEN lt."end" < current_date
            THEN format('S2: tilfellet er avsluttet men innenfor 16 dager (end=%s) -> UTSATT til i morgen', lt."end")
        WHEN lt.start + 28 > current_date
            THEN format('S3: for tidlig - tilfelle.start + 28d = %s > i dag -> UTSATT', lt.start + 28)
        WHEN NOT lt.arbeidstaker_at_end
            THEN 'S4: skulle blitt OVERSENDT ved neste kjoring ✅'
        ELSE 'S5: arbeidstakerAtTilfelleEnd = true (har arbeidsgiver) -> markerFerdig UTEN oversending'
    END AS sending_verdict
FROM KANDIDAT_UTEN_ARBEIDSGIVER k
CROSS JOIN pident
LEFT JOIN latest_tilfelle lt ON true
LEFT JOIN PERSON p ON p.personident = pident.personident
WHERE k.aktor_id = current_setting('diag.aktorid')
ORDER BY k.created_at;


-- =====================================================================================
-- 6. NAAVAERENDE OPPFOLGINGSTILFELLE-LISTE — full expansion of the newest
--    OPPFOLGINGSTILFELLE_PERSON row, for manual inspection.
-- =====================================================================================
-- === 6. Naavaerende oppfolgingstilfelle-liste ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
),
latest_op AS (
    SELECT op.*
    FROM OPPFOLGINGSTILFELLE_PERSON op, pident
    WHERE op.personident = pident.personident
    ORDER BY op.referanse_tilfelle_bit_inntruffet DESC, op.id DESC
    LIMIT 1
)
SELECT
    latest_op.uuid                                AS oppfolgingstilfelle_person_uuid,
    latest_op.created_at,
    latest_op.referanse_tilfelle_bit_uuid,
    latest_op.referanse_tilfelle_bit_inntruffet,
    ord                                           AS tilfelle_index,
    (t ->> 'start')::date                         AS start,
    (t ->> 'end')::date                           AS "end",
    (t ->> 'arbeidstakerAtTilfelleEnd')::boolean  AS arbeidstaker_at_end,
    (t ->> 'gradertAtTilfelleEnd')::boolean       AS gradert_at_end,
    (t ->> 'antallSykedager')::int                AS antall_sykedager,
    t ->> 'virksomhetsnummerList'                 AS virksomheter,
    (t ->> 'start')::date + 28                    AS tidligst_oversending,
    (t ->> 'end')::date + 16                      AS siste_frist_oversending
FROM latest_op,
     jsonb_array_elements(latest_op.oppfolgingstilfeller) WITH ORDINALITY AS x(t, ord)
ORDER BY ord;


-- =====================================================================================
-- 7. HISTORIKK — every OPPFOLGINGSTILFELLE_PERSON row for the person, with the last
--    tilfelle from each. Shows how the tilfelle end/arbeidstaker-flag moved over time
--    as bits arrived — i.e. whether a later bit "stole" the tilfelle end.
-- =====================================================================================
-- === 7. Historikk for oppfolgingstilfelle-beregninger ===
WITH pident AS (
    SELECT personident FROM AKTORID WHERE aktor_id = current_setting('diag.aktorid')
)
SELECT
    op.created_at,
    op.referanse_tilfelle_bit_inntruffet,
    tb.tags                                                                    AS utlosende_bit_tags,
    tb.fom                                                                     AS utlosende_bit_fom,
    tb.tom                                                                     AS utlosende_bit_tom,
    jsonb_array_length(op.oppfolgingstilfeller)                                AS antall_tilfeller,
    ((op.oppfolgingstilfeller -> -1) ->> 'start')::date                        AS siste_tilfelle_start,
    ((op.oppfolgingstilfeller -> -1) ->> 'end')::date                          AS siste_tilfelle_end,
    ((op.oppfolgingstilfeller -> -1) ->> 'arbeidstakerAtTilfelleEnd')::boolean AS arbeidstaker_at_end,
    ((op.oppfolgingstilfeller -> -1) ->> 'end')::date = tb.tom                 AS g4_oppfylt
FROM OPPFOLGINGSTILFELLE_PERSON op
LEFT JOIN TILFELLE_BIT tb ON tb.uuid = op.referanse_tilfelle_bit_uuid, pident
WHERE op.personident = pident.personident
ORDER BY op.created_at;
