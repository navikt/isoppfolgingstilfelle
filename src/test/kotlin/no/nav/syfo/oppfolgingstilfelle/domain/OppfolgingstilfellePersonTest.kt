package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.calculateCurrentVarighetUker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import testhelper.generator.generateOppfolgingstilfelle
import testhelper.generator.generateOppfolgingstilfellePerson
import java.time.LocalDate

class OppfolgingstilfellePersonTest {

    @Test
    fun `calculates current varighetUker for tilfelle in past`() {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
            antallSykedager = 6 * 7,
        )

        val varighetUker = oppfolgingstilfellePerson.oppfolgingstilfelleList[0].calculateCurrentVarighetUker()

        assertEquals(6, varighetUker)
    }

    @Test
    fun `calculates current varighetUker until today`() {
        val oppfolgingstilfelle = generateOppfolgingstilfelle(
            start = LocalDate.now().minusWeeks(8),
            end = LocalDate.now().plusWeeks(2),
            antallSykedager = (5 + 2) * 7,
        )
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
            oppfolgingstilfelleList = listOf(oppfolgingstilfelle)
        )

        val varighetUker = oppfolgingstilfellePerson.oppfolgingstilfelleList[0].calculateCurrentVarighetUker()

        assertEquals(5, varighetUker)
    }
}
