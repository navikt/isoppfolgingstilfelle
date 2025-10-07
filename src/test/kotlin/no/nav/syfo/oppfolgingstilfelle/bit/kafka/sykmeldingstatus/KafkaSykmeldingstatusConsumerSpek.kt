package no.nav.syfo.oppfolgingstilfelle.bit.kafka.sykmeldingstatus

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.STATUSENDRING_TOPIC
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.SykmeldingstatusConsumer
import no.nav.syfo.util.nowUTC
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.dropData
import testhelper.generator.generateKafkaStatusendring
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.util.*

class KafkaSykmeldingstatusConsumerSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    val tilfelleBitRepository = externalMockEnvironment.tilfellebitRepository
    val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
    val sykmeldingstatusConsumer = SykmeldingstatusConsumer(tilfellebitRepository = externalMockEnvironment.tilfellebitRepository)
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
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfelleRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        ),
        tilfellebitRepository = externalMockEnvironment.tilfellebitRepository,
    )

    beforeEachTest {
        database.dropData()

        clearMocks(mockKafkaConsumerStatusendring)
        every { mockKafkaConsumerStatusendring.commitSync() } returns Unit

        clearMocks(oppfolgingstilfellePersonProducer)
        justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
    }

    describe(KafkaSykmeldingstatusConsumerSpek::class.java.simpleName) {
        describe("Consume statusendring from Kafka topic") {
            describe("Happy path") {
                it("should store statusendring when known sykmeldingId") {
                    tilfelleBitRepository.createOppfolgingstilfelleBit(
                        oppfolgingstilfelleBit = oppfolgingstilfelleBitSykmeldingNy,
                    )
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

                    sykmeldingstatusConsumer.pollAndProcessRecords(
                        kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerStatusendring.commitSync()
                    }
                    runBlocking {
                        oppfolgingstilfelleCronjob.runJob()
                    }

                    tilfelleBitRepository.isTilfelleBitAvbrutt(tilfelleBitUuid) shouldBeEqualTo true

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 0
                }
                it("should not store statusendring when known sykmeldingId and sendt") {
                    tilfelleBitRepository.createOppfolgingstilfelleBit(
                        oppfolgingstilfelleBit = oppfolgingstilfelleBitSykmeldingSendt,
                    )
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

                    sykmeldingstatusConsumer.pollAndProcessRecords(
                        kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
                    )
                    runBlocking {
                        oppfolgingstilfelleCronjob.runJob()
                    }

                    verify(exactly = 1) {
                        mockKafkaConsumerStatusendring.commitSync()
                    }

                    tilfelleBitRepository.isTilfelleBitAvbrutt(tilfelleBitSykmeldingSendtUuid) shouldBeEqualTo false

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 1
                }
                it("should consume statusendring with unknown sykmeldingId") {
                    every { mockKafkaConsumerStatusendring.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            statusendringTopicPartition to listOf(
                                kafkaStatusendringUnknownSykmelding
                            )
                        )
                    )

                    sykmeldingstatusConsumer.pollAndProcessRecords(
                        kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
                    )

                    verify(exactly = 1) {
                        mockKafkaConsumerStatusendring.commitSync()
                    }
                }
            }
        }
    }
})
