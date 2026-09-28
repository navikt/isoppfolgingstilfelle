package no.nav.syfo.domain

/*
 * Speiler utfallene Modia AO (veilarboppfolging) bruker for å avgjøre om oppfølging kan startes
 * basert på folkeregisterstatus. Kun FREG_STATUS_OK kan starte oppfølging uten manuell godkjenning.
 */
enum class FregStatusSjekkResultat {
    FREG_STATUS_OK,
    FREG_STATUS_KREVER_MANUELL_GODKJENNING_PGA_DNUMMER_IKKE_EOS,
    FREG_STATUS_KREVER_MANUELL_GODKJENNING_PGA_IKKE_BOSATT,
    IKKE_LOVLIG_OPPHOLD,
    DOD,
    UKJENT_STATUS_FOLKEREGISTERET,
    INGEN_STATUS_FOLKEREGISTERET,
}
