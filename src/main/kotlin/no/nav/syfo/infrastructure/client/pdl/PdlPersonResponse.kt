package no.nav.syfo.infrastructure.client.pdl

import no.nav.syfo.domain.FregStatusSjekkResultat
import java.time.LocalDate

data class PdlPersonResponse(
    val data: PdlHentPerson?,
    val errors: List<PdlError>?,
)

data class PdlHentPerson(
    val hentPerson: PdlPerson?,
)

data class PdlPerson(
    val foedselsdato: List<PdlFoedselsdato> = emptyList(),
    val folkeregisterpersonstatus: List<PdlFolkeregisterpersonstatus> = emptyList(),
    val statsborgerskap: List<PdlStatsborgerskap> = emptyList(),
) {
    // Samme vurdering som Modia AO (veilarboppfolging): manglende fødselsdato tolkes som ikke under 18 år
    fun isUnder18(today: LocalDate = LocalDate.now()): Boolean =
        foedselsdato.firstOrNull()?.foedselsdato?.isAfter(today.minusYears(18)) ?: false

    // Samme vurdering som Modia AO (veilarboppfolging) gjør for å kunne starte oppfølging uten manuell godkjenning
    fun fregStatusSjekk(): FregStatusSjekkResultat {
        val euEllerEosBorger = statsborgerskap.any { EEA_LAND.contains(it.land.uppercase()) }
        val forenkletStatus = folkeregisterpersonstatus.firstOrNull()?.forenkletStatus
            ?: return FregStatusSjekkResultat.INGEN_STATUS_FOLKEREGISTERET

        return when (ForenkletFolkeregisterStatus.entries.find { it.name == forenkletStatus }) {
            ForenkletFolkeregisterStatus.bosattEtterFolkeregisterloven -> FregStatusSjekkResultat.FREG_STATUS_OK
            ForenkletFolkeregisterStatus.dNummer ->
                if (euEllerEosBorger) {
                    FregStatusSjekkResultat.FREG_STATUS_OK
                } else {
                    FregStatusSjekkResultat.FREG_STATUS_KREVER_MANUELL_GODKJENNING_PGA_DNUMMER_IKKE_EOS
                }
            ForenkletFolkeregisterStatus.forsvunnet,
            ForenkletFolkeregisterStatus.opphoert -> FregStatusSjekkResultat.IKKE_LOVLIG_OPPHOLD
            ForenkletFolkeregisterStatus.ikkeBosatt -> FregStatusSjekkResultat.FREG_STATUS_KREVER_MANUELL_GODKJENNING_PGA_IKKE_BOSATT
            ForenkletFolkeregisterStatus.doedIFolkeregisteret -> FregStatusSjekkResultat.DOD
            null -> FregStatusSjekkResultat.UKJENT_STATUS_FOLKEREGISTERET
        }
    }
}

data class PdlFoedselsdato(
    val foedselsdato: LocalDate?,
)

data class PdlFolkeregisterpersonstatus(
    val forenkletStatus: String,
)

data class PdlStatsborgerskap(
    val land: String,
)

@Suppress("EnumEntryName")
enum class ForenkletFolkeregisterStatus {
    bosattEtterFolkeregisterloven,
    ikkeBosatt,
    forsvunnet,
    doedIFolkeregisteret,
    opphoert,
    dNummer,
}

// Speiler listen i veilarboppfolging (Modia AO)
val EEA_LAND = setOf(
    "BEL", "BGR", "DNK", "EST", "FIN", "FRA", "GRC", "IRL", "ISL", "ITA", "HRV", "CYP", "LVA", "LIE", "LTU", "LUX",
    "MLT", "NLD", "NOR", "POL", "PRT", "ROU", "SVK", "SVN", "ESP", "SWE", "CZE", "DEU", "HUN", "AUT", "CHE",
)
