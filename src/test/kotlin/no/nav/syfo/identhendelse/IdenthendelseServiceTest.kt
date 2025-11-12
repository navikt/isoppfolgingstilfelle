package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.cache.ValkeyStore
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.toOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.bit.createOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.database.getIdentCount
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.generator.generateKafkaIdenthendelseDTO
import testhelper.generator.generateKafkaSyketilfellebitSykmeldingNy
import testhelper.generator.generateOppfolgingstilfellePerson

class IdenthendelseServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val redisConfig = externalMockEnvironment.environment.valkeyConfig

    private val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val pdlClient = PdlClient(
        azureAdClient = AzureAdClient(
            azureEnviroment = externalMockEnvironment.environment.azure,
            valkeyStore = ValkeyStore(
                JedisPool(
                    JedisPoolConfig(),
                    HostAndPort(redisConfig.host, redisConfig.port),
                    DefaultJedisClientConfig.builder()
                        .ssl(redisConfig.ssl)
                        .password(redisConfig.valkeyPassword)
                        .build()
                )
            ),
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

        database.connection.use { connection ->
            connection.createOppfolgingstilfelleBit(
                commit = true,
                oppfolgingstilfelleBit = newTilfelleBit,
            )
            oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
                connection = connection,
                commit = true,
                oppfolgingstilfellePerson = oppfolgingstilfellePerson
            )
            connection.commit()
        }
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
