package no.nav.syfo.oppfolgingstilfelle.bit.kafka.sykmeldingstatus

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.OppfolgingstilfelleBit
import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.infrastructure.database.bit.createOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.KafkaSykmeldingstatusService
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.STATUSENDRING_TOPIC
import no.nav.syfo.infrastructure.kafka.sykmeldingstatus.SykmeldingStatusKafkaMessageDTO
import no.nav.syfo.util.nowUTC
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.dropData
import testhelper.generator.generateKafkaStatusendring
import testhelper.isTilfelleBitAvbrutt
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.util.*

class KafkaSykmeldingstatusConsumerTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
    private val kafkaSykmeldingstatusService = KafkaSykmeldingstatusService(
        database = database,
    )
    private val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()
    private val sykmeldingId = UUID.randomUUID()
    private val tilfelleBitUuid = UUID.randomUUID()

    private val oppfolgingstilfelleBitSykmeldingNy = OppfolgingstilfelleBit(
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
    private val tilfelleBitSykmeldingSendtUuid = UUID.randomUUID()

    private val oppfolgingstilfelleBitSykmeldingSendt = oppfolgingstilfelleBitSykmeldingNy.copy(
        uuid = tilfelleBitSykmeldingSendtUuid,
        tagList = listOf(
            Tag.SYKMELDING,
            Tag.SENDT,
            Tag.PERIODE,
            Tag.INGEN_AKTIVITET,
        )
    )

    private val statusendringTopicPartition = TopicPartition(
        STATUSENDRING_TOPIC,
        0,
    )

    private val statusendring = generateKafkaStatusendring(
        sykmeldingId = sykmeldingId,
        personIdentNumber = personIdentDefault,
    )
    private val kafkaStatusendring = ConsumerRecord(
        STATUSENDRING_TOPIC,
        0,
        1,
        sykmeldingId.toString(),
        statusendring,
    )
    private val unknownSykmeldingId = UUID.randomUUID()
    private val statusendringUnknownSykmelding = generateKafkaStatusendring(
        sykmeldingId = unknownSykmeldingId,
        personIdentNumber = personIdentDefault,
    )
    private val kafkaStatusendringUnknownSykmelding = ConsumerRecord(
        STATUSENDRING_TOPIC,
        0,
        1,
        unknownSykmeldingId.toString(),
        statusendringUnknownSykmelding,
    )
    private val mockKafkaConsumerStatusendring = mockk<KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>>()

    private val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        database = database,
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfelleRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        )
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()

        clearMocks(mockKafkaConsumerStatusendring)
        every { mockKafkaConsumerStatusendring.commitSync() } returns Unit

        clearMocks(oppfolgingstilfellePersonProducer)
        justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
    }

    @Test
    fun `should store statusendring when known sykmeldingId`() {
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

        kafkaSykmeldingstatusService.pollAndProcessRecords(
            kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
        )

        verify(exactly = 1) {
            mockKafkaConsumerStatusendring.commitSync()
        }
        runBlocking {
            oppfolgingstilfelleCronjob.runJob()
        }

        assertTrue(database.isTilfelleBitAvbrutt(tilfelleBitUuid))

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)
        assertEquals(0, oppfolgingstilfellePerson!!.oppfolgingstilfeller.size)
    }

    @Test
    fun `should not store statusendring when known sykmeldingId and sendt`() {
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

        kafkaSykmeldingstatusService.pollAndProcessRecords(
            kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
        )
        runBlocking {
            oppfolgingstilfelleCronjob.runJob()
        }

        verify(exactly = 1) {
            mockKafkaConsumerStatusendring.commitSync()
        }

        assertFalse(database.isTilfelleBitAvbrutt(tilfelleBitSykmeldingSendtUuid))

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)
        assertEquals(1, oppfolgingstilfellePerson!!.oppfolgingstilfeller.size)
    }

    @Test
    fun `should consume statusendring with unknown sykmeldingId`() {
        every { mockKafkaConsumerStatusendring.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                statusendringTopicPartition to listOf(
                    kafkaStatusendringUnknownSykmelding
                )
            )
        )

        kafkaSykmeldingstatusService.pollAndProcessRecords(
            kafkaConsumerStatusendring = mockKafkaConsumerStatusendring,
        )

        verify(exactly = 1) {
            mockKafkaConsumerStatusendring.commitSync()
        }
    }
}
