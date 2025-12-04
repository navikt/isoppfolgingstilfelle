package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.api.endpoints.OppfolgingstilfellePersonDTO
import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.infrastructure.kafka.syketilfelle.SYKETILFELLEBIT_TOPIC
import no.nav.syfo.infrastructure.kafka.syketilfelle.SyketilfellebitConsumer
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
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
import testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import testhelper.generator.generateKafkaSyketilfellebitRelevantSykmeldingBekreftet
import testhelper.generator.generateKafkaSyketilfellebitRelevantVirksomhet
import testhelper.generator.generateOppfolgingstilfellePerson
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

class OppfolgingstilfelleApiTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val tilfellebitRepository = externalMockEnvironment.tilfellebitRepository
    private val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
    private val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(tilfellebitRepository)

    private val syketilfellebitConsumer =
        SyketilfellebitConsumer(oppfolgingstilfelleBitService = oppfolgingstilfelleBitService)
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
    private val kafkaSyketilfellebitRelevantSykmeldingBekreftet =
        generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = kafkaSyketilfellebitRelevantVirksomhet.tom.plusDays(1),
            tom = kafkaSyketilfellebitRelevantVirksomhet.tom.plusDays(2),
        )
    private val kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        2,
        "key2",
        kafkaSyketilfellebitRelevantSykmeldingBekreftet,
    )

    private val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

    private val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        ),
        tilfellebitRepository = tilfellebitRepository,
    )
    private val oppfolgingstilfelleApiV1Path = "/api/internad/v1/oppfolgingstilfelle"
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
    @DisplayName("Get OppfolgingstilfellePersonDTO for PersonIdent")
    inner class GetOppfolgingstilfellePersonDTOForPersonIdent {
        private val url = "$oppfolgingstilfelleApiV1Path/personident"

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

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
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
                    assertFalse(oppfolgingstilfellePersonDTO.hasGjentakendeSykefravar!!)

                    val oppfolgingstilfelleDTO =
                        oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                    assertEquals(1, oppfolgingstilfelleDTO.virksomhetsnummerList.size)
                    assertEquals(
                        kafkaSyketilfellebitRelevantVirksomhet.orgnummer,
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first()
                    )

                    assertTrue(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelleDTO.start)
                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.tom, oppfolgingstilfelleDTO.end)
                    assertEquals(
                        ChronoUnit.DAYS.between(
                            oppfolgingstilfelleDTO.start,
                            oppfolgingstilfelleDTO.end,
                        ).toInt() + 1,
                        oppfolgingstilfelleDTO.antallSykedager
                    )
                    assertEquals(0, oppfolgingstilfelleDTO.varighetUker)
                }
            }

            @Test
            fun `should create OppfolgingstilfellePerson with dodsdato if set`() {
                every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        syketilfellebitTopicPartition to listOf(
                            kafkaSyketilfellebitRecordRelevantVirksomhet,
                        )
                    )
                )

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
                )
                oppfolgingstilfelleCronjob.runJob()
                val dodsdato = LocalDate.now().minusDays(3)
                oppfolgingstilfellePersonRepository.createPerson(
                    uuid = UUID.randomUUID(),
                    personIdent = PersonIdentNumber(kafkaSyketilfellebitRecordRelevantVirksomhet.value().fnr),
                    dodsdato = dodsdato,
                    hendelseId = UUID.randomUUID(),
                )

                testApplication {
                    val client = setupApiAndClient()
                    val oppfolgingstilfellePersonDTO =
                        client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)

                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fnr, oppfolgingstilfellePersonDTO.personIdent)
                    assertEquals(dodsdato, oppfolgingstilfellePersonDTO.dodsdato)
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

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
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
                    assertFalse(oppfolgingstilfellePersonDTO.hasGjentakendeSykefravar!!)

                    val oppfolgingstilfelleDTO = oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                    assertEquals(1, oppfolgingstilfelleDTO.virksomhetsnummerList.size)
                    assertEquals(
                        kafkaSyketilfellebitRelevantVirksomhet.orgnummer,
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first()
                    )

                    assertTrue(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelleDTO.start)
                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.tom, oppfolgingstilfelleDTO.end)
                }
            }

            @Test
            fun `should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is never Arbeidstaker in Oppfolgingstilfelle`() {
                every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        syketilfellebitTopicPartition to listOf(
                            kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet,
                        )
                    )
                )

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
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

                    assertEquals(
                        kafkaSyketilfellebitRelevantSykmeldingBekreftet.fnr,
                        oppfolgingstilfellePersonDTO.personIdent
                    )
                    assertFalse(oppfolgingstilfellePersonDTO.hasGjentakendeSykefravar!!)

                    val oppfolgingstilfelleDTO =
                        oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                    assertEquals(0, oppfolgingstilfelleDTO.virksomhetsnummerList.size)

                    assertFalse(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                    assertEquals(kafkaSyketilfellebitRelevantSykmeldingBekreftet.fom, oppfolgingstilfelleDTO.start)
                    assertEquals(kafkaSyketilfellebitRelevantSykmeldingBekreftet.tom, oppfolgingstilfelleDTO.end)
                }
            }

            @Test
            @DisplayName("Multiple Syketilfellebit, 1 poll should create 2 OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is Arbeidstaker at the start of, but not at the end of Oppfolgingstilfelle")
            fun `Multiple Syketilfellebit, 1 poll should create 2 OppfolgingstilfellePerson`() {
                every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        syketilfellebitTopicPartition to listOf(
                            kafkaSyketilfellebitRecordRelevantVirksomhet,
                            kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet,
                        )
                    )
                )

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
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

                    assertEquals(
                        kafkaSyketilfellebitRelevantSykmeldingBekreftet.fnr,
                        oppfolgingstilfellePersonDTO.personIdent
                    )

                    val oppfolgingstilfelleDTO =
                        oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                    assertEquals(1, oppfolgingstilfelleDTO.virksomhetsnummerList.size)
                    assertEquals(
                        kafkaSyketilfellebitRelevantVirksomhet.orgnummer,
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first()
                    )
                    assertFalse(oppfolgingstilfellePersonDTO.hasGjentakendeSykefravar!!)

                    assertFalse(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelleDTO.start)
                    assertEquals(kafkaSyketilfellebitRelevantSykmeldingBekreftet.tom, oppfolgingstilfelleDTO.end)
                }
            }

            @Test
            @DisplayName("Multiple polls, 1 Syketilfellebit should create 2 OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is Arbeidstaker at the start of, but not at the end of Oppfolgingstilfelle")
            fun `Multiple polls, 1 Syketilfellebit should create 2 OppfolgingstilfellePerson`() {
                every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        syketilfellebitTopicPartition to listOf(
                            kafkaSyketilfellebitRecordRelevantVirksomhet,
                        )
                    )
                ) andThen ConsumerRecords(
                    mapOf(
                        syketilfellebitTopicPartition to listOf(
                            kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet,
                        )
                    )
                )

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
                )

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
                )
                oppfolgingstilfelleCronjob.runJob()

                verify(exactly = 2) {
                    mockKafkaConsumerSyketilfelleBit.commitSync()
                }
                verify(exactly = 2) {
                    oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                }

                testApplication {
                    val client = setupApiAndClient()
                    val oppfolgingstilfellePersonDTO =
                        client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)

                    assertEquals(
                        kafkaSyketilfellebitRelevantSykmeldingBekreftet.fnr,
                        oppfolgingstilfellePersonDTO.personIdent
                    )

                    val oppfolgingstilfelleDTO =
                        oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                    assertEquals(1, oppfolgingstilfelleDTO.virksomhetsnummerList.size)
                    assertEquals(
                        kafkaSyketilfellebitRelevantVirksomhet.orgnummer,
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first()
                    )
                    assertFalse(oppfolgingstilfellePersonDTO.hasGjentakendeSykefravar!!)

                    assertFalse(oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd)
                    assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelleDTO.start)
                    assertEquals(kafkaSyketilfellebitRelevantSykmeldingBekreftet.tom, oppfolgingstilfelleDTO.end)
                    assertEquals(0, oppfolgingstilfelleDTO.varighetUker)
                }
            }

            fun kafkaSyketilfellebiterToConsumerRecords(kafkaSyketilfellebiter: List<KafkaSyketilfellebit>): List<ConsumerRecord<String, KafkaSyketilfellebit>> =
                kafkaSyketilfellebiter.mapIndexed { index, tilfellebit ->
                    ConsumerRecord(
                        SYKETILFELLEBIT_TOPIC,
                        partition,
                        (index + 1).toLong(),
                        "key${index + 1}",
                        tilfellebit,
                    )
                }

            fun generateKafkaSyketilfellebit(fom: LocalDate, tom: LocalDate): KafkaSyketilfellebit =
                generateKafkaSyketilfellebitRelevantVirksomhet().copy(
                    fom = fom,
                    tom = tom,
                )

            @Test
            fun `5 Syketilfellebiter with sum of sickdays being above 100 returns OppfolgingstilfellePersonDTO with gjentakende sykefravær set to true`() {
                every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                    mapOf(
                        syketilfellebitTopicPartition to kafkaSyketilfellebiterToConsumerRecords(
                            listOf(
                                generateKafkaSyketilfellebit(
                                    LocalDate.now().minusDays(180),
                                    LocalDate.now().minusDays(130)
                                ),
                                generateKafkaSyketilfellebit(
                                    LocalDate.now().minusDays(110),
                                    LocalDate.now().minusDays(100)
                                ),
                                generateKafkaSyketilfellebit(
                                    LocalDate.now().minusDays(80),
                                    LocalDate.now().minusDays(70)
                                ),
                                generateKafkaSyketilfellebit(
                                    LocalDate.now().minusDays(50),
                                    LocalDate.now().minusDays(40)
                                ),
                                generateKafkaSyketilfellebit(
                                    LocalDate.now().minusDays(20),
                                    LocalDate.now()
                                ),
                            )
                        )
                    )
                )

                syketilfellebitConsumer.pollAndProcessRecords(
                    consumer = mockKafkaConsumerSyketilfelleBit,
                )
                oppfolgingstilfelleCronjob.runJob()

                verify(exactly = 1) {
                    mockKafkaConsumerSyketilfelleBit.commitSync()
                }
                verify(exactly = 5) {
                    oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                }
                testApplication {
                    val client = setupApiAndClient()
                    val oppfolgingstilfellePersonDTO =
                        client.getOppfolgingstilfellePerson(url, validToken, PERSONIDENTNUMBER_DEFAULT)

                    assertEquals(PERSONIDENTNUMBER_DEFAULT.value, oppfolgingstilfellePersonDTO.personIdent)
                    assertEquals(5, oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.size)
                    assertTrue(oppfolgingstilfellePersonDTO.hasGjentakendeSykefravar!!)
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
            fun `should return status Forbidden if denied access to personident supplied in NAV_PERSONIDENT_HEADER`() {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(url) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
                    }

                    assertEquals(HttpStatusCode.Forbidden, response.status)
                }
            }
        }
    }

    @Nested
    @DisplayName("Get list of OppfolgingstilfellePersonDTO for persons")
    inner class GetListOfOppfolgingstilfellePersonDTOForPersons {
        private val url = "$oppfolgingstilfelleApiV1Path/persons"

        @Nested
        @DisplayName("Happy path")
        inner class HappyPath {
            @Test
            fun `Returns oppfolgingstilfeller for persons`() {
                val antallSykedagerPerson1 = 100
                val antallSykedagerPerson2Tilfelle2 = 80
                val oppfolgingstilfellePerson1 = generateOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_FNR,
                    antallSykedager = antallSykedagerPerson1,
                )
                val oppfolgingstilfelle1Person2 = generateOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_2_FNR,
                )
                val oppfolgingstilfelle2Person2 = generateOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_2_FNR,
                    antallSykedager = antallSykedagerPerson2Tilfelle2,
                )

                listOf(oppfolgingstilfellePerson1, oppfolgingstilfelle1Person2, oppfolgingstilfelle2Person2).forEach {
                    oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(it)
                }
                oppfolgingstilfellePersonRepository.createPerson(
                    uuid = UUID.randomUUID(),
                    personIdent = UserConstants.ARBEIDSTAKER_2_FNR,
                    dodsdato = LocalDate.now().minusDays(1),
                    hendelseId = UUID.randomUUID()
                )

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(url) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(
                            listOf(
                                UserConstants.ARBEIDSTAKER_FNR.value,
                                UserConstants.ARBEIDSTAKER_2_FNR.value,
                            )
                        )
                    }

                    assertEquals(HttpStatusCode.OK, response.status)
                    val oppfolgingstilfellePersonDTOS = response.body<List<OppfolgingstilfellePersonDTO>>()
                    assertEquals(2, oppfolgingstilfellePersonDTOS.size)
                    val first = oppfolgingstilfellePersonDTOS.first()
                    val last = oppfolgingstilfellePersonDTOS.last()
                    assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, first.personIdent)
                    assertTrue(first.oppfolgingstilfelleList.isNotEmpty())
                    assertEquals(antallSykedagerPerson1, first.oppfolgingstilfelleList.first().antallSykedager)
                    assertNull(first.hasGjentakendeSykefravar)
                    assertEquals(UserConstants.ARBEIDSTAKER_2_FNR.value, last.personIdent)
                    assertTrue(last.oppfolgingstilfelleList.isNotEmpty())
                    assertEquals(antallSykedagerPerson2Tilfelle2, last.oppfolgingstilfelleList.first().antallSykedager)
                    assertNotNull(last.dodsdato)
                    assertNull(last.hasGjentakendeSykefravar)
                }
            }

            @Test
            fun `Returns oppfolgingstilfeller for persons with different values for hasGjentakendeSykefravar`() {
                val antallSykedagerPerson1 = 100
                val antallSykedagerPerson2 = 150
                val antallSykedagerPerson3 = 350
                val pastOppfolgingstilfellePerson1 = generateOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_FNR,
                    antallSykedager = antallSykedagerPerson1,
                    hasGjentakendeSykefravar = null,
                )
                val oppfolgingstilfellePerson2 = generateOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_2_FNR,
                    antallSykedager = antallSykedagerPerson2,
                    hasGjentakendeSykefravar = false,
                )
                val oppfolgingstilfellePerson3 = generateOppfolgingstilfellePerson(
                    personIdent = UserConstants.ARBEIDSTAKER_3_FNR,
                    antallSykedager = antallSykedagerPerson3,
                    hasGjentakendeSykefravar = true,
                )

                listOf(
                    pastOppfolgingstilfellePerson1,
                    oppfolgingstilfellePerson2,
                    oppfolgingstilfellePerson3
                ).forEach {
                    oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(it)
                }

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(url) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(
                            listOf(
                                UserConstants.ARBEIDSTAKER_FNR.value,
                                UserConstants.ARBEIDSTAKER_2_FNR.value,
                                UserConstants.ARBEIDSTAKER_3_FNR.value,
                            )
                        )
                    }

                    assertEquals(HttpStatusCode.OK, response.status)
                    val oppfolgingstilfellePersonDTOS = response.body<List<OppfolgingstilfellePersonDTO>>()
                    assertEquals(3, oppfolgingstilfellePersonDTOS.size)

                    val first = oppfolgingstilfellePersonDTOS.first()
                    assertEquals(UserConstants.ARBEIDSTAKER_FNR.value, first.personIdent)
                    assertTrue(first.oppfolgingstilfelleList.isNotEmpty())
                    assertEquals(antallSykedagerPerson1, first.oppfolgingstilfelleList.first().antallSykedager)
                    assertNull(first.hasGjentakendeSykefravar)

                    val second = oppfolgingstilfellePersonDTOS[1]
                    assertEquals(UserConstants.ARBEIDSTAKER_2_FNR.value, second.personIdent)
                    assertTrue(second.oppfolgingstilfelleList.isNotEmpty())
                    assertEquals(antallSykedagerPerson2, second.oppfolgingstilfelleList.first().antallSykedager)
                    assertFalse(second.hasGjentakendeSykefravar!!)

                    val last = oppfolgingstilfellePersonDTOS[2]
                    assertEquals(UserConstants.ARBEIDSTAKER_3_FNR.value, last.personIdent)
                    assertTrue(last.oppfolgingstilfelleList.isNotEmpty())
                    assertEquals(antallSykedagerPerson3, last.oppfolgingstilfelleList.first().antallSykedager)
                    assertTrue(last.hasGjentakendeSykefravar!!)
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
                    val response = client.post(url)

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                }
            }

            @Test
            fun `returns empty list if no access to persons`() {
                val oppfolgingstilfellePerson1 = generateOppfolgingstilfellePerson(
                    personIdent = PERSONIDENTNUMBER_VEILEDER_NO_ACCESS,
                )

                oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(
                    oppfolgingstilfellePerson = oppfolgingstilfellePerson1
                )

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(url) {
                        bearerAuth(validToken)
                        contentType(ContentType.Application.Json)
                        setBody(listOf(PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value))
                    }

                    assertEquals(HttpStatusCode.OK, response.status)
                    val oppfolgingstilfellePersonDTOS = response.body<List<OppfolgingstilfellePersonDTO>>()
                    assertTrue(oppfolgingstilfellePersonDTOS.isEmpty())
                }
            }
        }
    }
}
