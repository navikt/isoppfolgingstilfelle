package no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.client.ArbeidsforholdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.infrastructure.cronjob.SykmeldingNyCronjob
import no.nav.syfo.infrastructure.database.bit.createOppfolgingstilfelleBitAvbrutt
import no.nav.syfo.infrastructure.database.bit.getOppfolgingstilfelleBitForIdent
import no.nav.syfo.infrastructure.database.bit.getProcessedOppfolgingstilfelleBitList
import no.nav.syfo.infrastructure.database.bit.setProcessedOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebitService
import no.nav.syfo.infrastructure.kafka.syketilfelle.SYKETILFELLEBIT_TOPIC
import no.nav.syfo.util.and
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.countDeletedTilfelleBit
import testhelper.dropData
import testhelper.generator.*
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class KafkaSyketilfelleBitConsumerSpek : Spek({
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
        kafkaSyketilfellebitRelevantVirksomhet.id,
        kafkaSyketilfellebitRelevantVirksomhet,
    )
    val kafkaSyketilfellebitRecordRelevantVirksomhetDuplicate = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        1,
        kafkaSyketilfellebitRelevantVirksomhet.id,
        kafkaSyketilfellebitRelevantVirksomhet,
    )
    val kafkaSyketilfellebitNotRelevant1 = generateKafkaSyketilfellebitNotRelevantNoVirksomhet(
        personIdentNumber = personIdentDefault,
    )
    val kafkaSyketilfellebitRecordNotRelevant1 = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        3,
        kafkaSyketilfellebitNotRelevant1.id,
        kafkaSyketilfellebitNotRelevant1,
    )
    val kafkaSyketilfellebitSykmeldingNy = generateKafkaSyketilfellebitSykmeldingNy(
        personIdentNumber = personIdentDefault,
    )
    val kafkaSyketilfellebitRecordSykmeldingNy = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        4,
        kafkaSyketilfellebitSykmeldingNy.id,
        kafkaSyketilfellebitSykmeldingNy,
    )
    val kafkaSyketilfellebitSykmeldingNyNoOrgNr = generateKafkaSyketilfellebitSykmeldingNy(
        personIdentNumber = ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER,
    )
    val kafkaSyketilfellebitRecordSykmeldingNyNoOrgNr = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        5,
        kafkaSyketilfellebitSykmeldingNyNoOrgNr.id,
        kafkaSyketilfellebitSykmeldingNyNoOrgNr,
    )
    val kafkaSyketilfellebitInntektsmelding = generateKafkaSyketilfellebitInntektsmelding(
        personIdentNumber = personIdentDefault,
    )
    val kafkaSyketilfellebitRecordInntektsmelding = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        6,
        kafkaSyketilfellebitInntektsmelding.id,
        kafkaSyketilfellebitInntektsmelding,
    )

    val kafkaSyketilfellebitEgenmelding = generateKafkaSyketilfellebitEgenmelding(
        personIdentNumber = personIdentDefault,
    )
    val kafkaSyketilfellebitRecordEgenmelding = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        7,
        kafkaSyketilfellebitEgenmelding.id,
        kafkaSyketilfellebitEgenmelding,
    )
    val kafkaSyketilfellebitRecordEgenmeldingTombstone = ConsumerRecord<String, KafkaSyketilfellebit>(
        SYKETILFELLEBIT_TOPIC,
        partition,
        8,
        kafkaSyketilfellebitRecordEgenmelding.key(),
        null,
    )

    val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

    val sykmeldingNyCronJob = SykmeldingNyCronjob(
        database = database,
        arbeidsforholdClient = ArbeidsforholdClient(
            azureAdClient = AzureAdClient(
                azureEnviroment = externalMockEnvironment.environment.azure,
                valkeyStore = externalMockEnvironment.valkeyStore,
                httpClient = externalMockEnvironment.mockHttpClient,
            ),
            clientEnvironment = externalMockEnvironment.environment.clients.arbeidsforhold,
            httpClient = externalMockEnvironment.mockHttpClient,
        )
    )
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

    describe(KafkaSyketilfelleBitConsumerSpek::class.java.simpleName) {
        describe("Consume syketilfellebiter from Kafka topic") {
            describe("Happy path") {
                it("should create tilfelleBit and OppfolgingstilfellePerson if SyketilfelleBit is sykmelding ny") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordSykmeldingNy,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        val result = sykmeldingNyCronJob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    verify(exactly = 1) {
                        mockKafkaConsumerSyketilfelleBit.commitSync()
                    }
                    verify(exactly = 1) {
                        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                    }

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()

                    oppfolgingstilfellePerson.personIdentNumber shouldBeEqualTo personIdentDefault
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 1
                    val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
                    oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                    oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
                    oppfolgingstilfelle.virksomhetsnummerList[0] shouldBeEqualTo UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
                    oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitSykmeldingNy.fom
                    oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitSykmeldingNy.tom
                }
                it("tilfelleBit should be ignored if avbrutt") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordRelevantVirksomhet,
                                kafkaSyketilfellebitRecordSykmeldingNy,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        sykmeldingNyCronJob.runJob()
                        oppfolgingstilfelleCronjob.runJob()
                    }
                    val pTilfellebit = database.getOppfolgingstilfelleBitForIdent(personIdentDefault).filter {
                        it.tagList in (Tag.SYKMELDING and Tag.NY)
                    }.first()
                    database.connection.use {
                        it.createOppfolgingstilfelleBitAvbrutt(
                            commit = true,
                            pOppfolgingstilfelleBit = pTilfellebit,
                            inntruffet = OffsetDateTime.now(),
                            avbrutt = true,
                        )
                        val latestTilfelle = it.getProcessedOppfolgingstilfelleBitList(
                            personIdentNumber = personIdentDefault,
                            includeAvbrutt = true,
                        ).first()
                        it.setProcessedOppfolgingstilfelleBit(
                            uuid = latestTilfelle.uuid,
                            processed = false,
                        )
                        it.commit()
                    }
                    runBlocking {
                        oppfolgingstilfelleCronjob.runJob()
                    }

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()

                    oppfolgingstilfellePerson.personIdentNumber shouldBeEqualTo personIdentDefault
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 1
                    val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
                    oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.fom
                    oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitRelevantVirksomhet.tom
                }
                it("oppfolgingstilfellelist should be empty if the only bit is avbrutt sykmelding-ny") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordSykmeldingNy,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        sykmeldingNyCronJob.runJob()
                        oppfolgingstilfelleCronjob.runJob()
                    }
                    val pTilfellebit = database.getOppfolgingstilfelleBitForIdent(personIdentDefault).filter {
                        it.tagList in (Tag.SYKMELDING and Tag.NY)
                    }.first()
                    database.connection.use {
                        it.createOppfolgingstilfelleBitAvbrutt(
                            pOppfolgingstilfelleBit = pTilfellebit,
                            inntruffet = OffsetDateTime.now(),
                            avbrutt = true,
                        )
                        it.setProcessedOppfolgingstilfelleBit(
                            uuid = pTilfellebit.uuid,
                            processed = false,
                        )
                        it.commit()
                    }
                    runBlocking {
                        oppfolgingstilfelleCronjob.runJob()
                    }

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()
                    oppfolgingstilfellePerson.personIdentNumber shouldBeEqualTo personIdentDefault
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 0
                }

                it("tombstone records should be deleted from tilfelle_bit and added to tilfelle_bit_deleted") {
                    val sykepengebitRecord = ConsumerRecord(
                        SYKETILFELLEBIT_TOPIC,
                        partition,
                        8,
                        kafkaSyketilfellebitRelevantVirksomhet.id,
                        kafkaSyketilfellebitRelevantVirksomhet.copy(
                            fom = LocalDate.now().minusDays(13),
                            tom = LocalDate.now(),
                        ),
                    )
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordEgenmelding,
                                sykepengebitRecord,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        oppfolgingstilfelleCronjob.runJob()
                    }
                    database.countDeletedTilfelleBit() shouldBeEqualTo 0

                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordEgenmeldingTombstone,
                            )
                        )
                    )
                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()

                    oppfolgingstilfellePerson.personIdentNumber shouldBeEqualTo personIdentDefault
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 1
                    val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
                    // siden egenmeldingsbiten har blitt slettet vil fom for oppfolgingstilfellet fra
                    // sykepengesoknadbiten.
                    oppfolgingstilfelle.start shouldBeEqualTo sykepengebitRecord.value().fom
                    oppfolgingstilfelle.end shouldBeEqualTo sykepengebitRecord.value().tom

                    database.countDeletedTilfelleBit() shouldBeEqualTo 1
                }

                it("should create tilfelleBit and OppfolgingstilfellePerson if bit is inntekstmelding") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordInntektsmelding,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        val result = sykmeldingNyCronJob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    verify(exactly = 1) {
                        mockKafkaConsumerSyketilfelleBit.commitSync()
                    }
                    verify(exactly = 1) {
                        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                    }

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldNotBeNull()

                    oppfolgingstilfellePerson.personIdentNumber.value shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.fnr
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 1
                    val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
                    oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo true
                    oppfolgingstilfelle.virksomhetsnummerList.size shouldBeEqualTo 1
                    oppfolgingstilfelle.virksomhetsnummerList[0].value shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.orgnummer
                    oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.fom
                    oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitInntektsmelding.tom
                }

                it("should create tilfelleBit and OppfolgingstilfellePerson if SyketilfelleBit is sykmelding ny and orgnr missing") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordSykmeldingNyNoOrgNr,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0 // since bit not ready
                    }
                    runBlocking {
                        val result = sykmeldingNyCronJob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1 // since bit is ready
                    }

                    verify(exactly = 1) {
                        mockKafkaConsumerSyketilfelleBit.commitSync()
                    }
                    verify(exactly = 1) {
                        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                    }

                    val oppfolgingstilfellePerson =
                        oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
                    oppfolgingstilfellePerson.shouldNotBeNull()
                    oppfolgingstilfellePerson.personIdentNumber shouldBeEqualTo ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
                    oppfolgingstilfellePerson.oppfolgingstilfeller.size shouldBeEqualTo 1
                    val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
                    oppfolgingstilfelle.arbeidstakerAtTilfelleEnd shouldBeEqualTo false
                    oppfolgingstilfelle.start shouldBeEqualTo kafkaSyketilfellebitSykmeldingNyNoOrgNr.fom
                    oppfolgingstilfelle.end shouldBeEqualTo kafkaSyketilfellebitSykmeldingNyNoOrgNr.tom
                }

                it("should not create tilfelleBit or OppfolgingstilfellePerson if SyketilfelleBit is not relevant") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordNotRelevant1,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        val result = sykmeldingNyCronJob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }

                    verify(exactly = 1) {
                        mockKafkaConsumerSyketilfelleBit.commitSync()
                    }
                    verify(exactly = 0) {
                        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                    }

                    val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
                    oppfolgingstilfellePerson.shouldBeNull()
                }

                it("should produce exactly 1 Oppfolgingstilfelle for each relevant SyketilfelleBit") {
                    every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
                        mapOf(
                            syketilfellebitTopicPartition to listOf(
                                kafkaSyketilfellebitRecordRelevantVirksomhet,
                                kafkaSyketilfellebitRecordRelevantVirksomhetDuplicate,
                            )
                        )
                    )

                    kafkaSyketilfellebitService.pollAndProcessRecords(
                        kafkaConsumerSyketilfelleBit = mockKafkaConsumerSyketilfelleBit,
                    )
                    runBlocking {
                        val result = sykmeldingNyCronJob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 0
                    }
                    runBlocking {
                        val result = oppfolgingstilfelleCronjob.runJob()
                        result.failed shouldBeEqualTo 0
                        result.updated shouldBeEqualTo 1
                    }

                    verify(exactly = 1) {
                        mockKafkaConsumerSyketilfelleBit.commitSync()
                    }
                    verify(exactly = 1) {
                        oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
                    }
                }
            }
        }
    }
})
