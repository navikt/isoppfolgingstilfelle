package no.nav.syfo.infrastructure.cronjob

import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.KandidatStatus
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pensjonpen.PensjonPenClient
import no.nav.syfo.infrastructure.kafka.StartOppfolgingProducer
import no.nav.syfo.util.toLocalDateOslo
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants.ARBEIDSTAKER_AKTOR_ID
import testhelper.UserConstants.ARBEIDSTAKER_UFOR
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
    private val startOppfolgingProducer = mockk<StartOppfolgingProducer>()

    private val cronjob = ModiaAOOversendingCronjob(
        oppfolgingstilfelleService = OppfolgingstilfelleService(oppfolgingstilfellePersonRepository),
        kandidatRepository = kandidatRepository,
        pensjonPenClient = PensjonPenClient(
            azureAdClient = AzureAdClient(
                azureEnviroment = externalMockEnvironment.environment.azure,
                valkeyStore = externalMockEnvironment.valkeyStore,
                httpClient = externalMockEnvironment.mockHttpClient,
            ),
            clientEnvironment = externalMockEnvironment.environment.clients.pensjonPen,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
        startOppfolgingProducer = startOppfolgingProducer,
        sendEnabled = true,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        justRun { startOppfolgingProducer.sendSykmeldtUtenArbeidsgiverKandidat(any()) }
    }

    private fun createKandidatForProcessing(
        tilfelleStart: LocalDate = LocalDate.now().minusDays(29),
        personident: PersonIdentNumber = PERSONIDENTNUMBER_DEFAULT,
    ) {
        val kandidat = SykmeldtUtenArbeidsgiverKandidat.opprett(
            personident = personident,
            aktorId = ARBEIDSTAKER_AKTOR_ID,
            referanseId = UUID.randomUUID().toString(),
            tilfelleStart = tilfelleStart,
        )
        kandidatRepository.createIfMissing(kandidat, tilfelleEnd = tilfelleStart)
    }

    private fun createTilfelle(
        start: LocalDate,
        end: LocalDate,
        arbeidstakerAtTilfelleEnd: Boolean = false,
        personident: PersonIdentNumber = PERSONIDENTNUMBER_DEFAULT,
    ) {
        val person = generateOppfolgingstilfellePerson(
            personIdent = personident,
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

        runBlocking { cronjob.runJob() }

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

        runBlocking { cronjob.runJob() }

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

        runBlocking { cronjob.runJob() }

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

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets UTSATT when latest tilfelle is less than 29 days old`() {
        val tilfelleStart = LocalDate.now().minusDays(10)
        createKandidatForProcessing()
        createTilfelle(
            start = tilfelleStart,
            end = LocalDate.now(),
        )

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.UTSATT, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
        assertEquals(tilfelleStart.plusDays(29), kandidat.nextProcessingAt.toLocalDateOslo())
    }

    @Test
    fun `sets FERDIG and oversendt_at when latest tilfelle is more than 29 days old and kandidat does not have arbeidsgiver`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = false,
        )

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNotNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets FERDIG without sending when kandidat has 100 percent uforegrad`() {
        createKandidatForProcessing(personident = ARBEIDSTAKER_UFOR)
        createTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = false,
            personident = ARBEIDSTAKER_UFOR,
        )

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(ARBEIDSTAKER_UFOR).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets FERDIG and oversendt_at when latest tilfelle is more than 29 days old and kandidat does not have arbeidsgiver and old tilfelle exists`() {
        createKandidatForProcessing()
        createTilfelleWithOldTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = false,
        )

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNotNull(kandidat.oversendtAt)
    }

    @Test
    fun `sets FERDIG when latest tilfelle is more than 29 days old and kandidat has arbeidsgiver`() {
        createKandidatForProcessing()
        createTilfelle(
            start = LocalDate.now().minusDays(30),
            end = LocalDate.now(),
            arbeidstakerAtTilfelleEnd = true,
        )

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.FERDIG, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }

    @Test
    fun `does not process kandidat when nextProcessingAt is in the future`() {
        createKandidatForProcessing(tilfelleStart = LocalDate.now())

        runBlocking { cronjob.runJob() }

        val kandidat = database.getKandidaterForPersonident(PERSONIDENTNUMBER_DEFAULT).single()
        assertEquals(KandidatStatus.NY, KandidatStatus.valueOf(kandidat.status))
        assertNull(kandidat.oversendtAt)
    }
}
