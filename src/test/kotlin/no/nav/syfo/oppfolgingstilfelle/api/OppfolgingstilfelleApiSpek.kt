package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.KafkaSyketilfellebitService
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle.SYKETILFELLEBIT_TOPIC
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiPersonIdentPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiPersonsPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiV1Path
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.personhendelse.db.createPerson
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import org.amshove.kluent.*
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
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

class OppfolgingstilfelleApiSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database

    val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfelleRepository
    val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
    val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService()

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
    val kafkaSyketilfellebitRelevantSykmeldingBekreftet =
        generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = kafkaSyketilfellebitRelevantVirksomhet.tom.plusDays(1),
            tom = kafkaSyketilfellebitRelevantVirksomhet.tom.plusDays(2),
        )
    val kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        2,
        "key2",
        kafkaSyketilfellebitRelevantSykmeldingBekreftet,
    )

    val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

    val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        database = database,
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            database = database,
            oppfolgingstilfelleRepository = oppfolgingstilfelleRepository,
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

    describe(OppfolgingstilfelleApiSpek::class.java.simpleName) {
        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            azp = testIsdialogmoteClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        )

        describe("Get OppfolgingstilfellePersonDTO for PersonIdent") {
            val url = "$oppfolgingstilfelleApiV1Path$oppfolgingstilfelleApiPersonIdentPath"

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

                    testApplication {
                        val client = setupApiAndClient()
                        val oppfolgingstilfellePersonDTO = client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)

                        oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fnr
                        oppfolgingstilfellePersonDTO.dodsdato shouldBe null

                        val oppfolgingstilfelleDTO =
                            oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                        oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.orgnummer

                        oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                        oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                        oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.tom
                        oppfolgingstilfelleDTO.antallSykedager shouldBeEqualTo ChronoUnit.DAYS.between(
                            oppfolgingstilfelleDTO.start,
                            oppfolgingstilfelleDTO.end,
                        ).toInt() + 1
                        oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 0
                    }
                }
                it("should create OppfolgingstilfellePerson with dodsdato if set") {
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
                    val dodsdato = LocalDate.now().minusDays(3)
                    database.connection.use {
                        it.createPerson(
                            uuid = UUID.randomUUID(),
                            personIdent = PersonIdentNumber(kafkaSyketilfellebitRecordRelevantVirksomhet.value().fnr),
                            dodsdato = dodsdato,
                            hendelseId = UUID.randomUUID(),
                        )
                        it.commit()
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val oppfolgingstilfellePersonDTO =
                            client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)

                        oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fnr
                        oppfolgingstilfellePersonDTO.dodsdato shouldBeEqualTo dodsdato
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

                    testApplication {
                        val client = setupApiAndClient()
                        val oppfolgingstilfellePersonDTO =
                            client.getOppfolgingstilfellePerson(url, validToken, personIdentDefault)

                        oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fnr
                        oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.size shouldBeEqualTo 1

                        val oppfolgingstilfelleDTO = oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                        oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.orgnummer

                        oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                        oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                        oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.tom
                    }
                }

                it("should create OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is never Arbeidstaker in Oppfolgingstilfelle") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet,
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

                        oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.fnr

                        val oppfolgingstilfelleDTO =
                            oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                        oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 0

                        oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo false
                        oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.fom
                        oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.tom
                    }
                }

                it("Multiple Syketilfellebit, 1 poll: should create 2 OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is Arbeidstaker at the start of, but not at the end of Oppfolgingstilfelle") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordRelevantVirksomhet,
                                kafkaSyketilfellebitRecordRelevantSykmeldingBekreftet,
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

                        oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.fnr

                        val oppfolgingstilfelleDTO =
                            oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                        oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.orgnummer

                        oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo false
                        oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                        oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.tom
                    }
                }

                it("Multiple polls, 1 Syketilfellebit: should create 2 OppfolgingstilfellePerson and return OppfolgingstilfelleDTO for Person that is Arbeidstaker at the start of, but not at the end of Oppfolgingstilfelle") {
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

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
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

                        oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.fnr

                        val oppfolgingstilfelleDTO =
                            oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                        oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                        oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.orgnummer

                        oppfolgingstilfelleDTO.arbeidstakerAtTilfelleEnd shouldBeEqualTo false
                        oppfolgingstilfelleDTO.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                        oppfolgingstilfelleDTO.end shouldBeEqualTo kafkaSyketilfellebitRelevantSykmeldingBekreftet.tom
                        oppfolgingstilfelleDTO.varighetUker shouldBeEqualTo 0
                    }
                }
            }

            describe("Unhappy paths") {
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(url)

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(url) {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(url) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdentDefault.value.drop(1))
                        }

                        response.status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }

                it("should return status Forbidden if denied access to personident supplied in $NAV_PERSONIDENT_HEADER") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(url) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }

        describe("Get list of OppfolgingstilfellePersonDTO for persons") {
            val url = "$oppfolgingstilfelleApiV1Path$oppfolgingstilfelleApiPersonsPath"

            describe("Happy path") {
                it("Returns oppfolgingstilfeller for persons") {
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

                    database.connection.use { connection ->
                        listOf(oppfolgingstilfellePerson1, oppfolgingstilfelle1Person2, oppfolgingstilfelle2Person2).forEach {
                            oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(connection = connection, commit = false, it)
                        }
                        connection.createPerson(
                            uuid = UUID.randomUUID(),
                            personIdent = UserConstants.ARBEIDSTAKER_2_FNR,
                            dodsdato = LocalDate.now().minusDays(1),
                            hendelseId = UUID.randomUUID()
                        )
                        connection.commit()
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
                                )
                            )
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val oppfolgingstilfellePersonDTOS = response.body<List<OppfolgingstilfellePersonDTO>>()
                        oppfolgingstilfellePersonDTOS.size shouldBeEqualTo 2
                        val first = oppfolgingstilfellePersonDTOS.first()
                        val last = oppfolgingstilfellePersonDTOS.last()
                        first.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_FNR.value
                        first.oppfolgingstilfelleList.shouldNotBeEmpty()
                        first.oppfolgingstilfelleList.first().antallSykedager shouldBeEqualTo antallSykedagerPerson1
                        last.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_2_FNR.value
                        last.oppfolgingstilfelleList.shouldNotBeEmpty()
                        last.oppfolgingstilfelleList.first().antallSykedager shouldBeEqualTo antallSykedagerPerson2Tilfelle2
                        last.dodsdato.shouldNotBeNull()
                    }
                }
            }
            describe("Unhappy paths") {
                it("should return status Unauthorized if no token is supplied") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url)

                        response.status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }

                it("returns empty list if no access to persons") {
                    val oppfolgingstilfellePerson1 = generateOppfolgingstilfellePerson(
                        personIdent = PERSONIDENTNUMBER_VEILEDER_NO_ACCESS,
                    )

                    database.connection.use { connection ->
                        oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
                            connection = connection,
                            commit = true,
                            oppfolgingstilfellePerson = oppfolgingstilfellePerson1
                        )
                        connection.commit()
                    }

                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.post(url) {
                            bearerAuth(validToken)
                            contentType(ContentType.Application.Json)
                            setBody(listOf(PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value))
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val oppfolgingstilfellePersonDTOS = response.body<List<OppfolgingstilfellePersonDTO>>()
                        oppfolgingstilfellePersonDTOS.shouldBeEmpty()
                    }
                }
            }
        }
    }
})
