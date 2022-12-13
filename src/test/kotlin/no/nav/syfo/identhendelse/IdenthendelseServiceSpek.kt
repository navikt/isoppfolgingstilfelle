package no.nav.syfo.identhendelse

import io.ktor.server.testing.*
import kotlinx.coroutines.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.database.getOppfolgingstilfelleBitForIdent
import no.nav.syfo.oppfolgingstilfelle.bit.domain.toOppfolgingstilfelleBit
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.generator.generateKafkaIdenthendelseDTO
import testhelper.generator.generateKafkaSyketilfellebitSykmeldingNy

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

                    val newTilfelleBit = generateKafkaSyketilfellebitSykmeldingNy(
                        personIdentNumber = oldIdent,
                    ).toOppfolgingstilfelleBit()

                    database.connection.use { connection ->
                        connection.createOppfolgingstilfelleBit(
                            commit = true,
                            oppfolgingstilfelleBit = newTilfelleBit,
                        )
                    }

                    val currentTilfelleBitList = database.getOppfolgingstilfelleBitForIdent(oldIdent)
                    currentTilfelleBitList.size shouldBeEqualTo 1

                    runBlocking {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    val updatedTilfelleBitList = database.getOppfolgingstilfelleBitForIdent(newIdent)
                    updatedTilfelleBitList.size shouldBeEqualTo 1
                    updatedTilfelleBitList.first().personIdentNumber.value shouldBeEqualTo newIdent.value

                    val oldTilfelleBitList = database.getOppfolgingstilfelleBitForIdent(oldIdent)
                    oldTilfelleBitList.size shouldBeEqualTo 0
                }
            }

            describe("Unhappy path") {
                it("Skal kaste feil hvis PDL ikke har oppdatert identen") {
                    val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTO(
                        personident = UserConstants.ARBEIDSTAKER_3_FNR,
                        hasOldPersonident = true,
                    )
                    val oldIdent = kafkaIdenthendelseDTO.getInactivePersonidenter().first()

                    val newTilfelleBit = generateKafkaSyketilfellebitSykmeldingNy(
                        personIdentNumber = oldIdent,
                    ).toOppfolgingstilfelleBit()

                    database.connection.use { connection ->
                        connection.createOppfolgingstilfelleBit(
                            commit = true,
                            oppfolgingstilfelleBit = newTilfelleBit,
                        )
                    }

                    val currentTilfelleBitList = database.getOppfolgingstilfelleBitForIdent(oldIdent)
                    currentTilfelleBitList.size shouldBeEqualTo 1

                    runBlocking {
                        assertFailsWith(IllegalStateException::class) {
                            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                        }
                    }
                }
            }
        }
    }
})
