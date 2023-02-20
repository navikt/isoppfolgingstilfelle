package no.nav.syfo.identhendelse

import io.ktor.server.testing.*
import kotlinx.coroutines.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.identhendelse.database.getIdentCount
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.toOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.person.database.createOppfolgingstilfellePerson
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.generator.generateKafkaIdenthendelseDTO
import testhelper.generator.generateKafkaSyketilfellebitSykmeldingNy
import testhelper.generator.generateOppfolgingstilfellePerson

object IdenthendelseServiceSpek : Spek({

    describe(IdenthendelseServiceSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val pdlClient = PdlClient(
                azureAdClient = AzureAdClient(
                    azureEnviroment = externalMockEnvironment.environment.azure,
                    redisStore = RedisStore(externalMockEnvironment.environment.redis),
                ),
                clientEnvironment = externalMockEnvironment.environment.clients.pdl,
                redisStore = RedisStore(
                    redisEnvironment = externalMockEnvironment.environment.redis,
                )
            )

            val identhendelseService = IdenthendelseService(
                database = database,
                pdlClient = pdlClient,
            )

            beforeEachTest {
                database.dropData()
            }

            describe("Happy path") {
                it("Skal oppdatere tilfelle når person har fått ny ident") {
                    val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(hasOldPersonident = true)
                    val newIdent = kafkaIdenthendelseDTO.getActivePersonident()!!
                    val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                    populateDatabase(oldIdent, database)

                    val oldIdentOccurrences = database.getIdentCount(listOf(oldIdent)) + database.getIdentCount(listOf(UserConstants.ARBEIDSTAKER_4_FNR))
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
    }
})

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
        connection.createOppfolgingstilfellePerson(true, oppfolgingstilfellePerson)
        connection.commit()
    }
}
