-- NULL betyr at informasjonen ikke er hentet fra PDL (f.eks. kandidater opprettet før denne migreringen)
-- freg_status_sjekk inneholder samme utfall som Modia AO bruker (FregStatusSjekkResultat)
ALTER TABLE KANDIDAT_UTEN_ARBEIDSGIVER
    ADD COLUMN is_under_18 BOOLEAN,
    ADD COLUMN freg_status_sjekk TEXT;
