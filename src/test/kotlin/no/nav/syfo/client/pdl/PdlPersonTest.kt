package no.nav.syfo.client.pdl

import no.nav.syfo.domain.FregStatusSjekkResultat
import no.nav.syfo.infrastructure.client.pdl.ForenkletFolkeregisterStatus
import no.nav.syfo.infrastructure.client.pdl.PdlFoedselsdato
import no.nav.syfo.infrastructure.client.pdl.PdlFolkeregisterpersonstatus
import no.nav.syfo.infrastructure.client.pdl.PdlPerson
import no.nav.syfo.infrastructure.client.pdl.PdlStatsborgerskap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PdlPersonTest {

    private val today = LocalDate.now()

    private fun person(
        foedselsdato: LocalDate? = today.minusYears(30),
        status: String? = ForenkletFolkeregisterStatus.bosattEtterFolkeregisterloven.name,
        statsborgerskap: List<String> = listOf("NOR"),
    ) = PdlPerson(
        foedselsdato = listOf(PdlFoedselsdato(foedselsdato)),
        folkeregisterpersonstatus = listOfNotNull(status?.let { PdlFolkeregisterpersonstatus(it) }),
        statsborgerskap = statsborgerskap.map { PdlStatsborgerskap(it) },
    )

    @Test
    fun `isUnder18 is true until 18th birthday`() {
        assertTrue(person(foedselsdato = today.minusYears(18).plusDays(1)).isUnder18(today))
        assertFalse(person(foedselsdato = today.minusYears(18)).isUnder18(today))
    }

    @Test
    fun `isUnder18 is false when foedselsdato is missing`() {
        assertFalse(PdlPerson().isUnder18(today))
        assertFalse(person(foedselsdato = null).isUnder18(today))
    }

    @Test
    fun `fregStatusSjekk is OK when bosatt etter folkeregisterloven`() {
        assertEquals(FregStatusSjekkResultat.FREG_STATUS_OK, person().fregStatusSjekk())
    }

    @Test
    fun `fregStatusSjekk for dNummer depends on EU-EOS statsborgerskap`() {
        val dNummer = ForenkletFolkeregisterStatus.dNummer.name
        assertEquals(
            FregStatusSjekkResultat.FREG_STATUS_OK,
            person(status = dNummer, statsborgerskap = listOf("SWE")).fregStatusSjekk(),
        )
        assertEquals(
            FregStatusSjekkResultat.FREG_STATUS_OK,
            person(status = dNummer, statsborgerskap = listOf("USA", "DEU")).fregStatusSjekk(),
        )
        assertEquals(
            FregStatusSjekkResultat.FREG_STATUS_KREVER_MANUELL_GODKJENNING_PGA_DNUMMER_IKKE_EOS,
            person(status = dNummer, statsborgerskap = listOf("USA")).fregStatusSjekk(),
        )
    }

    @Test
    fun `fregStatusSjekk maps remaining statuses like Modia AO`() {
        mapOf(
            ForenkletFolkeregisterStatus.forsvunnet.name to FregStatusSjekkResultat.IKKE_LOVLIG_OPPHOLD,
            ForenkletFolkeregisterStatus.opphoert.name to FregStatusSjekkResultat.IKKE_LOVLIG_OPPHOLD,
            ForenkletFolkeregisterStatus.ikkeBosatt.name to FregStatusSjekkResultat.FREG_STATUS_KREVER_MANUELL_GODKJENNING_PGA_IKKE_BOSATT,
            ForenkletFolkeregisterStatus.doedIFolkeregisteret.name to FregStatusSjekkResultat.DOD,
            "nyStatusFraPdl" to FregStatusSjekkResultat.UKJENT_STATUS_FOLKEREGISTERET,
            null to FregStatusSjekkResultat.INGEN_STATUS_FOLKEREGISTERET,
        ).forEach { (status, expected) ->
            assertEquals(expected, person(status = status).fregStatusSjekk(), "status: $status")
        }
    }
}
