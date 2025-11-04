package no.nav.syfo.personhendelse

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.application.PersonhendelseService
import no.nav.syfo.infrastructure.kafka.personhendelse.KafkaPersonhendelseConsumerService
import no.nav.syfo.infrastructure.kafka.personhendelse.PDL_LEESAH_TOPIC
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.generator.generateKafkaPersonhendelseAnnulleringDTO
import testhelper.generator.generateKafkaPersonhendelseDTO
import testhelper.generator.generateOppfolgingstilfellePerson
import java.time.Duration
import java.time.LocalDate

class KafkaPersonhendelseConsumerServiceTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val mockKafkaConsumerPersonhendelse = mockk<KafkaConsumer<String, Personhendelse>>()
    private val oppfolgingstilfelleService = OppfolgingstilfelleService(
        oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
    )
    private val personhendelseService = PersonhendelseService(
        database = database,
        oppfolgingstilfelleService = oppfolgingstilfelleService,
        oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository,
    )
    private val kafkaPersonhendelseConsumerService = KafkaPersonhendelseConsumerService(
        personhendelseService = personhendelseService
    )

    private val partition = 0
    private val personhendelseTopicPartition = TopicPartition(
        PDL_LEESAH_TOPIC,
        partition,
    )
    private val personIdent = UserConstants.ARBEIDSTAKER_FNR
    private val kafkaPersonHendelse = generateKafkaPersonhendelseDTO(
        personident = personIdent,
    )
    private val kafkaPersonhendelseRecord = ConsumerRecord(
        PDL_LEESAH_TOPIC,
        partition,
        1,
        "key1",
        kafkaPersonHendelse,
    )
    private val kafkaPersonHendelseAnnullering = generateKafkaPersonhendelseAnnulleringDTO(
        personident = personIdent,
        annullertHendelseId = kafkaPersonHendelse.hendelseId,
    )
    private val kafkaPersonHendelseAnnulleringRecord = ConsumerRecord(
        PDL_LEESAH_TOPIC,
        partition,
        2,
        "key1",
        kafkaPersonHendelseAnnullering,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        clearMocks(mockKafkaConsumerPersonhendelse)
        every { mockKafkaConsumerPersonhendelse.commitSync() } returns Unit
    }

    @Test
    fun `Skal lagre doedsdato hvis identen er kjent`() {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
            personIdent = personIdent,
        )

        database.connection.use { connection ->
            oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(
                connection = connection,
                commit = true,
                oppfolgingstilfellePerson = oppfolgingstilfellePerson,
            )
        }
        every { mockKafkaConsumerPersonhendelse.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                personhendelseTopicPartition to listOf(
                    kafkaPersonhendelseRecord,
                )
            )
        )
        assertNull(oppfolgingstilfellePersonRepository.getDodsdato(personIdent))
        runBlocking {
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
        }
        verify(exactly = 1) {
            mockKafkaConsumerPersonhendelse.commitSync()
        }

        assertEquals(LocalDate.now(), oppfolgingstilfellePersonRepository.getDodsdato(personIdent))
    }

    @Test
    fun `Skal takle duplikat record`() {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
            personIdent = personIdent,
        )

        database.connection.use { connection ->
            oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(
                connection = connection,
                commit = true,
                oppfolgingstilfellePerson = oppfolgingstilfellePerson,
            )
        }
        every { mockKafkaConsumerPersonhendelse.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                personhendelseTopicPartition to listOf(
                    kafkaPersonhendelseRecord,
                )
            )
        )
        runBlocking {
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
        }
        verify(exactly = 2) {
            mockKafkaConsumerPersonhendelse.commitSync()
        }

        assertEquals(LocalDate.now(), oppfolgingstilfellePersonRepository.getDodsdato(personIdent))
    }

    @Test
    fun `Skal ikke lagre dato for ukjent personident`() {
        every { mockKafkaConsumerPersonhendelse.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                personhendelseTopicPartition to listOf(
                    kafkaPersonhendelseRecord,
                )
            )
        )
        runBlocking {
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
        }

        verify(exactly = 1) {
            mockKafkaConsumerPersonhendelse.commitSync()
        }

        assertNull(oppfolgingstilfellePersonRepository.getDodsdato(personIdent))
    }

    @Test
    fun `Skal slette personforekomst ved annullering`() {
        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
            personIdent = personIdent,
        )

        database.connection.use { connection ->
            oppfolgingstilfellePersonRepository.createOppfolgingstilfellePerson(
                connection = connection,
                commit = true,
                oppfolgingstilfellePerson = oppfolgingstilfellePerson,
            )
        }
        every { mockKafkaConsumerPersonhendelse.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                personhendelseTopicPartition to listOf(
                    kafkaPersonhendelseRecord,
                )
            )
        )
        runBlocking {
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
        }
        verify(exactly = 1) {
            mockKafkaConsumerPersonhendelse.commitSync()
        }

        assertEquals(LocalDate.now(), oppfolgingstilfellePersonRepository.getDodsdato(personIdent))

        every { mockKafkaConsumerPersonhendelse.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                personhendelseTopicPartition to listOf(
                    kafkaPersonHendelseAnnulleringRecord,
                )
            )
        )
        runBlocking {
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
        }
        verify(exactly = 2) {
            mockKafkaConsumerPersonhendelse.commitSync()
        }
        assertNull(oppfolgingstilfellePersonRepository.getDodsdato(personIdent))
    }
}
