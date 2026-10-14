package no.nav.syfo.infrastructure.cronjob

import io.mockk.clearMocks
import io.mockk.justRun
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.toOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.database.getIdentCount
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.generator.generateKafkaSyketilfellebitRelevantVirksomhet
import testhelper.generator.generateOppfolgingstilfelle
import testhelper.generator.generateOppfolgingstilfellePerson
import java.time.LocalDate
import java.util.*

class OutdatedIdentCronjobTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val tilfellebitRepository = externalMockEnvironment.tilfellebitRepository
    private val oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()

    private val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = externalMockEnvironment.pdlClient,
    )
    private val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        ),
        tilfellebitRepository = tilfellebitRepository,
        pdlClient = externalMockEnvironment.pdlClient,
        kandidatRepository = externalMockEnvironment.kandidatRepository,
    )

    // PDL-mocken returnerer 11111111111 som aktiv ident for ARBEIDSTAKER_3_FNR
    private val oldIdent = UserConstants.ARBEIDSTAKER_3_FNR
    private val newIdent = PersonIdentNumber("11111111111")

    private val oldTilfellebit = generateTilfellebit(
        personIdent = oldIdent,
        fom = LocalDate.now().minusDays(30),
        tom = LocalDate.now().minusDays(20),
        inntruffetDaysAgo = 20,
    )
    private val newTilfellebit = generateTilfellebit(
        personIdent = newIdent,
        fom = LocalDate.now().minusDays(19),
        tom = LocalDate.now().minusDays(5),
        inntruffetDaysAgo = 5,
    )

    @BeforeEach
    fun beforeEach() {
        clearMocks(oppfolgingstilfellePersonProducer)
        justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Test
    fun `merges tilfellebiter and oppfolgingstilfellePerson on outdated ident into existing data on aktiv ident`() {
        tilfellebitRepository.createOppfolgingstilfelleBit(oldTilfellebit)
        tilfellebitRepository.createOppfolgingstilfelleBit(newTilfellebit)
        createOppfolgingstilfellePerson(oldTilfellebit)
        createOppfolgingstilfellePerson(newTilfellebit)

        val result = createCronjob(listOf(oldTilfellebit.uuid)).runJob()

        assertEquals(1, result.updated)
        assertEquals(0, result.failed)
        assertEquals(0, database.getIdentCount(listOf(oldIdent)))
        assertEquals(4, database.getIdentCount(listOf(newIdent)))
        assertTrue(tilfellebitRepository.getOppfolgingstilfelleBit(oldTilfellebit.uuid)!!.processed)
        assertFalse(tilfellebitRepository.getOppfolgingstilfelleBit(newTilfellebit.uuid)!!.processed)

        runBlocking {
            val cronjobResult = oppfolgingstilfelleCronjob.runJob()
            assertEquals(1, cronjobResult.updated)
        }

        val oppfolgingstilfellePerson = oppfolgingstilfellePersonRepository.getOppfolgingstilfellePerson(newIdent)!!
        assertEquals(newTilfellebit.uuid, oppfolgingstilfellePerson.referanseTilfelleBitUUID)
        assertEquals(1, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
        assertEquals(oldTilfellebit.fom, oppfolgingstilfellePerson.oppfolgingstilfeller.first().start)
        assertEquals(newTilfellebit.tom, oppfolgingstilfellePerson.oppfolgingstilfeller.first().end)
    }

    @Test
    fun `does nothing when ident is already aktiv ident`() {
        tilfellebitRepository.createOppfolgingstilfelleBit(newTilfellebit)

        val result = createCronjob(listOf(newTilfellebit.uuid)).runJob()

        assertEquals(0, result.updated)
        assertEquals(0, result.failed)
        assertTrue(tilfellebitRepository.getOppfolgingstilfelleBit(newTilfellebit.uuid)!!.processed)
    }

    @Test
    fun `ignores unknown tilfellebit uuid`() {
        val result = createCronjob(listOf(UUID.randomUUID())).runJob()

        assertEquals(0, result.updated)
        assertEquals(0, result.failed)
    }

    private fun createCronjob(uuids: List<UUID>) = OutdatedIdentCronjob(
        tilfellebitRepository = tilfellebitRepository,
        identhendelseService = identhendelseService,
        tilfellebitUuids = uuids,
    )

    private fun createOppfolgingstilfellePerson(tilfellebit: OppfolgingstilfelleBit) =
        oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(
            generateOppfolgingstilfellePerson(
                personIdent = tilfellebit.personIdentNumber,
                oppfolgingstilfelleList = listOf(
                    generateOppfolgingstilfelle(start = tilfellebit.fom, end = tilfellebit.tom, antallSykedager = 10)
                ),
            ).copy(
                referanseTilfelleBitUuid = tilfellebit.uuid,
                referanseTilfelleBitInntruffet = tilfellebit.inntruffet,
            )
        )

    private fun generateTilfellebit(
        personIdent: PersonIdentNumber,
        fom: LocalDate,
        tom: LocalDate,
        inntruffetDaysAgo: Long,
    ): OppfolgingstilfelleBit {
        val kafkaTilfellebit = generateKafkaSyketilfellebitRelevantVirksomhet(personIdent = personIdent)
        return kafkaTilfellebit.copy(
            fom = fom,
            tom = tom,
            inntruffet = kafkaTilfellebit.inntruffet.minusDays(inntruffetDaysAgo),
        ).toOppfolgingstilfelleBit().copy(processed = true)
    }
}
