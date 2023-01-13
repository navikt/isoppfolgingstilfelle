package no.nav.syfo.personhendelse

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.*
import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.database.createOppfolgingstilfellePerson
import no.nav.syfo.personhendelse.kafka.KafkaPersonhendelseConsumerService
import no.nav.syfo.personhendelse.kafka.PDL_LEESAH_TOPIC
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.generator.*
import java.time.Duration
import java.time.LocalDate

object KafkaPersonhendelseConsumerServiceSpek : Spek({

    describe(KafkaPersonhendelseConsumerServiceSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val mockKafkaConsumerPersonhendelse = mockk<KafkaConsumer<String, Personhendelse>>()

            val pdlClient = PdlClient(
                azureAdClient = AzureAdClient(
                    azureEnviroment = externalMockEnvironment.environment.azure,
                    redisStore = RedisStore(externalMockEnvironment.environment.redis),
                ),
                clientEnvironment = externalMockEnvironment.environment.clients.pdl,
                redisStore = RedisStore(
                    redisEnvironment = externalMockEnvironment.environment.redis,
                )
            )
            val oppfolgingstilfelleService = OppfolgingstilfelleService(
                database = database,
                pdlClient = pdlClient,
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
                        connection.createOppfolgingstilfellePerson(
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
                        connection.createOppfolgingstilfellePerson(
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
            }
        }
    }
})
