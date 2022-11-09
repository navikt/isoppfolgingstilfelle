package no.nav.syfo.oppfolgingstilfelle.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.*
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleSystemApiPersonIdentPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleSystemApiV1Path
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import testhelper.generator.*
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.util.*

class OppfolgingstilfelleSystemApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
        val oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            database = database,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        )
        val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(
            database = database,
            oppfolgingstilfellePersonService = oppfolgingstilfellePersonService,
        )

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = database,
            oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
            cronjobEnabled = externalMockEnvironment.environment.cronjobSyketilfellebitProcessingEnabled,
        )
        val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()

        val oppfolgingstilfelleBit = OppfolgingstilfelleBit(
            uuid = UUID.randomUUID(),
            personIdentNumber = personIdentDefault,
            virksomhetsnummer = VIRKSOMHETSNUMMER_DEFAULT.value,
            createdAt = nowUTC(),
            inntruffet = nowUTC().minusDays(1),
            fom = LocalDate.now().minusDays(1),
            tom = LocalDate.now().plusDays(1),
            tagList = listOf(
                Tag.SYKEPENGESOKNAD,
                Tag.SENDT,
            ),
            ressursId = UUID.randomUUID().toString(),
        )

        val partition = 0
        val syketilfellebitTopicPartition = TopicPartition(
            SYKETILFELLEBIT_TOPIC,
            partition,
        )

        val kafkaSyketilfellebitRelevantVirksomhet = generateKafkaSyketilfellebitRelevantVirksomhet(
            personIdent = personIdentDefault,
        )
        val kafkaSyketilfellebitRecordRelevantVirksomhet = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            1,
            "key1",
            kafkaSyketilfellebitRelevantVirksomhet,
        )

        val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

        beforeEachTest {
            database.dropData()

            clearMocks(mockKafkaConsumerSyketilfelleBit)
            every { mockKafkaConsumerSyketilfelleBit.commitSync() } returns Unit

            clearMocks(oppfolgingstilfellePersonProducer)
            justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
        }

        describe(OppfolgingstilfelleSystemApiSpek::class.java.simpleName) {
            describe("Get OppfolgingstilfellePersonDTO for PersonIdent") {
                val url = "$oppfolgingstilfelleSystemApiV1Path$oppfolgingstilfelleSystemApiPersonIdentPath"
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    azp = testIsdialogmoteClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {
                    it("should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is always Arbeidstaker in Oppfolgingstilfelle") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordRelevantVirksomhet,
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }
                        verify(exactly = 1) {
                            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                        }

                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo oppfolgingstilfelleBit.personIdentNumber.value

                            val oppfolgingstilfelleDTO =
                                oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                            oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo oppfolgingstilfelleBit.virksomhetsnummer

                            oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                            oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfelleBit.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo oppfolgingstilfelleBit.tom
                        }
                    }
                }
                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value.drop(1))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status Forbidden if wrong azp") {
                        val invalidToken = generateJWT(
                            audience = externalMockEnvironment.environment.azure.appClientId,
                            azp = testIsnarmesteLederClientId,
                            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                        )
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(invalidToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }
            }
        }
    }
})
