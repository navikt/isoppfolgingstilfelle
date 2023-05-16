package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
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

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService()

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val kafkaStatusendringService = KafkaStatusendringService(
            database = database,
            oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
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

        beforeEachTest {
            database.dropData()

            clearMocks(mockKafkaConsumerStatusendring)
            every { mockKafkaConsumerStatusendring.commitSync() } returns Unit
        }

        describe(KafkaStatusendringConsumerSpek::class.java.simpleName) {
            describe("Consume statusendring from Kafka topic") {
                describe("Happy path") {
                    it("should store statusendring when known sykmeldingId") {
                        database.connection.use {
                            it.createOppfolgingstilfelleBit(
                                commit = true,
                                oppfolgingstilfelleBit = oppfolgingstilfelleBitSykmeldingNy,
                            )
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

                        database.isTilfelleBitAvbrutt(tilfelleBitUuid) shouldBeEqualTo true
                    }
                    it("should not store statusendring when known sykmeldingId and sendt") {
                        database.connection.use {
                            it.createOppfolgingstilfelleBit(
                                commit = true,
                                oppfolgingstilfelleBit = oppfolgingstilfelleBitSykmeldingSendt,
                            )
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

                        database.isTilfelleBitAvbrutt(tilfelleBitSykmeldingSendtUuid) shouldBeEqualTo false
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
