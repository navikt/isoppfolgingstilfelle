package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.oppfolgingstilfelle.bit.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.oppfolgingstilfelle.person.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiPersonIdentPath
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleApiV1Path
import no.nav.syfo.oppfolgingstilfelle.person.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.util.*
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

class KafkaStatusendringConsumerSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val kafkaStatusendringService = KafkaStatusendringService(
            database = database,
        )
        val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()
        val sykmeldingId = UUID.randomUUID()
        val tilfelleBitUuid = UUID.randomUUID()

        val oppfolgingstilfelleBitSykmeldingNy = OppfolgingstilfelleBit(
            uuid = tilfelleBitUuid,
            personIdentNumber = personIdentDefault,
            virksomhetsnummer = null,
            createdAt = nowUTC(),
            inntruffet = nowUTC().minusDays(1),
            fom = LocalDate.now().minusDays(1),
            tom = LocalDate.now().plusDays(1),
            tagList = listOf(
                Tag.SYKMELDING,
                Tag.NY,
                Tag.PERIODE,
                Tag.INGEN_AKTIVITET,
            ),
            ressursId = sykmeldingId.toString(),
            korrigerer = null,
            processed = false,
        )
        val tilfelleBitSykmeldingSendtUuid = UUID.randomUUID()

        val oppfolgingstilfelleBitSykmeldingSendt = oppfolgingstilfelleBitSykmeldingNy.copy(
            uuid = tilfelleBitSykmeldingSendtUuid,
            tagList = listOf(
                Tag.SYKMELDING,
                Tag.SENDT,
                Tag.PERIODE,
                Tag.INGEN_AKTIVITET,
            )
        )

        val statusendringTopicPartition = TopicPartition(
            STATUSENDRING_TOPIC,
            0,
        )

        val statusendring = generateKafkaStatusendring(
            sykmeldingId = sykmeldingId,
            personIdentNumber = personIdentDefault,
        )
        val kafkaStatusendring = ConsumerRecord(
            STATUSENDRING_TOPIC,
            0,
            1,
            sykmeldingId.toString(),
            statusendring,
        )
        val unknownSykmeldingId = UUID.randomUUID()
        val statusendringUnknownSykmelding = generateKafkaStatusendring(
            sykmeldingId = unknownSykmeldingId,
            personIdentNumber = personIdentDefault,
        )
        val kafkaStatusendringUnknownSykmelding = ConsumerRecord(
            STATUSENDRING_TOPIC,
            0,
            1,
            unknownSykmeldingId.toString(),
            statusendringUnknownSykmelding,
        )
        val mockKafkaConsumerStatusendring = mockk<KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>>()

        val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
            database = database,
            oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
                database = database,
                oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
            )
        )

        beforeEachTest {
            database.dropData()

            clearMocks(mockKafkaConsumerStatusendring)
            every { mockKafkaConsumerStatusendring.commitSync() } returns Unit

            clearMocks(oppfolgingstilfellePersonProducer)
            justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
        }

        describe(KafkaStatusendringConsumerSpek::class.java.simpleName) {
            describe("Consume statusendring from Kafka topic") {
                val url = "$oppfolgingstilfelleApiV1Path$oppfolgingstilfelleApiPersonIdentPath"
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azure.appClientId,
                    azp = testIsdialogmoteClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )
                describe("Happy path") {
                    it("should store statusendring when known sykmeldingId") {
                        database.connection.use {
                            it.createOppfolgingstilfelleBit(
                                commit = true,
                                oppfolgingstilfelleBit = oppfolgingstilfelleBitSykmeldingNy,
                            )
                        }
                        runBlocking {
                            oppfolgingstilfelleCronjob.runJob()
                        }

                        every { mockKafkaConsumerStatusendring.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                statusendringTopicPartition to listOf(
                                    kafkaStatusendring
                                )
                            )
                        )

                        kafkaStatusendringService.pollAndProcessRecords(
                            kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
                        )

                        verify(exactly = 1) {
                            mockKafkaConsumerStatusendring.commitSync()
                        }
                        runBlocking {
                            oppfolgingstilfelleCronjob.runJob()
                        }

                        database.isTilfelleBitAvbrutt(tilfelleBitUuid) shouldBeEqualTo true

                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)
                            oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList.size shouldBeEqualTo 0
                        }
                    }
                    it("should not store statusendring when known sykmeldingId and sendt") {
                        database.connection.use {
                            it.createOppfolgingstilfelleBit(
                                commit = true,
                                oppfolgingstilfelleBit = oppfolgingstilfelleBitSykmeldingSendt,
                            )
                        }
                        runBlocking {
                            oppfolgingstilfelleCronjob.runJob()
                        }
                        every { mockKafkaConsumerStatusendring.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                statusendringTopicPartition to listOf(
                                    kafkaStatusendring
                                )
                            )
                        )

                        kafkaStatusendringService.pollAndProcessRecords(
                            kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
                        )
                        runBlocking {
                            oppfolgingstilfelleCronjob.runJob()
                        }

                        verify(exactly = 1) {
                            mockKafkaConsumerStatusendring.commitSync()
                        }

                        database.isTilfelleBitAvbrutt(tilfelleBitSykmeldingSendtUuid) shouldBeEqualTo false
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)
                            oppfolgingstilfelleArbeidstakerDTO.oppfolgingstilfelleList.size shouldBeEqualTo 1
                        }
                    }
                    it("should consume statusendring with unknown sykmeldingId") {
                        every { mockKafkaConsumerStatusendring.poll(any<Duration>()) } returns ConsumerRecords(
                            mapOf(
                                statusendringTopicPartition to listOf(
                                    kafkaStatusendringUnknownSykmelding
                                )
                            )
                        )

                        kafkaStatusendringService.pollAndProcessRecords(
                            kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
                        )

                        verify(exactly = 1) {
                            mockKafkaConsumerStatusendring.commitSync()
                        }
                    }
                }
            }
        }
    }
})
