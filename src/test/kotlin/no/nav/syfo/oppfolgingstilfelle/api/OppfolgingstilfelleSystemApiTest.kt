package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebitService
import no.nav.syfo.infrastructure.kafka.syketilfelle.SYKETILFELLEBIT_TOPIC
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleSystemApiPersonIdentPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleSystemApiV1Path
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.nowUTC
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.*
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.generator.generateKafkaSyketilfellebitRelevantVirksomhet
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.util.*

class OppfolgingstilfelleSystemApiTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
    private val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService()

    private val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
        database = database,
        oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
    )
    private val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()

    private val partition = 0
    private val syketilfellebitTopicPartition = TopicPartition(
        SYKETILFELLEBIT_TOPIC,
        partition,
    )

    private val kafkaSyketilfellebitRelevantVirksomhet = generateKafkaSyketilfellebitRelevantVirksomhet(
        personIdent = personIdentDefault,
    )
    private val kafkaSyketilfellebitRecordRelevantVirksomhet = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        1,
        "key1",
        kafkaSyketilfellebitRelevantVirksomhet,
    )

    private val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

    private val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        database = database,
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        )
    )

    private val url = "$oppfolgingstilfelleSystemApiV1Path$oppfolgingstilfelleSystemApiPersonIdentPath"
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        azp = testIsdialogmoteClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()

        clearMocks(mockKafkaConsumerSyketilfelleBit)
        every { mockKafkaConsumerSyketilfelleBit.commitSync() } returns Unit

        clearMocks(oppfolgingstilfellePersonProducer)
        justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        @Test
        fun `should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is always Arbeidstaker in Oppfolgingstilfelle`() {
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

            testApplication {
                val client = setupApiAndClient()
                val oppfolgingstilfellePersonDTO =
                    client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)

                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fnr, oppfolgingstilfellePersonDTO.personIdent)
                assertNull(oppfolgingstilfellePersonDTO.dodsdato)

                val oppfolgingstilfelleDTO =
                    oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                assertEquals(1, oppfolgingstilfelleDTO.virksomhetsnummerList.size)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.orgnummer, oppfolgingstilfelleDTO.virksomhetsnummerList.first())

                assertTrue(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelleDTO.start)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.tom, oppfolgingstilfelleDTO.end)
                assertEquals(3, oppfolgingstilfelleDTO.antallSykedager)
                assertEquals(0, oppfolgingstilfelleDTO.varighetUker)
            }
        }

        @Test
        fun `should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with correct varighet 2 weeks`() {
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

            testApplication {
                val client = setupApiAndClient()
                val oppfolgingstilfellePersonDTO =
                    client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)
                val oppfolgingstilfelleDTO =
                    oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                assertEquals(kafkaSyketilfelle.fom, oppfolgingstilfelleDTO.start)
                assertEquals(kafkaSyketilfelle.tom, oppfolgingstilfelleDTO.end)
                assertEquals(20, oppfolgingstilfelleDTO.antallSykedager)
                assertEquals(2, oppfolgingstilfelleDTO.varighetUker)
            }
        }

        @Test
        fun `should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with correct varighet 3 weeks`() {
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

            testApplication {
                val client = setupApiAndClient()
                val oppfolgingstilfellePersonDTO =
                    client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)
                val oppfolgingstilfelleDTO =
                    oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                assertEquals(kafkaSyketilfelle.fom, oppfolgingstilfelleDTO.start)
                assertEquals(kafkaSyketilfelle.tom, oppfolgingstilfelleDTO.end)
                assertEquals(21, oppfolgingstilfelleDTO.antallSykedager)
                assertEquals(3, oppfolgingstilfelleDTO.varighetUker)
            }
        }

        @Test
        fun `should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with correct varighet relative to today`() {
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

            testApplication {
                val client = setupApiAndClient()
                val oppfolgingstilfellePersonDTO =
                    client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)
                val oppfolgingstilfelleDTO =
                    oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                assertEquals(kafkaSyketilfelle.fom, oppfolgingstilfelleDTO.start)
                assertEquals(kafkaSyketilfelle.tom, oppfolgingstilfelleDTO.end)
                assertEquals(24, oppfolgingstilfelleDTO.antallSykedager)
                assertEquals(2, oppfolgingstilfelleDTO.varighetUker)
            }
        }

        @Test
        fun `should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person with egenmelding`() {
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

            testApplication {
                val client = setupApiAndClient()
                val oppfolgingstilfellePersonDTO =
                    client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)
                val oppfolgingstilfelleDTO =
                    oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()
                assertEquals(kafkaEgenmelding.fom, oppfolgingstilfelleDTO.start)
                assertEquals(kafkaSyketilfelle.tom, oppfolgingstilfelleDTO.end)
                assertEquals(21, oppfolgingstilfelleDTO.antallSykedager)
                assertEquals(3, oppfolgingstilfelleDTO.varighetUker)
            }
        }

        @Test
        fun `should not return future oppfolgingstilfelle when using get in api`() {
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

            testApplication {
                val client = setupApiAndClient()
                val oppfolgingstilfellePersonDTO =
                    client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fnr, oppfolgingstilfellePersonDTO.personIdent)
                assertEquals(1, oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.size)

                val oppfolgingstilfelleDTO = oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                assertEquals(1, oppfolgingstilfelleDTO.virksomhetsnummerList.size)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.orgnummer, oppfolgingstilfelleDTO.virksomhetsnummerList.first())

                assertTrue(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelleDTO.start)
                assertEquals(kafkaSyketilfellebitRelevantVirksomhet.tom, oppfolgingstilfelleDTO.end)
                assertEquals(3, oppfolgingstilfelleDTO.antallSykedager)
                assertEquals(0, oppfolgingstilfelleDTO.varighetUker)
            }
        }
    }

    @Nested
    @DisplayName("Unhappy paths")
    inner class UnhappyPaths {
        @Test
        fun `should return status Unauthorized if no token is supplied`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(url)

                assertEquals(HttpStatusCode.Unauthorized, response.status)
            }
        }

        @Test
        fun `should return status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(url) {
                    bearerAuth(validToken)
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

        @Test
        fun `should return status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() {
            testApplication {
                val client = setupApiAndClient()
                val response = client.get(url) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, personIdentDefault.value.drop(1))
                }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

        @Test
        fun `should return status Forbidden if wrong azp`() {
            val invalidToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                azp = testIsnarmesteLederClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            )

            testApplication {
                val client = setupApiAndClient()
                val response = client.get(url) {
                    bearerAuth(invalidToken)
                    header(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
