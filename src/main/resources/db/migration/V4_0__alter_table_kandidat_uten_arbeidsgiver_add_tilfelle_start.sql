ALTER TABLE KANDIDAT_UTEN_ARBEIDSGIVER
    ADD COLUMN tilfelle_start DATE;

UPDATE KANDIDAT_UTEN_ARBEIDSGIVER k
SET tilfelle_start = COALESCE(
    (
        SELECT (tilfelle->>'start')::date
        FROM OPPFOLGINGSTILFELLE_PERSON op,
             jsonb_array_elements(op.oppfolgingstilfeller) AS tilfelle
        WHERE op.personident = k.personident
          AND op.created_at <= k.created_at
        ORDER BY op.created_at DESC, (tilfelle->>'start')::date DESC
        LIMIT 1
    ),
    k.created_at::date
)
WHERE tilfelle_start IS NULL;

ALTER TABLE KANDIDAT_UTEN_ARBEIDSGIVER
    ALTER COLUMN tilfelle_start SET NOT NULL;
