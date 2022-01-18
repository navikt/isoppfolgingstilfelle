package no.nav.syfo.oppfolgingstilfelle.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.bit.*
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.*
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.consumer.*
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import testhelper.generator.generateKafkaSyketilfellebit
import java.time.*
import java.util.*

class OppfolgingstilfelleApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(
            database = database,
        )
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = database,
            oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
        )

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val kafkaSyketilfellebitService = KafkaSyketilfellebitService(
            database = database,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
        )

        val oppfolgingstilfelleBit = OppfolgingstilfelleBit(
            uuid = UUID.randomUUID(),
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            virksomhetsnummer = VIRKSOMHETSNUMMER_DEFAULT.value,
            createdAt = OffsetDateTime.now(),
            inntruffet = OffsetDateTime.now().minusDays(1),
            fom = LocalDate.now().minusDays(1),
            tom = LocalDate.now().plusDays(1),
            tagList = listOf(
                Tag.SYKEPENGESOKNAD,
                Tag.SENDT,
            ),
            ressursId = UUID.randomUUID().toString(),
        )

        val partition = 0
        val syketilfellebitTopicPartition = TopicPartition(
            SYKETILFELLEBIT_TOPIC,
            partition,
        )

        val kafkaSyketilfellebit = generateKafkaSyketilfellebit(
            arbeidstakerPersonIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
        )
        val kafkaSyketilfellebitRecord = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            1,
            "key1",
            kafkaSyketilfellebit,
        )
        val kafkaSyketilfellebitRecordDuplicate = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            1,
            "key1",
            kafkaSyketilfellebit,
        )

        val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecord,
                    kafkaSyketilfellebitRecordDuplicate,
                )
            )
        )
        every { mockKafkaConsumerSyketilfelleBit.commitSync() } returns Unit

        describe(OppfolgingstilfelleApiSpek::class.java.simpleName) {
            describe("Get OppfolgingstilfellePersonDTO for PersonIdent") {
                val url = "$oppfolgingstilfelleApiV1Path$oppfolgingstilfelleApiPersonIdentPath"
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azureAppClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {

                    it("should return list of OppfolgingstilfelleDTO if request is successful") {
                        kafkaSyketilfellebitService.pollAndProcessRecords(
                            kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                        )

                        verify(exactly = 1) {
                            mockKafkaConsumerSyketilfelleBit.commitSync()
                        }

                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo oppfolgingstilfelleBit.personIdentNumber.value

                            val oppfolgingstilfelleDTO = oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                            oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo oppfolgingstilfelleBit.virksomhetsnummer

                            oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfelleBit.fom
                            oppfolgingstilfelleDTO.end shouldBeEqualTo oppfolgingstilfelleBit.tom
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
