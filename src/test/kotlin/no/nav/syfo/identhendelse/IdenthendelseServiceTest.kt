package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.toOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.getIdentCount
import org.junit.jupiter.api.*
import no.nav.syfo.domain.KandidatStatus
import no.nav.syfo.domain.SykmeldtUtenArbeidsgiverKandidat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.getKandidaterForPersonident
import java.time.LocalDate
import testhelper.generator.generateKafkaIdenthendelseDTO
import testhelper.generator.generateKafkaSyketilfellebitSykmeldingNy
import testhelper.generator.generateOppfolgingstilfellePerson

class IdenthendelseServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val tilfelleBitRepository = externalMockEnvironment.tilfellebitRepository
    private val pdlClient = PdlClient(
        azureAdClient = AzureAdClient(
            azureEnviroment = externalMockEnvironment.environment.azure,
            valkeyStore = externalMockEnvironment.valkeyStore,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
        clientEnvironment = externalMockEnvironment.environment.clients.pdl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    private val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = pdlClient,
    )

    private fun populateDatabase(oldIdent: PersonIdentNumber, database: DatabaseInterface) {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(oldIdent)
        val newTilfelleBit = generateKafkaSyketilfellebitSykmeldingNy(
            personIdentNumber = UserConstants.ARBEIDSTAKER_4_FNR,
        ).toOppfolgingstilfelleBit().copy(
            processed = true
        )

        tilfelleBitRepository.createOppfolgingstilfelleBit(oppfolgingstilfelleBit = newTilfelleBit)
        oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(oppfolgingstilfellePerson = oppfolgingstilfellePerson)
    }

    @AfterEach
    fun afterEach() {
        database.dropData()
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        @Test
        fun `Skal oppdatere tilfelle når person har fått ny ident`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            populateDatabase(oldIdent, database)

            val oldIdentOccurrences =
                database.getIdentCount(listOf(oldIdent)) + database.getIdentCount(listOf(UserConstants.ARBEIDSTAKER_4_FNR))
            assertEquals(2, oldIdentOccurrences)

            runBlocking {
                identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
            }

            val newIdentOccurrences = database.getIdentCount(listOf(newIdent))
            assertEquals(2, newIdentOccurrences)
        }

        @Test
        fun `Skal flytte kandidat for oversending til Modia AO når person har fått ny ident`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            externalMockEnvironment.kandidatRepository.createIfMissing(
                kandidat = SykmeldtUtenArbeidsgiverKandidat.opprett(
                    personident = oldIdent,
                    aktorId = "1234567890123",
                    referanseId = null,
                    tilfelleStart = LocalDate.now().minusDays(10),
                ),
                tilfelleEnd = LocalDate.now(),
            )

            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

            assertTrue(database.getKandidaterForPersonident(oldIdent).isEmpty())
            assertEquals(1, database.getKandidaterForPersonident(newIdent).size)
        }

        private fun opprettKandidat(personident: PersonIdentNumber, tilfelleStart: LocalDate): SykmeldtUtenArbeidsgiverKandidat {
            val kandidat = SykmeldtUtenArbeidsgiverKandidat.opprett(
                personident = personident,
                aktorId = "1234567890123",
                referanseId = null,
                tilfelleStart = tilfelleStart,
            )
            externalMockEnvironment.kandidatRepository.createIfMissing(kandidat = kandidat, tilfelleEnd = tilfelleStart)
            return kandidat
        }

        @Test
        fun `Skal ferdigstille flyttet kandidat når ny ident allerede har aktiv kandidat`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()
            val gammelKandidat = opprettKandidat(oldIdent, LocalDate.now().minusDays(40))
            val nyKandidat = opprettKandidat(newIdent, LocalDate.now().minusDays(10))

            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

            val kandidater = database.getKandidaterForPersonident(newIdent).associateBy { it.uuid }
            assertEquals(2, kandidater.size)
            assertEquals(KandidatStatus.FERDIG.name, kandidater[gammelKandidat.uuid]!!.status)
            assertEquals(KandidatStatus.NY.name, kandidater[nyKandidat.uuid]!!.status)
        }

        @Test
        fun `Skal ikke ferdigstille flyttet kandidat når ny ident kun har oversendt kandidat`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
            val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()
            val gammelKandidat = opprettKandidat(oldIdent, LocalDate.now().minusDays(10))
            val nyKandidat = opprettKandidat(newIdent, LocalDate.now().minusDays(200))
            externalMockEnvironment.kandidatRepository.markerOversendt(nyKandidat.uuid)

            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)

            val kandidat = database.getKandidaterForPersonident(newIdent).first { it.uuid == gammelKandidat.uuid }
            assertEquals(KandidatStatus.NY.name, kandidat.status)
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {
        @Test
        fun `Skal kaste feil hvis PDL ikke har oppdatert identen`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                personident = UserConstants.ARBEIDSTAKER_3_FNR,
                hasOldPersonident = true,
            )
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            populateDatabase(oldIdent, database)

            runBlocking {
                assertThrows<IllegalStateException> {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }
            }
        }

        @Test
        fun `Skal kaste RuntimeException hvis PDL gir en not_found ved henting av identer`() {
            val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                personident = UserConstants.ARBEIDSTAKER_WITH_ERROR,
                hasOldPersonident = true,
            )
            val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

            populateDatabase(oldIdent, database)

            runBlocking {
                assertThrows<RuntimeException> {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }
            }
        }
    }
}
