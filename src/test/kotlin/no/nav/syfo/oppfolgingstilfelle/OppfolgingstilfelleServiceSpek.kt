package no.nav.syfo.oppfolgingstilfelle

import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.generator.generateOppfolgingstilfelleBit
import java.time.*

class OppfolgingstilfelleServiceSpek : Spek({
    describe(OppfolgingstilfelleServiceSpek::class.java.simpleName) {

        val oppfolgingstilfelleService = OppfolgingstilfelleService()

        val monday = LocalDate.of(2018, 11, 26)
        val defaultBit: OppfolgingstilfelleBit = generateOppfolgingstilfelleBit().copy(
            createdAt = monday.atStartOfDay(),
            inntruffet = monday.atStartOfDay(),
            fom = monday.atStartOfDay(),
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
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now().minusDays(16),
                        tom = LocalDateTime.now().minusDays(16),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now(),
                        tom = LocalDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
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
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now().minusDays(20),
                        tom = LocalDateTime.now().minusDays(20),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                        fom = LocalDateTime.now().minusDays(19),
                        tom = LocalDateTime.now().minusDays(10),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, PERMISJON),
                        fom = LocalDateTime.now().minusDays(9),
                        tom = LocalDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now(),
                        tom = LocalDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
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
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now().minusDays(16),
                        tom = LocalDateTime.now().minusDays(16),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                        fom = LocalDateTime.now().minusDays(15),
                        tom = LocalDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now(),
                        tom = LocalDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
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
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now().minusDays(17),
                        tom = LocalDateTime.now().minusDays(17),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now(),
                        tom = LocalDateTime.now().plusDays(1),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
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
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now().minusDays(17),
                        tom = LocalDateTime.now().minusDays(17),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                        fom = LocalDateTime.now().minusDays(16),
                        tom = LocalDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now(),
                        tom = LocalDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
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
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now().minusDays(20),
                        tom = LocalDateTime.now().minusDays(20),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                        fom = LocalDateTime.now().minusDays(18),
                        tom = LocalDateTime.now().minusDays(10),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT, PERMISJON),
                        fom = LocalDateTime.now().minusDays(9),
                        tom = LocalDateTime.now().minusDays(1),
                    ),
                    defaultBit.copy(
                        createdAt = LocalDateTime.now(),
                        inntruffet = LocalDateTime.now(),
                        tagList = listOf(SYKEPENGESOKNAD, SENDT),
                        fom = LocalDateTime.now(),
                        tom = LocalDateTime.now(),
                    ),
                )

                val oppfolgingstilfelleList = oppfolgingstilfelleService.createOppfolgingstilfelleList(
                    oppfolgingstilfelleBitList = oppfolgingstilfelleBitList,
                )
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
    }
})
