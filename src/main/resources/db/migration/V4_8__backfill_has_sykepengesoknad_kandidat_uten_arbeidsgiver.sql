-- Backfill has_sykepengesoknad for kandidater created before this flag existed.
-- A sykepengesoknad bit belongs to the kandidat's tilfelle if it occurred on or
-- after tilfelle_start, so it is enough to check for its existence per personident.
UPDATE KANDIDAT_UTEN_ARBEIDSGIVER k
SET has_sykepengesoknad = TRUE
WHERE NOT k.has_sykepengesoknad
  AND EXISTS (
    SELECT 1
    FROM TILFELLE_BIT t
    WHERE t.personident = k.personident
      AND t.tags LIKE '%SYKEPENGESOKNAD%'
      AND t.tom >= k.tilfelle_start
  );
