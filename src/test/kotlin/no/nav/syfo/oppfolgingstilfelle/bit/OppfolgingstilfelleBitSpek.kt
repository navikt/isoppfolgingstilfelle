package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag.*
import no.nav.syfo.oppfolgingstilfelle.bit.domain.generateOppfolgingstilfelleList
import no.nav.syfo.util.defaultZoneOffset
import no.nav.syfo.util.nowUTC
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.generator.generateOppfolgingstilfelleBit
import java.time.LocalDate
import java.time.Period

class OppfolgingstilfelleBitSpek : Spek({
    val mondayLocal = LocalDate.of(2018, 11, 26)
    val monday = mondayLocal.atStartOfDay().toInstant(defaultZoneOffset).atOffset(defaultZoneOffset)
    val defaultBit: OppfolgingstilfelleBit = generateOppfolgingstilfelleBit().copy(
        createdAt = monday,
        inntruffet = monday,
        fom = monday.toLocalDate(),
    )

    describe("Generate OppfolgingstilfelleList from OppfolgingsBitList") {
        it("should return empty list if OppfolgingstilfelleBitList is empty") {
            val result = emptyList<OppfolgingstilfelleBit>().generateOppfolgingstilfelleList()
            result shouldBeEqualTo emptyList()
        }

        it("should return 1 Oppfolgingstilfelle if person only has days with Sykedag") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(16),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1

            val tilfelleDuration = Period.between(
                oppfolgingstilfelleList.first().start,
                oppfolgingstilfelleList.first().end,
            ).days
            tilfelleDuration shouldBeEqualTo 16
        }

        it("should return 1 Oppfolgingstilfelle, if person only has Ferie/Permisjon between 2 Sykedag") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().minusDays(20),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                    fom = LocalDate.now().minusDays(19),
                    tom = LocalDate.now().minusDays(10),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, PERMISJON),
                    fom = LocalDate.now().minusDays(9),
                    tom = LocalDate.now().minusDays(1),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1

            val tilfelleDuration = Period.between(
                oppfolgingstilfelleList.first().start,
                oppfolgingstilfelleList.first().end,
            ).days
            tilfelleDuration shouldBeEqualTo 20
        }

        it("should return 1 Oppfolgingstilfelle, if person has less than 16 Arbeidsdag between sickness") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(16),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                    fom = LocalDate.now().minusDays(15),
                    tom = LocalDate.now().minusDays(1),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1

            val tilfelleDuration = Period.between(
                oppfolgingstilfelleList.first().start,
                oppfolgingstilfelleList.first().end,
            ).days
            tilfelleDuration shouldBeEqualTo 16
        }

        it("should return 2 Oppfolgingstilfelle, if person is not sick for at least 16 days") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(17),
                    tom = LocalDate.now().minusDays(17),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(1),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 2

            val firstTilfelleDuration = Period.between(
                oppfolgingstilfelleList.first().start,
                oppfolgingstilfelleList.first().end,
            ).days
            firstTilfelleDuration shouldBeEqualTo 0

            val secondTilfelleDuration = Period.between(
                oppfolgingstilfelleList.last().start,
                oppfolgingstilfelleList.last().end,
            ).days
            secondTilfelleDuration shouldBeEqualTo 1
        }

        it("should return 2 Oppfolgingstilfelle, if person has at least 16 Arbeidsdag between 2 Sykedag") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(17),
                    tom = LocalDate.now().minusDays(17),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(1),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 2

            val firstTilfelleDuration = Period.between(
                oppfolgingstilfelleList.first().start,
                oppfolgingstilfelleList.first().end,
            ).days
            firstTilfelleDuration shouldBeEqualTo 0

            val secondTilfelleDuration = Period.between(
                oppfolgingstilfelleList.last().start,
                oppfolgingstilfelleList.last().end,
            ).days
            secondTilfelleDuration shouldBeEqualTo 0
        }

        it("should return 2 Oppfolgingstilfelle, if person has at least 16 Arbeidsdag+Feriedag and at at least 1 Feriedag between 2 Sykedag") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().minusDays(20),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                    fom = LocalDate.now().minusDays(18),
                    tom = LocalDate.now().minusDays(10),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, PERMISJON),
                    fom = LocalDate.now().minusDays(9),
                    tom = LocalDate.now().minusDays(1),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 2

            val firstTilfelleDuration = Period.between(
                oppfolgingstilfelleList.first().start,
                oppfolgingstilfelleList.first().end,
            ).days
            firstTilfelleDuration shouldBeEqualTo 0

            val secondTilfelleDuration = Period.between(
                oppfolgingstilfelleList.last().start,
                oppfolgingstilfelleList.last().end,
            ).days
            secondTilfelleDuration shouldBeEqualTo 0
        }
    }
})
