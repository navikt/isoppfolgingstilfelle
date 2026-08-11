-- Vi mottok feil fom ved forlengelse av sykmelding (for langt tilbake)

-- 1) Rett fom paa de feilaktige bitene.
UPDATE TILFELLE_BIT SET fom='2026-07-16' WHERE uuid in ('aa2b5c69-a206-4a92-b814-d87cf50d223d', 'b1f10316-bcf6-4c5b-befd-1d19d9fa141c');

-- 2) Sett biten med nyeste inntruffet til processed=false slik at OppfolgingstilfelleCronjob
--    regenererer den GJELDENDE oppfolgingstilfelle_person-raden. Gjeldende rad velges av
--    "ORDER BY referanse_tilfelle_bit_inntruffet DESC", saa den nyeste biten maa reprosesseres
--    for at det korrigerte innholdet skal bli synlig i Syfomodia.
UPDATE TILFELLE_BIT SET processed=false WHERE uuid='4c854f52-2c14-4b8f-9f38-5c4f6a19dcb8';
