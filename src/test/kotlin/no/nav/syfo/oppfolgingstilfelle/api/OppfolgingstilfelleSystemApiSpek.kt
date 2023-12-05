package no.nav.syfo.oppfolgingstilfelle.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.*
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleSystemApiPersonIdentPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleSystemApiV1Path
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
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
        val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService()

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = database,
            oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
        )
        val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()

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

        val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
            database = database,
            oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
                database = database,
                oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
            )
        )

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
                        oppfolgingstilfelleCronjob.runJob()

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

                            oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fnr
                            oppfolgingstilfellePersonDTO.dodsdato shouldBe null

                            val oppfolgingstilfelleDTO =
                                oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                            oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.orgnummer

                            oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                            oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.tom
                            oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo 3
                            oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 0
                        }
                    }
                    it("should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with correct varighet 2 weeks") {
                        val kafkaSyketilfelle = kafkaSyketilfellebitRelevantVirksomhet.copy(
                            fom = LocalDate.now().minusDays(19),
                            tom = LocalDate.now(),
                        )
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        1,
                                        "key1",
                                        kafkaSyketilfelle,
                                    )
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        oppfolgingstilfelleCronjob.runJob()
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)
                            val oppfolgingstilfelleDTO =
                                oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                            oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfelle.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfelle.tom
                            oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo 20
                            oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 2
                        }
                    }
                    it("should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with correct varighet 3 weeks") {
                        val kafkaSyketilfelle = kafkaSyketilfellebitRelevantVirksomhet.copy(
                            fom = LocalDate.now().minusDays(20),
                            tom = LocalDate.now(),
                        )
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        1,
                                        "key1",
                                        kafkaSyketilfelle,
                                    )
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        oppfolgingstilfelleCronjob.runJob()
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)
                            val oppfolgingstilfelleDTO =
                                oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                            oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfelle.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfelle.tom
                            oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo 21
                            oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 3
                        }
                    }
                    it("should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with correct varighet relative to today") {
                        val kafkaSyketilfelle = kafkaSyketilfellebitRelevantVirksomhet.copy(
                            fom = LocalDate.now().minusDays(19),
                            tom = LocalDate.now().plusDays(4),
                        )
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        1,
                                        "key1",
                                        kafkaSyketilfelle,
                                    )
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        oppfolgingstilfelleCronjob.runJob()
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)
                            val oppfolgingstilfelleDTO =
                                oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                            oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfelle.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfelle.tom
                            oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo 24
                            oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 2
                        }
                    }
                    it("should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with egenmelding") {
                        val kafkaEgenmelding = kafkaSyketilfellebitRelevantVirksomhet.copy(
                            id = UUID.randomUUID().toString(),
                            opprettet = nowUTC(),
                            inntruffet = nowUTC().minusDays(24),
                            fom = LocalDate.now().minusDays(24),
                            tom = LocalDate.now().minusDays(23),
                            tags = listOf(
                                Tag.SYKMELDING,
                                Tag.SENDT,
                                Tag.EGENMELDING,
                            ).map { tag -> tag.name },
                        )
                        val kafkaSyketilfelle = kafkaSyketilfellebitRelevantVirksomhet.copy(
                            id = UUID.randomUUID().toString(),
                            opprettet = nowUTC(),
                            inntruffet = nowUTC(),
                            fom = LocalDate.now().minusDays(18),
                            tom = LocalDate.now(),
                        )
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        1,
                                        "key1",
                                        kafkaEgenmelding,
                                    ),
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        2,
                                        "key2",
                                        kafkaSyketilfelle,
                                    ),
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        oppfolgingstilfelleCronjob.runJob()
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)
                            val oppfolgingstilfelleDTO =
                                oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                            oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaEgenmelding.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfelle.tom
                            oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo 21
                            oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 3
                        }
                    }
                    it("should not return future oppfolgingstilfelle when using get in api") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordRelevantVirksomhet,
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        2,
                                        "key2",
                                        kafkaSyketilfellebitRelevantVirksomhet.copy(
                                            id = UUID.randomUUID().toString(),
                                            fom = LocalDate.now().plusDays(20),
                                            tom = LocalDate.now().plusDays(24),
                                        ),
                                    )
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        oppfolgingstilfelleCronjob.runJob()

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }
                        verify(exactly = 2) {
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

                            oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fnr
                            oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.size shouldBeEqualTo 1

                            val oppfolgingstilfelleDTO = oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                            oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.orgnummer

                            oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                            oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.tom
                            oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo 3
                            oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 0
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
