package no.nav.syfo.identhendelse

import kotlinx.coroutines.runBlocking
import no.nav.syfo.api.cache.ValkeyStore
import no.nav.syfo.application.IdenthendelseService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.toOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pdl.PdlClient
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.getIdentCount
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
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

class IdenthendelseServiceSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val redisConfig = externalMockEnvironment.environment.valkeyConfig

    val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    val tilfelleBitRepository = externalMockEnvironment.tilfellebitRepository
    val pdlClient = PdlClient(
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

    val identhendelseService = IdenthendelseService(
        database = database,
        pdlClient = pdlClient,
    )

    fun populateDatabase(oldIdent: PersonIdentNumber, database: DatabaseInterface) {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(oldIdent)
        val newTilfelleBit = generateKafkaSyketilfellebitSykmeldingNy(
            personIdentNumber = UserConstants.ARBEIDSTAKER_4_FNR,
        ).toOppfolgingstilfelleBit().copy(
            processed = true
        )

        tilfelleBitRepository.createOppfolgingstilfelleBit(
            oppfolgingstilfelleBit = newTilfelleBit,
        )
        oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
            oppfolgingstilfellePerson = oppfolgingstilfellePerson
        )
    }

    describe(IdenthendelseServiceSpek::class.java.simpleName) {

        beforeEachTest {
            database.dropData()
        }

        describe("Happy path") {
            it("Skal oppdatere tilfelle når person har fått ny ident") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
                val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                populateDatabase(oldIdent, database)

                val oldIdentOccurrences =
                    database.getIdentCount(listOf(oldIdent)) + database.getIdentCount(listOf(UserConstants.ARBEIDSTAKER_4_FNR))
                oldIdentOccurrences shouldBeEqualTo 2

                runBlocking {
                    identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                }

                val newIdentOccurrences = database.getIdentCount(listOf(newIdent))
                newIdentOccurrences shouldBeEqualTo 2
            }
        }

        describe("Unhappy path") {
            it("Skal kaste feil hvis PDL ikke har oppdatert identen") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                    personident = UserConstants.ARBEIDSTAKER_3_FNR,
                    hasOldPersonident = true,
                )
                val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                populateDatabase(oldIdent, database)

                runBlocking {
                    assertFailsWith(IllegalStateException::class) {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }
                }
            }
            it("Skal kaste RuntimeException hvis PDL gir en not_found ved henting av identer") {
                val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                    personident = UserConstants.ARBEIDSTAKER_WITH_ERROR,
                    hasOldPersonident = true,
                )
                val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                populateDatabase(oldIdent, database)

                runBlocking {
                    assertFailsWith(RuntimeException::class) {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }
                }
            }
        }
    }
})
