package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag.*
import no.nav.syfo.util.defaultZoneOffset
import no.nav.syfo.util.toLocalDateOslo
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.generator.generateOppfolgingstilfelleBit
import java.time.*

class OppfolgingstilfelleServiceSpek : Spek({
    describe(OppfolgingstilfelleServiceSpek::class.java.simpleName) {

        val oppfolgingstilfelleService = OppfolgingstilfelleService()

        val mondayLocal = LocalDate.of(2018, 11, 26)
        val monday = mondayLocal.atStartOfDay().toInstant(defaultZoneOffset).atOffset(defaultZoneOffset)
        val defaultBit: OppfolgingstilfelleBit = generateOppfolgingstilfelleBit().copy(
            createdAt = monday,
            inntruffet = monday,
            fom = monday,
        )

        describe("Generate OppfolgingstilfelleList from OppfolgingsBitList") {
            it("should return empty list of OppfolgingstilfelleBitList is empty") {
                val result = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = emptyList(),
                )
                result shouldBeEqualTo emptyList()
            }

            it("should return 1 Oppfolgingstilfelle if person only has days with Sykedag") {
                val oppfolgingstilfelleBitList = listOf(
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now().minusDays(16),
                        tom = OffsetDateTime.now().minusDays(16),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now(),
                        tom = OffsetDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
                oppfolgingstilfelleList.size shouldBeEqualTo 1

                val tilfelleDuration = Period.between(
                    oppfolgingstilfelleList.first().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.first().end.toLocalDateOslo(),
                ).days
                tilfelleDuration shouldBeEqualTo 16
            }

            it("should return 1 Oppfolgingstilfelle, if person only has Ferie/Permisjon between 2 Sykedag") {
                val oppfolgingstilfelleBitList = listOf(
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now().minusDays(20),
                        tom = OffsetDateTime.now().minusDays(20),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                        fom = OffsetDateTime.now().minusDays(19),
                        tom = OffsetDateTime.now().minusDays(10),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, PERMISJON),
                        fom = OffsetDateTime.now().minusDays(9),
                        tom = OffsetDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now(),
                        tom = OffsetDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
                oppfolgingstilfelleList.size shouldBeEqualTo 1

                val tilfelleDuration = Period.between(
                    oppfolgingstilfelleList.first().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.first().end.toLocalDateOslo(),
                ).days
                tilfelleDuration shouldBeEqualTo 20
            }

            it("should return 1 Oppfolgingstilfelle, if person has less than 16 Arbeidsdag between sickness") {
                val oppfolgingstilfelleBitList = listOf(
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now().minusDays(16),
                        tom = OffsetDateTime.now().minusDays(16),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                        fom = OffsetDateTime.now().minusDays(15),
                        tom = OffsetDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now(),
                        tom = OffsetDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
                oppfolgingstilfelleList.size shouldBeEqualTo 1

                val tilfelleDuration = Period.between(
                    oppfolgingstilfelleList.first().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.first().end.toLocalDateOslo(),
                ).days
                tilfelleDuration shouldBeEqualTo 16
            }

            it("should return 2 Oppfolgingstilfelle, if person is not sick for at least 16 days") {
                val oppfolgingstilfelleBitList = listOf(
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now().minusDays(17),
                        tom = OffsetDateTime.now().minusDays(17),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now(),
                        tom = OffsetDateTime.now().plusDays(1),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
                oppfolgingstilfelleList.size shouldBeEqualTo 2

                val firstTilfelleDuration = Period.between(
                    oppfolgingstilfelleList.first().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.first().end.toLocalDateOslo(),
                ).days
                firstTilfelleDuration shouldBeEqualTo 0

                val secondTilfelleDuration = Period.between(
                    oppfolgingstilfelleList.last().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.last().end.toLocalDateOslo(),
                ).days
                secondTilfelleDuration shouldBeEqualTo 1
            }

            it("should return 2 Oppfolgingstilfelle, if person has at least 16 Arbeidsdag between 2 Sykedag") {
                val oppfolgingstilfelleBitList = listOf(
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now().minusDays(17),
                        tom = OffsetDateTime.now().minusDays(17),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                        fom = OffsetDateTime.now().minusDays(16),
                        tom = OffsetDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now(),
                        tom = OffsetDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
                oppfolgingstilfelleList.size shouldBeEqualTo 2

                val firstTilfelleDuration = Period.between(
                    oppfolgingstilfelleList.first().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.first().end.toLocalDateOslo(),
                ).days
                firstTilfelleDuration shouldBeEqualTo 0

                val secondTilfelleDuration = Period.between(
                    oppfolgingstilfelleList.last().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.last().end.toLocalDateOslo(),
                ).days
                secondTilfelleDuration shouldBeEqualTo 0
            }

            it("should return 2 Oppfolgingstilfelle, if person has at least 16 Arbeidsdag+Feriedag and at at least 1 Feriedag between 2 Sykedag") {
                val oppfolgingstilfelleBitList = listOf(
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now().minusDays(20),
                        tom = OffsetDateTime.now().minusDays(20),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                        fom = OffsetDateTime.now().minusDays(18),
                        tom = OffsetDateTime.now().minusDays(10),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, PERMISJON),
                        fom = OffsetDateTime.now().minusDays(9),
                        tom = OffsetDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = OffsetDateTime.now(),
                        inntruffet = OffsetDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = OffsetDateTime.now(),
                        tom = OffsetDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
                oppfolgingstilfelleList.size shouldBeEqualTo 2

                val firstTilfelleDuration = Period.between(
                    oppfolgingstilfelleList.first().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.first().end.toLocalDateOslo(),
                ).days
                firstTilfelleDuration shouldBeEqualTo 0

                val secondTilfelleDuration = Period.between(
                    oppfolgingstilfelleList.last().start.toLocalDateOslo(),
                    oppfolgingstilfelleList.last().end.toLocalDateOslo(),
                ).days
                secondTilfelleDuration shouldBeEqualTo 0
            }
        }
    }
})
