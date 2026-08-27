-- Backfill has_sykepengesoknad for kandidater created before this flag existed.
-- For each kandidat, use the newest OPPFOLGINGSTILFELLE_PERSON snapshot for that
-- personident, find the tilfelle matching tilfelle_start, and check if a
-- sykepengesoknad bit overlaps that tilfelle's date range.
UPDATE KANDIDAT_UTEN_ARBEIDSGIVER k
SET has_sykepengesoknad = TRUE
WHERE NOT k.has_sykepengesoknad
  AND EXISTS (
    SELECT 1
    FROM jsonb_array_elements(
        (
            SELECT oppfolgingstilfeller
            FROM OPPFOLGINGSTILFELLE_PERSON
            WHERE personident = k.personident
            ORDER BY created_at DESC
            LIMIT 1
        )
    ) AS tilfelle
    WHERE (tilfelle ->> 'start')::date = k.tilfelle_start
      AND EXISTS (
        SELECT 1
        FROM TILFELLE_BIT t
        WHERE t.personident = k.personident
          AND t.tags LIKE '%SYKEPENGESOKNAD%'
          AND t.fom <= (tilfelle ->> 'end')::date
          AND t.tom >= (tilfelle ->> 'start')::date
      )
  );
