package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.KandidatStatus
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import no.nav.syfo.util.toLocalDateOslo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants.ARBEIDSTAKER_AKTOR_ID
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.dropData
import testhelper.generator.generateOppfolgingstilfelle
import testhelper.generator.generateOppfolgingstilfellePerson
import testhelper.getKandidaterForPersonident
import java.time.LocalDate
import java.util.*

class ModiaAOOversendingCronjobTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val kandidatRepository = externalMockEnvironment.kandidatRepository

    private val cronjob = ModiaAOOversendingCronjob(
        oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfellePersonRepository),
        kandidatRepository = kandidatRepository,
        sendEnabled = true,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
    }

    private fun createKandidatForProcessing(
        tilfelleStart: LocalDate = LocalDate.now().minusDays(29),
    ) {
        val kandidat = SykmeldtUtenArbeidsgiverKandidat.opprett(
            personident = PERSONIDENTNUMBER_DEFAULT,
            aktorId = ARBEIDSTAKER_AKTOR_ID,
            referanseId = UUID.randomUUID().toString(),
            tilfelleStart = tilfelleStart,
        )
        kandidatRepository.createIfMissing(kandidat)
    }

    private fun createTilfelle(
        start: LocalDate,
        end: LocalDate,
        arbeidstakerAtTilfelleEnd: Boolean = false,
    ) {
        val person = generateOppfolgingstilfellePerson(
            personIdent = PERSONIDENTNUMBER_DEFAULT,
            oppfolgingstilfelleList = listOf(
                generateOppfolgingstilfelle(
                    start = start,
                    end = end,
                    arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
                )
            ),
        )
        oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(person)
    }

    private fun createTilfelleWithOldTilfelle(
        start: LocalDate,
        end: LocalDate,
        arbeidstakerAtTilfelleEnd: Boolean = false,
    ) {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
            personIdent = PERSONIDENTNUMBER_DEFAULT,
            oppfolgingstilfelleList = listOf(
                generateOppfolgingstilfelle(
                    start = start.minusYears(1),
                    end = end.minusYears(1),
                    arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
                ),
                generateOppfolgingstilfelle(
                    start = start,
                    end = end,
                    arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
                )
            ),
        )
        oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(oppfolgingstilfellePerson)
    }

    @Test
    fun `sets FERDIG when no tilfelle exists for kandidat`() {
        createKandidatForProcessing()

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets FERDIG when tilfelle ended more than DAYS_AFTER_TILFELLE_END days ago`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(40),
            end = LocalDate.now().minusDays(20),
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets UTSATT to tomorrow when tilfelle has ended recently`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(40),
            end = LocalDate.now().minusDays(5),
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.UTSATT, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
        assertEquals(LocalDate.now().plusDays(1), kandidat.nextProcessingAt.toLocalDateOslo())
    }

    @Test
    fun `sets FERDIG when person has dodsdato`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
        )
        oppfolgingstilfellePersonRepository.createPerson(
            uuid = UUID.randomUUID(),
            personIdent = PERSONIDENTNUMBER_DEFAULT,
            dodsdato = LocalDate.now().minusDays(1),
            hendelseId = UUID.randomUUID(),
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets UTSATT when latest tilfelle is less than 28 days old`() {
        val tilfelleStart = LocalDate.now().minusDays(10)
        createKandidatForProcessing()
        createTilfelle(
            start = tilfelleStart,
            end = LocalDate.now(),
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.UTSATT, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
        assertEquals(tilfelleStart.plusDays(28), kandidat.nextProcessingAt.toLocalDateOslo())
    }

    @Test
    fun `sets FERDIG and oversendt_at when latest tilfelle is more than 28 days old and kandidat does not have arbeidsgiver`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = false,
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNotNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets FERDIG and oversendt_at when latest tilfelle is more than 28 days old and kandidat does not have arbeidsgiver and old tilfelle exists`() {
        createKandidatForProcessing()
        createTilfelleWithOldTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = false,
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNotNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets FERDIG when latest tilfelle is more than 28 days old and kandidat has arbeidsgiver`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = true,
        )

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `does not process kandidat when nextProcessingAt is in the future`() {
        createKandidatForProcessing(tilfelleStart = LocalDate.now())

        cronjob.runJob()

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.NY, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }
}
