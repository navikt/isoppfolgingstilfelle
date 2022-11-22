package no.nav.syfo.oppfolgingstilfelle.cronjob

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.arbeidsforhold.ArbeidsforholdClient
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.SykmeldingNyCronjob
import no.nav.syfo.oppfolgingstilfelle.bit.database.getOppfolgingstilfelleBitForUUID
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.*
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiPersonIdentPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiV1Path
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import testhelper.generator.*
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.util.*

class OppfolgingstilfelleCronjobSpek : Spek({
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
            korrigerer = null,
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
        val kafkaSyketilfellebitRecordRelevantVirksomhetDuplicate = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            1,
            "key1",
            kafkaSyketilfellebitRelevantVirksomhet,
        )
        val kafkaSyketilfellebitNotRelevant1 = generateKafkaSyketilfellebitNotRelevantNoVirksomhet(
            personIdentNumber = personIdentDefault,
        )
        val kafkaSyketilfellebitRecordNotRelevant1 = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            3,
            "key3",
            kafkaSyketilfellebitNotRelevant1,
        )
        val kafkaSyketilfellebitSykmeldingNy = generateKafkaSyketilfellebitSykmeldingNy(
            personIdentNumber = personIdentDefault,
        )
        val kafkaSyketilfellebitRecordSykmeldingNy = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            4,
            "key4",
            kafkaSyketilfellebitSykmeldingNy,
        )
        val kafkaSyketilfellebitSykmeldingNyNoOrgNr = generateKafkaSyketilfellebitSykmeldingNy(
            personIdentNumber = ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER,
        )
        val kafkaSyketilfellebitRecordSykmeldingNyNoOrgNr = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            5,
            "key5",
            kafkaSyketilfellebitSykmeldingNyNoOrgNr,
        )
        val kafkaSyketilfellebitInntektsmelding = generateKafkaSyketilfellebitInntektsmelding(
            personIdentNumber = personIdentDefault,
        )
        val kafkaSyketilfellebitRecordInntektsmelding = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            6,
            "key6",
            kafkaSyketilfellebitInntektsmelding,
        )

        val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

        val sykmeldingNyCronJob = SykmeldingNyCronjob(
            database = database,
            arbeidsforholdClient = ArbeidsforholdClient(
                azureAdClient = AzureAdClient(
                    azureEnviroment = externalMockEnvironment.environment.azure,
                    redisStore = RedisStore(
                        redisEnvironment = externalMockEnvironment.environment.redis,
                    )
                ),
                clientEnvironment = externalMockEnvironment.environment.clients.arbeidsforhold,
            )
        )
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

        describe(OppfolgingstilfelleCronjobSpek::class.java.simpleName) {
            describe("Get OppfolgingstilfellePersonDTO for PersonIdent") {
                val url = "$oppfolgingstilfelleApiV1Path$oppfolgingstilfelleApiPersonIdentPath"
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    azp = testIsdialogmoteClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {
                    it("should create OppfolgingstilfelleBit and OppfolgingstilfellePerson if SyketilfelleBit is sykmelding ny") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordSykmeldingNy,
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        runBlocking {
                            val result = sykmeldingNyCronJob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1
                        }
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1
                        }

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

                            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfelleArbeidstakerDTO.personIdent shouldBeEqualTo oppfolgingstilfelleBit.personIdentNumber.value
                            oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList.size shouldBeEqualTo 1
                            val oppfolgingstilfelle = oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList[0]
                            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelle.virksomhetsnummerList[0] shouldBeEqualTo UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
                            oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitSykmeldingNy.fom
                            oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitSykmeldingNy.tom
                        }
                    }

                    it("should create OppfolgingstilfelleBit and OppfolgingstilfellePerson if bit is inntekstmelding") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordInntektsmelding,
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        runBlocking {
                            val result = sykmeldingNyCronJob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 0
                        }
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1
                        }

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

                            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfelleArbeidstakerDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.fnr
                            oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList.size shouldBeEqualTo 1
                            val oppfolgingstilfelle = oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList[0]
                            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                            oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelle.virksomhetsnummerList[0] shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.orgnummer
                            oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.fom
                            oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.tom
                        }
                    }

                    it("should create OppfolgingstilfelleBit and OppfolgingstilfellePerson if SyketilfelleBit is sykmelding ny and orgnr missing") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordSykmeldingNyNoOrgNr,
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 0 // since bit not ready
                        }
                        runBlocking {
                            val result = sykmeldingNyCronJob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1
                        }
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1 // since bit is ready
                        }

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }
                        verify(exactly = 1) {
                            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                        }

                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfelleArbeidstakerDTO.personIdent shouldBeEqualTo ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value
                            oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList.size shouldBeEqualTo 1
                            val oppfolgingstilfelle = oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList[0]
                            oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo false
                            oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitSykmeldingNyNoOrgNr.fom
                            oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitSykmeldingNyNoOrgNr.tom
                        }
                    }

                    it("should not create OppfolgingstilfelleBit or OppfolgingstilfellePerson if SyketilfelleBit is not relevant") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordNotRelevant1,
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        runBlocking {
                            val result = sykmeldingNyCronJob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 0
                        }
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 0
                        }

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }
                        verify(exactly = 0) {
                            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                        }

                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfelleArbeidstakerDTO.personIdent shouldBeEqualTo oppfolgingstilfelleBit.personIdentNumber.value
                            oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList.size shouldBeEqualTo 0
                        }
                    }

                    it("should produce exactly 1 Oppfolgingstilfelle for each relevant SyketilfelleBit") {
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordRelevantVirksomhet,
                                    kafkaSyketilfellebitRecordRelevantVirksomhetDuplicate,
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        runBlocking {
                            val result = sykmeldingNyCronJob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 0
                        }
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1
                        }

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }
                        verify(exactly = 1) {
                            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                        }
                    }

                    it("should update bit with korrigerer when processing duplicate") {
                        val uuid = UUID.randomUUID()
                        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                syketilfellebitTopicPartition to listOf(
                                    kafkaSyketilfellebitRecordRelevantVirksomhet,
                                    ConsumerRecord(
                                        SYKETILFELLEBIT_TOPIC,
                                        partition,
                                        1,
                                        "key1",
                                        kafkaSyketilfellebitRelevantVirksomhet.copy(
                                            korrigererSendtSoknad = uuid.toString(),
                                        ),
                                    ),
                                )
                            )
                        )

                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )
                        runBlocking {
                            val result = sykmeldingNyCronJob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 0
                        }
                        runBlocking {
                            val result = oppfolgingstilfelleCronjob.runJob()
                            result.failed shouldBeEqualTo 0
                            result.updated shouldBeEqualTo 1
                        }

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }
                        verify(exactly = 1) {
                            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                        }
                        val persisted = database.connection.use {
                            it.getOppfolgingstilfelleBitForUUID(
                                uuid = UUID.fromString(kafkaSyketilfellebitRecordRelevantVirksomhet.value().id)
                            )
                        }
                        persisted shouldNotBeEqualTo null
                        persisted!!.korrigerer shouldBeEqualTo uuid.toString()
                    }
                }
            }
        }
    }
})
