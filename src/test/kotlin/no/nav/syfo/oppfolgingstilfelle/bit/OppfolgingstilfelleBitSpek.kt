package no.nav.syfo.oppfolgingstilfelle.bit

import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag.*
import no.nav.syfo.oppfolgingstilfelle.person.domain.calculateCurrentVarighetUker
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.generator.generateOppfolgingstilfelleBit
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 2
        }

        it("Egenmelding should count as sykedager") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, EGENMELDING),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(13),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, PERIODE, SENDT, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(12),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo tilfelleDuration.toInt() + 1
        }

        it("should return 1 Oppfolgingstilfelle of 0 day duration if fom og tom is the same") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, PERIODE, NY, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(1),
                    tom = LocalDate.now().minusDays(1),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 1
        }

        it("should return 2 Oppfolgingstilfelle with latest gradertAtTilfelleEnd=true, if sykmelding gradert and older tilfelle-bit from different virksomhet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.of(2018, 1, 9),
                    tom = LocalDate.of(2018, 1, 22),
                    virksomhetsnummer = "987654322",
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.of(2023, 1, 9),
                    tom = LocalDate.of(2023, 1, 22),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.of(2023, 1, 9),
                    tom = LocalDate.of(2023, 1, 22),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.of(2023, 1, 9),
                    tom = LocalDate.of(2023, 1, 22),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FRAVAR_FOR_SYKMELDING),
                    fom = LocalDate.of(2023, 1, 7),
                    tom = LocalDate.of(2023, 1, 8),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.of(2023, 1, 23),
                    tom = LocalDate.of(2023, 2, 19),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.of(2023, 1, 23),
                    tom = LocalDate.of(2023, 2, 19),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.of(2023, 2, 20),
                    tom = LocalDate.of(2023, 3, 5),
                ),
                defaultBit.copy(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.of(2023, 2, 20),
                    tom = LocalDate.of(2023, 3, 5),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 2
            val oppfolgingstilfelle = oppfolgingstilfelleList.last()
            oppfolgingstilfelle.gradertAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle if person only has days with Sykedag from korrigert bit") {
            val feilsendtBit = defaultBit.copy(
                createdAt = nowUTC(),
                inntruffet = nowUTC().minusSeconds(60),
                tagList = listOf(SYKEPENGESOKNAD, SENDT, ARBEID_GJENNOPPTATT),
                fom = LocalDate.now().minusDays(26),
                tom = LocalDate.now(),
                ressursId = UUID.randomUUID().toString(),
            )
            val korrigerendeBit = defaultBit.copy(
                createdAt = nowUTC(),
                inntruffet = nowUTC(),
                tagList = listOf(SYKEPENGESOKNAD, SENDT),
                fom = LocalDate.now().minusDays(16),
                tom = LocalDate.now(),
                ressursId = UUID.randomUUID().toString(),
                korrigerer = UUID.fromString(feilsendtBit.ressursId),
            )
            val oppfolgingstilfelleBitList = listOf(
                feilsendtBit,
                korrigerendeBit,
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1

            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
        }

        it("should return 1 Oppfolgingstilfelle if person only has days with Sykedag - utdanning-bit should be ignored") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(7),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, UTDANNING, DELTID),
                    fom = LocalDate.now().minusDays(160),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if no biter gradert") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, BEHANDLINGSDAGER),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(16),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(6),
                ),
            )
            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if latest bit gradert aktivitet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, BEHANDLINGSDAGER),
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(6),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().plusDays(6),
                    tom = LocalDate.now().plusDays(15),
                ),
            )
            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if latest bit ingen aktivitet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, BEHANDLINGSDAGER),
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(6),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().plusDays(6),
                    tom = LocalDate.now().plusDays(15),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().plusDays(16),
                    tom = LocalDate.now().plusDays(25),
                )
            )
            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if sykmelding-ny bit not gradert and sendt bit gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if sykmelding-ny bit not gradert and sendt bit and sykepengesoknad gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, KORRIGERT_ARBEIDSTID, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                )
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if sykmelding-ny bit not gradert, sendt bit gradert and sykepengesoknad at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                )
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if sykmelding sendt bit gradert and sykepengesoknad ingen aktivitet at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, KORRIGERT_ARBEIDSTID, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                )
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if biter from different virksomheter and no bit gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if biter from different virksomheter and one bit gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, KORRIGERT_ARBEIDSTID, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if bit with ingen_aktivitet ends before last day of tilfelle") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(15),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if biter from different virksomheter with sykmelding gradert and sykepengesoknad without graderingsinformasjon") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if priorityGraderingBit is behandlingsdager") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, BEHANDLINGSDAGER),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(INNTEKTSMELDING, ARBEIDSGIVERPERIODE),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()

            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if no biter with graderingsinformasjon") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }

        it("returns Dag with priorityOppfolgingstilfelleBit with INGEN_AKTIVITET even if it has inntruffet before GRADERT bit") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC().minusDays(1),
                    tagList = listOf(SYKMELDING, SENDT, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val lastDayInTilfelle = oppfolgingstilfelleBitList.pickOppfolgingstilfelleDag(LocalDate.now())

            lastDayInTilfelle.priorityOppfolgingstilfelleBit?.tagList?.contains(INGEN_AKTIVITET)
        }

        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if biter from different virksomheter and all biter gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if biter from different virksomheter and sykmelding-ny bit not gradert and sendt bit gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if biter from different virksomheter and sykmelding-ny bit not gradert and sendt bit and sykepengesoknad gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, KORRIGERT_ARBEIDSTID, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                )
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if biter from different virksomheter and sykmelding-ny bit not gradert, sendt bit gradert and sykepengesoknad at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                )
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if biter from different virksomheter and sykmelding-ny bit not gradert and sykepengesoknad at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                )
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if biter from different virksomheter and sykmelding-ny bit gradert and sendt bit not gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=true if only sykmelding-ny biter from different virksomheter and all gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe true
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if only sykmelding-ny biter from different virksomheter and one gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }
        it("returns Oppfolgingstilfelle with gradertAtTilfelleEnd=false if only sykmelding-ny biter from different virksomheter and none gradert at tilfelle end") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBe false
        }

        it("should return 1 Oppfolgingstilfelle with two virksomheter if biter from different virksomheter") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 2
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle with two virksomheter if biter from different virksomheter and both sykmelding and sykepengesoknad") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, BEKREFTET, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = null,
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC().minusDays(8),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC().minusDays(1),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(7),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 2
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle with virksomhet from sendt bit when both sykmelding and sykmelding-ny") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654320",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654330",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
            oppfolgingstilfelle.virksomhetsnummerList[0].value shouldBeEqualTo "987654330"
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle with virksomhet from both biter when both only sykmelding-ny") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(7),
                    virksomhetsnummer = "987654320",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(6),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654330",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 2
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle and not arbeidstaker when only sykmelding-ny and sykmelding-bekreftet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, BEKREFTET, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8),
                    virksomhetsnummer = null,
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(6),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654330",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 0
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo false
        }

        it("should return 1 Oppfolgingstilfelle and arbeidstaker when sykmelding-ny, sykmelding-sendt and sykmelding-bekreftet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, BEKREFTET, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8),
                    virksomhetsnummer = null,
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(6),
                    tom = LocalDate.now().minusDays(4),
                    virksomhetsnummer = "987654330",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(3),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654330",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle and arbeidstaker when sykmelding-ny, sykpengesoknad-sendt and sykmelding-bekreftet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, BEKREFTET, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8),
                    virksomhetsnummer = null,
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(6),
                    tom = LocalDate.now().minusDays(4),
                    virksomhetsnummer = "987654331",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(3),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654330",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle and arbeidstaker when sykmelding-ny, inntektsmelding and sykmelding-bekreftet") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, BEKREFTET, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(16),
                    tom = LocalDate.now().minusDays(8),
                    virksomhetsnummer = null,
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(INNTEKTSMELDING, ARBEIDSGIVERPERIODE),
                    fom = LocalDate.now().minusDays(6),
                    tom = LocalDate.now().minusDays(4),
                    virksomhetsnummer = "987654331",
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(3),
                    tom = LocalDate.now(),
                    virksomhetsnummer = "987654330",
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return 1 Oppfolgingstilfelle, if person only has Ferie/Permisjon between 2 Sykedag") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, BEKREFTET, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().minusDays(1),
                    virksomhetsnummer = null,
                ),
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 20
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 21
            oppfolgingstilfelle.calculateCurrentVarighetUker() shouldBeEqualTo 3
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 16
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 2
            oppfolgingstilfelle.calculateCurrentVarighetUker() shouldBeEqualTo 0
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val firstTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            firstTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 1

            val oppfolgingstilfelleLast = oppfolgingstilfelleList.last()

            val secondTilfelleDuration = oppfolgingstilfelleLast.start.until(oppfolgingstilfelleLast.end, ChronoUnit.DAYS)
            secondTilfelleDuration shouldBeEqualTo 1
            oppfolgingstilfelleLast.antallSykedager shouldBeEqualTo 2
        }

        it("should return 2 Oppfolgingstilfelle with gradertAtTilfelleEnd=true and gradertAtTilfelleEnd=false, if sykmelding gradert then not sick for at least 16 days then sykmelding not gradert") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(100),
                    tom = LocalDate.now().minusDays(50),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().plusDays(1),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 2

            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBeEqualTo true
            oppfolgingstilfelleList.last().gradertAtTilfelleEnd shouldBeEqualTo false
        }

        it("should return 2 Oppfolgingstilfelle with gradertAtTilfelleEnd=false and gradertAtTilfelleEnd=true, if sykmelding not gradert then not sick for at least 16 days then sykmelding gradert") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, NY, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(100),
                    tom = LocalDate.now().minusDays(50),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, GRADERT_AKTIVITET),
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().plusDays(1),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 2

            oppfolgingstilfelleList.first().gradertAtTilfelleEnd shouldBeEqualTo false
            oppfolgingstilfelleList.last().gradertAtTilfelleEnd shouldBeEqualTo true
        }

        it("should return no Oppfolgingstilfelle, if person has only behandlingsdager") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, BEHANDLINGSDAGER),
                    fom = LocalDate.now().minusDays(18),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 0
        }

        it("should return Oppfolgingstilfelle and exclude behandlingsdager from the interval") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, BEHANDLINGSDAGER),
                    fom = LocalDate.now().minusDays(18),
                    tom = LocalDate.now(),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(10),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()
            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            tilfelleDuration shouldBeEqualTo 10
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 11
            oppfolgingstilfelle.calculateCurrentVarighetUker() shouldBeEqualTo 1
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val firstTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            firstTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 1

            val oppfolgingstilfelleLast = oppfolgingstilfelleList.last()
            val secondTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            secondTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelleLast.antallSykedager shouldBeEqualTo 1
            oppfolgingstilfelleLast.calculateCurrentVarighetUker() shouldBeEqualTo 0
        }

        it("should return 2 Oppfolgingstilfelle, if person has at least 16 behandlingsdager between 2 Sykedag") {
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
                    tagList = listOf(SYKMELDING, NY, PERIODE, BEHANDLINGSDAGER),
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val firstTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            firstTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 1

            val oppfolgingstilfelleLast = oppfolgingstilfelleList.last()
            val secondTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            secondTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelleLast.antallSykedager shouldBeEqualTo 1
            oppfolgingstilfelleLast.calculateCurrentVarighetUker() shouldBeEqualTo 0
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val firstTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            firstTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 1

            val oppfolgingstilfelleLast = oppfolgingstilfelleList.last()
            val secondTilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)
            secondTilfelleDuration shouldBeEqualTo 0
            oppfolgingstilfelleLast.antallSykedager shouldBeEqualTo 1
            oppfolgingstilfelleLast.calculateCurrentVarighetUker() shouldBeEqualTo 0
        }
        it("should return 1 Oppfolgingstilfelle with correct Sykedager even if multiple ferieperioder") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(30),
                    tom = LocalDate.now().minusDays(30),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                    fom = LocalDate.now().minusDays(28),
                    tom = LocalDate.now().minusDays(20),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(19),
                    tom = LocalDate.now().minusDays(10),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                    fom = LocalDate.now().minusDays(8),
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
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)

            tilfelleDuration shouldBeEqualTo 30
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 12
            oppfolgingstilfelle.calculateCurrentVarighetUker() shouldBeEqualTo 1
        }
        it("should return 1 Oppfolgingstilfelle with correct Sykedager even if multiple ferieperioder with arbeidsdag in between") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT),
                    fom = LocalDate.now().minusDays(30),
                    tom = LocalDate.now().minusDays(28),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                    fom = LocalDate.now().minusDays(27),
                    tom = LocalDate.now().minusDays(20),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKEPENGESOKNAD, SENDT, FERIE),
                    fom = LocalDate.now().minusDays(18),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)

            tilfelleDuration shouldBeEqualTo 10
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 11
            oppfolgingstilfelle.calculateCurrentVarighetUker() shouldBeEqualTo 1
        }
        it("should return 1 Oppfolgingstilfelle with correct Sykedager even if egenmeldingsdager to begin with") {
            val oppfolgingstilfelleBitList = listOf(
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, EGENMELDING),
                    fom = LocalDate.now().minusDays(30),
                    tom = LocalDate.now().minusDays(28),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, EGENMELDING),
                    fom = LocalDate.now().minusDays(20),
                    tom = LocalDate.now().minusDays(20),
                ),
                defaultBit.copy(
                    createdAt = nowUTC(),
                    inntruffet = nowUTC(),
                    tagList = listOf(SYKMELDING, SENDT, PERIODE, INGEN_AKTIVITET),
                    fom = LocalDate.now().minusDays(10),
                    tom = LocalDate.now(),
                ),
            )

            val oppfolgingstilfelleList = oppfolgingstilfelleBitList.generateOppfolgingstilfelleList()
            oppfolgingstilfelleList.size shouldBeEqualTo 1
            val oppfolgingstilfelle = oppfolgingstilfelleList.first()

            val tilfelleDuration = oppfolgingstilfelle.start.until(oppfolgingstilfelle.end, ChronoUnit.DAYS)

            tilfelleDuration shouldBeEqualTo 30
            oppfolgingstilfelle.antallSykedager shouldBeEqualTo 15
            oppfolgingstilfelle.calculateCurrentVarighetUker() shouldBeEqualTo 2
        }
    }
})
