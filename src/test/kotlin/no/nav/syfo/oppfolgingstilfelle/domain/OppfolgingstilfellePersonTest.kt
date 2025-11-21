package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.calculateCurrentVarighetUker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import testhelper.generator.generateOppfolgingstilfelle
import testhelper.generator.generateOppfolgingstilfellePerson
import java.time.LocalDate

class OppfolgingstilfellePersonTest {

    val THREE_YEARS_IN_MONTHS: Long = 36
    val FIVE_YEARS_IN_MONTHS: Long = 60

    fun daysFromToday(days: Int): LocalDate = LocalDate.now().plusDays(days.toLong())

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

    @Test
    fun `has NOT gjentakende sykefravar when no oppfolgingstilfeller`() {
        assertFalse(OppfolgingstilfellePerson.hasGjentakendeSykefravar(emptyList()))
    }

    @Test
    fun `has gjentakende sykefravar if sick twice adding up to more than 400 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-601), daysFromToday(-401)),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-200)),
        )

        assertTrue(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has gjentakende sykefravar if sick twice adding up to 300 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-601), daysFromToday(-501)),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-202)),
        )

        assertTrue(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has NOT gjentakende sykefravar if sick twice adding up to 299 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-601), daysFromToday(-501)),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-203)),
        )

        assertFalse(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has NOT gjentakende sykefravar if sick once for more than 400 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-500), daysFromToday(-100)),
        )

        assertFalse(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has NOT gjentakende sykefravar if 5 short, less than 3 days, sykefravar and one long adding up to more than 100 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-500), daysFromToday(-400)),
            generateOppfolgingstilfelle(daysFromToday(-300), daysFromToday(-299)),
            generateOppfolgingstilfelle(daysFromToday(-250), daysFromToday(-249)),
            generateOppfolgingstilfelle(daysFromToday(-200), daysFromToday(-199)),
            generateOppfolgingstilfelle(daysFromToday(-150), daysFromToday(-149)),
            generateOppfolgingstilfelle(daysFromToday(-100), daysFromToday(-99)),
        )

        assertFalse(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has gjentakende sykefravar if 5 almost short, exactly 3 days, sykefravar and one long adding up to more than 100 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-500), daysFromToday(-400)),
            generateOppfolgingstilfelle(daysFromToday(-300), daysFromToday(-298)),
            generateOppfolgingstilfelle(daysFromToday(-250), daysFromToday(-248)),
            generateOppfolgingstilfelle(daysFromToday(-200), daysFromToday(-198)),
            generateOppfolgingstilfelle(daysFromToday(-150), daysFromToday(-148)),
            generateOppfolgingstilfelle(daysFromToday(-100), daysFromToday(-98)),
        )

        assertTrue(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has gjentakende sykefravar if 5 sykefravar adding up to 100 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-500), daysFromToday(-482)),
            generateOppfolgingstilfelle(daysFromToday(-450), daysFromToday(-431)),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-381)),
            generateOppfolgingstilfelle(daysFromToday(-350), daysFromToday(-331)),
            generateOppfolgingstilfelle(daysFromToday(-300), daysFromToday(-280)),
        )

        assertTrue(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has NOT gjentakende sykefravar if 5 sykefravar adding up to 99 days`() {
        val tilfeller = listOf(
            generateOppfolgingstilfelle(daysFromToday(-500), daysFromToday(-483)),
            generateOppfolgingstilfelle(daysFromToday(-450), daysFromToday(-431)),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-381)),
            generateOppfolgingstilfelle(daysFromToday(-350), daysFromToday(-331)),
            generateOppfolgingstilfelle(daysFromToday(-300), daysFromToday(-280)),
        )

        assertFalse(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has NOT gjentakende sykefravar if sick twice adding up to more than 400 days and one is old`() {
        val threeYearsAgo = LocalDate.now().minusMonths(THREE_YEARS_IN_MONTHS).minusDays(1)
        val threeYearsMinus200Days = threeYearsAgo.minusDays(200)
        val tilfeller = listOf(
            generateOppfolgingstilfelle(threeYearsMinus200Days, threeYearsAgo),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-200)),
        )

        assertFalse(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }

    @Test
    fun `has gjentakende sykefravar if tilfelle ends less than three years ago`() {
        val fiveYearsAgo = LocalDate.now().minusMonths(FIVE_YEARS_IN_MONTHS)
        val lessThanThreeYearsAgo = LocalDate.now().minusMonths(THREE_YEARS_IN_MONTHS).plusDays(1)
        val tilfeller = listOf(
            generateOppfolgingstilfelle(fiveYearsAgo, lessThanThreeYearsAgo),
            generateOppfolgingstilfelle(daysFromToday(-400), daysFromToday(-200)),
        )

        assertTrue(OppfolgingstilfellePerson.hasGjentakendeSykefravar(tilfeller))
    }
}
