package no.nav.syfo.personhendelse

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.personhendelse.kafka.KafkaPersonhendelseConsumerService
import no.nav.syfo.personhendelse.kafka.PDL_LEESAH_TOPIC
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.dropData
import testhelper.generator.generateKafkaPersonhendelseAnnulleringDTO
import testhelper.generator.generateKafkaPersonhendelseDTO
import testhelper.generator.generateOppfolgingstilfellePerson
import testhelper.getDodsdato
import java.time.Duration
import java.time.LocalDate

object KafkaPersonhendelseConsumerServiceSpek : Spek({

    describe(KafkaPersonhendelseConsumerServiceSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfelleRepository
        val mockKafkaConsumerPersonhendelse = mockk<KafkaConsumer<String, Personhendelse>>()
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = database,
            oppfolgingstilfelleRepository = oppfolgingstilfelleRepository,
        )
        val personhendelseService = PersonhendelseService(
            database = database,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
        )
        val kafkaPersonhendelseConsumerService = KafkaPersonhendelseConsumerService(
            personhendelseService = personhendelseService
        )

        val partition = 0
        val personhendelseTopicPartition = TopicPartition(
            PDL_LEESAH_TOPIC,
            partition,
        )
        val personIdent = UserConstants.ARBEIDSTAKER_FNR
        val kafkaPersonHendelse = generateKafkaPersonhendelseDTO(
            personident = personIdent,
        )
        val kafkaPersonhendelseRecord = ConsumerRecord(
            PDL_LEESAH_TOPIC,
            partition,
            1,
            "key1",
            kafkaPersonHendelse,
        )
        val kafkaPersonHendelseAnnullering = generateKafkaPersonhendelseAnnulleringDTO(
            personident = personIdent,
            annullertHendelseId = kafkaPersonHendelse.hendelseId,
        )
        val kafkaPersonHendelseAnnulleringRecord = ConsumerRecord(
            PDL_LEESAH_TOPIC,
            partition,
            2,
            "key1",
            kafkaPersonHendelseAnnullering,
        )

        beforeEachTest {
            database.dropData()
            clearMocks(mockKafkaConsumerPersonhendelse)
            every { mockKafkaConsumerPersonhendelse.commitSync() } returns Unit
        }

        describe("Happy path") {
            it("Skal lagre doedsdato hvis identen er kjent") {

                val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
                    personIdent = personIdent,
                )

                database.connection.use { connection ->
                    oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
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
                database.getDodsdato(personIdent) shouldBe null
                runBlocking {
                    kafkaPersonhendelseConsumerService.pollAndProcessRecords(mockKafkaConsumerPersonhendelse)
                }
                verify(exactly = 1) {
                    mockKafkaConsumerPersonhendelse.commitSync()
                }

                database.getDodsdato(personIdent) shouldBeEqualTo LocalDate.now()
            }
            it("Skal takle duplikat record") {

                val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
                    personIdent = personIdent,
                )

                database.connection.use { connection ->
                    oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
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

                database.getDodsdato(personIdent) shouldBeEqualTo LocalDate.now()
            }
            it("Skal ikke lagre dato for ukjent personident ") {
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

                database.getDodsdato(personIdent) shouldBe null
            }
            it("Skal slette personforekomst ved annullering") {

                val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
                    personIdent = personIdent,
                )

                database.connection.use { connection ->
                    oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
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

                database.getDodsdato(personIdent) shouldBeEqualTo LocalDate.now()

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
                database.getDodsdato(personIdent) shouldBe null
            }
        }
    }
})
