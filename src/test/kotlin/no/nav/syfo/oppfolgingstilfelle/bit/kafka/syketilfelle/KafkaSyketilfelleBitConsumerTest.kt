package no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.client.ArbeidsforholdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.pensjonpen.PensjonPenClient
import no.nav.syfo.infrastructure.cronjob.OppfolgingstilfelleCronjob
import no.nav.syfo.infrastructure.cronjob.SykmeldingNyCronjob
import no.nav.syfo.infrastructure.cronjob.TilfellebitDeleteCronjob
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.infrastructure.kafka.syketilfelle.SYKETILFELLEBIT_TOPIC
import no.nav.syfo.infrastructure.kafka.syketilfelle.SyketilfellebitConsumer
import no.nav.syfo.util.and
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.*
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.generator.*
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class KafkaSyketilfelleBitConsumerTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val tilfellebitRepository = externalMockEnvironment.tilfellebitRepository
    private val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()
    private val oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(tilfellebitRepository)
    private val kafkaSyketilfellebitService = SyketilfellebitConsumer(
        oppfolgingstilfelleBitService = oppfolgingstilfelleBitService,
    )
    private val tilfellebitDeleteCronjob = TilfellebitDeleteCronjob(
        tilfellebitRepository = tilfellebitRepository,
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfelleRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        ),
    )
    private val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()

    private val partition = 0
    private val syketilfellebitTopicPartition = TopicPartition(
        SYKETILFELLEBIT_TOPIC,
        partition,
    )

    private val kafkaSyketilfellebitRelevantVirksomhet = generateKafkaSyketilfellebitRelevantVirksomhet(
        personIdent = personIdentDefault,
    )
    private val kafkaSyketilfellebitRecordRelevantVirksomhet = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        1,
        kafkaSyketilfellebitRelevantVirksomhet.id,
        kafkaSyketilfellebitRelevantVirksomhet,
    )
    private val kafkaSyketilfellebitRecordRelevantVirksomhetDuplicate = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        1,
        kafkaSyketilfellebitRelevantVirksomhet.id,
        kafkaSyketilfellebitRelevantVirksomhet,
    )
    private val kafkaSyketilfellebitNotRelevant1 = generateKafkaSyketilfellebitNotRelevantNoVirksomhet(
        personIdentNumber = personIdentDefault,
    )
    private val kafkaSyketilfellebitRecordNotRelevant1 = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        3,
        kafkaSyketilfellebitNotRelevant1.id,
        kafkaSyketilfellebitNotRelevant1,
    )
    private val kafkaSyketilfellebitSykmeldingNy = generateKafkaSyketilfellebitSykmeldingNy(
        personIdentNumber = personIdentDefault,
    )
    private val kafkaSyketilfellebitRecordSykmeldingNy = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        4,
        kafkaSyketilfellebitSykmeldingNy.id,
        kafkaSyketilfellebitSykmeldingNy,
    )
    private val kafkaSyketilfellebitSykmeldingNyNoOrgNr = generateKafkaSyketilfellebitSykmeldingNy(
        personIdentNumber = ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER,
    )
    private val kafkaSyketilfellebitRecordSykmeldingNyNoOrgNr = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        5,
        kafkaSyketilfellebitSykmeldingNyNoOrgNr.id,
        kafkaSyketilfellebitSykmeldingNyNoOrgNr,
    )
    private val kafkaSyketilfellebitInntektsmelding = generateKafkaSyketilfellebitInntektsmelding(
        personIdentNumber = personIdentDefault,
    )
    private val kafkaSyketilfellebitRecordInntektsmelding = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        6,
        kafkaSyketilfellebitInntektsmelding.id,
        kafkaSyketilfellebitInntektsmelding,
    )

    private val kafkaSyketilfellebitEgenmelding = generateKafkaSyketilfellebitEgenmelding(
        personIdentNumber = personIdentDefault,
    )
    private val kafkaSyketilfellebitRecordEgenmelding = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        7,
        kafkaSyketilfellebitEgenmelding.id,
        kafkaSyketilfellebitEgenmelding,
    )
    private val kafkaSyketilfellebitRecordEgenmeldingTombstone = ConsumerRecord<String, KafkaSyketilfellebit>(
        SYKETILFELLEBIT_TOPIC,
        partition,
        8,
        kafkaSyketilfellebitRecordEgenmelding.key(),
        null,
    )

    private val kafkaSyketilfellebitSykmeldingBekreftet = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
        personIdentNumber = personIdentDefault,
        fom = LocalDate.now().minusDays(16),
        tom = LocalDate.now().minusDays(14),
    )
    private val kafkaSyketilfellebitRecordSykmeldingBekreftet = ConsumerRecord(
        SYKETILFELLEBIT_TOPIC,
        partition,
        9,
        kafkaSyketilfellebitSykmeldingBekreftet.id,
        kafkaSyketilfellebitSykmeldingBekreftet,
    )
    private val kafkaSyketilfellebitRecordSykmeldingBekreftetTombstone = ConsumerRecord<String, KafkaSyketilfellebit>(
        SYKETILFELLEBIT_TOPIC,
        partition,
        10,
        kafkaSyketilfellebitRecordSykmeldingBekreftet.key(),
        null,
    )

    private val mockKafkaConsumerSyketilfelleBit = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()

    private val sykmeldingNyCronJob = SykmeldingNyCronjob(
        database = database,
        arbeidsforholdClient = ArbeidsforholdClient(
            azureAdClient = AzureAdClient(
                azureEnviroment = externalMockEnvironment.environment.azure,
                valkeyStore = externalMockEnvironment.valkeyStore,
                httpClient = externalMockEnvironment.mockHttpClient,
            ),
            clientEnvironment = externalMockEnvironment.environment.clients.arbeidsforhold,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
        pensjonPenClient = PensjonPenClient(
            azureAdClient = AzureAdClient(
                azureEnviroment = externalMockEnvironment.environment.azure,
                valkeyStore = externalMockEnvironment.valkeyStore,
                httpClient = externalMockEnvironment.mockHttpClient,
            ),
            clientEnvironment = externalMockEnvironment.environment.clients.pensjonPen,
            httpClient = externalMockEnvironment.mockHttpClient,
        ),
    )
    private val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfelleRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        ),
        tilfellebitRepository = tilfellebitRepository,
        pdlClient = externalMockEnvironment.pdlClient,
        kandidatRepository = externalMockEnvironment.kandidatRepository,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()

        clearMocks(mockKafkaConsumerSyketilfelleBit)
        every { mockKafkaConsumerSyketilfelleBit.commitSync() } returns Unit

        clearMocks(oppfolgingstilfellePersonProducer)
        justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
    }

    @Test
    fun `should create tilfelleBit and OppfolgingstilfellePerson if SyketilfelleBit is sykmelding ny`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordSykmeldingNy,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = sykmeldingNyCronJob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }

        verify(exactly = 1) {
            mockKafkaConsumerSyketilfelleBit.commitSync()
        }
        verify(exactly = 1) {
            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
        }

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)

        assertEquals(personIdentDefault, oppfolgingstilfellePerson!!.personIdentNumber)
        assertEquals(1, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
        val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
        assertTrue(oppfolgingstilfelle.arbeidstakerAtTilfelleEnd)
        assertEquals(1, oppfolgingstilfelle.virksomhetsnummerList.size)
        assertEquals(UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER, oppfolgingstilfelle.virksomhetsnummerList[0])
        assertEquals(kafkaSyketilfellebitSykmeldingNy.fom, oppfolgingstilfelle.start)
        assertEquals(kafkaSyketilfellebitSykmeldingNy.tom, oppfolgingstilfelle.end)
    }

    @Test
    fun `tilfelleBit should be ignored if avbrutt`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordRelevantVirksomhet,
                    kafkaSyketilfellebitRecordSykmeldingNy,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            sykmeldingNyCronJob.runJob()
            oppfolgingstilfelleCronjob.runJob()
        }
        val pTilfellebit = database.getOppfolgingstilfelleBitForIdent(personIdentDefault).first {
            it.tagList in (Tag.SYKMELDING and Tag.NY)
        }
        tilfellebitRepository.createOppfolgingstilfelleBitAvbrutt(pTilfellebit, OffsetDateTime.now())
        val latestTilfelle =
            tilfellebitRepository.getProcessedOppfolgingstilfelleBitList(personIdentNumber = personIdentDefault, includeAvbrutt = true)
                .first()
        tilfellebitRepository.setProcessedOppfolgingstilfelleBit(latestTilfelle.uuid, processed = false)
        runBlocking {
            oppfolgingstilfelleCronjob.runJob()
        }

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)

        assertEquals(personIdentDefault, oppfolgingstilfellePerson!!.personIdentNumber)
        assertEquals(1, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
        val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
        assertEquals(kafkaSyketilfellebitRelevantVirksomhet.fom, oppfolgingstilfelle.start)
        assertEquals(kafkaSyketilfellebitRelevantVirksomhet.tom, oppfolgingstilfelle.end)
    }

    @Test
    fun `oppfolgingstilfellelist should be empty if the only bit is avbrutt sykmelding-ny`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordSykmeldingNy,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            sykmeldingNyCronJob.runJob()
            oppfolgingstilfelleCronjob.runJob()
        }
        val pTilfellebit = database.getOppfolgingstilfelleBitForIdent(personIdentDefault).first {
            it.tagList in (Tag.SYKMELDING and Tag.NY)
        }
        tilfellebitRepository.createOppfolgingstilfelleBitAvbrutt(
            pOppfolgingstilfelleBit = pTilfellebit,
            inntruffet = OffsetDateTime.now(),
        )
        tilfellebitRepository.setProcessedOppfolgingstilfelleBit(uuid = pTilfellebit.uuid, processed = false)
        runBlocking {
            oppfolgingstilfelleCronjob.runJob()
        }

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)
        assertEquals(personIdentDefault, oppfolgingstilfellePerson!!.personIdentNumber)
        assertEquals(0, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
    }

    @Test
    fun `tombstone records should be deleted from tilfelle_bit and added to tilfelle_bit_deleted`() {
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
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            oppfolgingstilfelleCronjob.runJob()
        }
        assertEquals(0, database.countDeletedTilfelleBit())

        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordEgenmeldingTombstone,
                )
            )
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        // Tombstone only marks the bit for deletion; physical deletion (and the resulting
        // reprocessing trigger) happens in TilfellebitDeleteCronjob.
        assertEquals(0, database.countDeletedTilfelleBit())

        val deleteResult = tilfellebitDeleteCronjob.runJob()
        assertEquals(0, deleteResult.failed)
        assertEquals(1, deleteResult.updated)

        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)

        assertEquals(personIdentDefault, oppfolgingstilfellePerson!!.personIdentNumber)
        assertEquals(1, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
        val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
        // siden egenmeldingsbiten har blitt slettet vil fom for oppfolgingstilfellet fra
        // sykepengesoknadbiten.
        assertEquals(sykepengebitRecord.value().fom, oppfolgingstilfelle.start)
        assertEquals(sykepengebitRecord.value().tom, oppfolgingstilfelle.end)

        assertEquals(1, database.countDeletedTilfelleBit())
    }

    @Test
    fun `tombstone for the only tilfelleBit of a person should result in an empty oppfolgingstilfellePerson`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordEgenmelding,
                )
            )
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        val oppfolgingstilfellePersonBeforeDelete =
            oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePersonBeforeDelete)
        assertEquals(1, oppfolgingstilfellePersonBeforeDelete!!.oppfolgingstilfeller.size)

        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordEgenmeldingTombstone,
                )
            )
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )

        val deleteResult = tilfellebitDeleteCronjob.runJob()
        assertEquals(0, deleteResult.failed)
        assertEquals(1, deleteResult.updated)
        assertEquals(1, database.countDeletedTilfelleBit())

        val oppfolgingstilfellePersonAfterDelete =
            oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePersonAfterDelete)
        assertEquals(personIdentDefault, oppfolgingstilfellePersonAfterDelete!!.personIdentNumber)
        assertEquals(0, oppfolgingstilfellePersonAfterDelete.oppfolgingstilfeller.size)
    }

    @Test
    fun `a newer unprocessed tilfelleBit arriving before an older processed SYKMELDING-BEKREFTET tilfelleBit is deleted should not be lost`() {
        // Older bit (SYKMELDING-BEKREFTET, which is what tombstones are seen for in practice)
        // arrives and is processed first, resulting in a person with a single tilfelle.
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordSykmeldingBekreftet,
                )
            )
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        val oppfolgingstilfellePersonBeforeDelete =
            oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePersonBeforeDelete)
        assertEquals(1, oppfolgingstilfellePersonBeforeDelete!!.oppfolgingstilfeller.size)

        // A tombstone for the older SYKMELDING-BEKREFTET bit arrives, marking it for (later)
        // deletion.
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordSykmeldingBekreftetTombstone,
                )
            )
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )

        // Before TilfellebitDeleteCronjob runs, a newer tilfelleBit for the same person arrives,
        // but has not yet been picked up/processed by OppfolgingstilfelleCronjob.
        val nyereTilfellebitRecord = ConsumerRecord(
            SYKETILFELLEBIT_TOPIC,
            partition,
            11,
            kafkaSyketilfellebitRelevantVirksomhet.id,
            kafkaSyketilfellebitRelevantVirksomhet.copy(
                // inntruffet must be strictly later than the SYKMELDING-BEKREFTET bit being
                // deleted, so that the person snapshot resulting from this bit correctly
                // supersedes the transient empty snapshot from TilfellebitDeleteCronjob.
                inntruffet = kafkaSyketilfellebitSykmeldingBekreftet.inntruffet.plusDays(1),
                fom = LocalDate.now().minusDays(13),
                tom = LocalDate.now(),
            ),
        )
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    nyereTilfellebitRecord,
                )
            )
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )

        // TilfellebitDeleteCronjob deletes the older (tombstoned) bit. At this point the newer
        // bit is still unprocessed, so there is no other processed bit left for the person -
        // this triggers the "no bits remain" branch, which (transiently) creates an empty
        // oppfolgingstilfellePerson snapshot.
        val deleteResult = tilfellebitDeleteCronjob.runJob()
        assertEquals(0, deleteResult.failed)
        assertEquals(1, deleteResult.updated)
        assertEquals(1, database.countDeletedTilfelleBit())

        // OppfolgingstilfelleCronjob later picks up the newer bit (still unprocessed) and
        // recomputes the person - this must supersede the transient empty snapshot, so the
        // newer bit's tilfelle is not lost.
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }

        val oppfolgingstilfellePersonAfterReprocessing =
            oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePersonAfterReprocessing)
        assertEquals(personIdentDefault, oppfolgingstilfellePersonAfterReprocessing!!.personIdentNumber)
        assertEquals(1, oppfolgingstilfellePersonAfterReprocessing.oppfolgingstilfeller.size)
        val oppfolgingstilfelle = oppfolgingstilfellePersonAfterReprocessing.oppfolgingstilfeller[0]
        assertEquals(nyereTilfellebitRecord.value().fom, oppfolgingstilfelle.start)
        assertEquals(nyereTilfellebitRecord.value().tom, oppfolgingstilfelle.end)
    }

    @Test
    fun `should create tilfelleBit and OppfolgingstilfellePerson if bit is inntekstmelding`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordInntektsmelding,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = sykmeldingNyCronJob.runJob()
            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }

        verify(exactly = 1) {
            mockKafkaConsumerSyketilfelleBit.commitSync()
        }
        verify(exactly = 1) {
            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
        }

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNotNull(oppfolgingstilfellePerson)

        assertEquals(kafkaSyketilfellebitInntektsmelding.fnr, oppfolgingstilfellePerson!!.personIdentNumber.value)
        assertEquals(1, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
        val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
        assertTrue(oppfolgingstilfelle.arbeidstakerAtTilfelleEnd)
        assertEquals(1, oppfolgingstilfelle.virksomhetsnummerList.size)
        assertEquals(kafkaSyketilfellebitInntektsmelding.orgnummer, oppfolgingstilfelle.virksomhetsnummerList[0].value)
        assertEquals(kafkaSyketilfellebitInntektsmelding.fom, oppfolgingstilfelle.start)
        assertEquals(kafkaSyketilfellebitInntektsmelding.tom, oppfolgingstilfelle.end)
    }

    @Test
    fun `should create tilfelleBit and OppfolgingstilfellePerson if SyketilfelleBit is sykmelding ny and orgnr missing`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordSykmeldingNyNoOrgNr,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(0, result.updated) // since bit not ready
        }
        runBlocking {
            val result = sykmeldingNyCronJob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated) // since bit is ready
        }

        verify(exactly = 1) {
            mockKafkaConsumerSyketilfelleBit.commitSync()
        }
        verify(exactly = 1) {
            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
        }

        val oppfolgingstilfellePerson =
            oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER)
        assertNotNull(oppfolgingstilfellePerson)
        assertEquals(ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER, oppfolgingstilfellePerson!!.personIdentNumber)
        assertEquals(1, oppfolgingstilfellePerson.oppfolgingstilfeller.size)
        val oppfolgingstilfelle = oppfolgingstilfellePerson.oppfolgingstilfeller[0]
        assertFalse(oppfolgingstilfelle.arbeidstakerAtTilfelleEnd)
        assertEquals(kafkaSyketilfellebitSykmeldingNyNoOrgNr.fom, oppfolgingstilfelle.start)
        assertEquals(kafkaSyketilfellebitSykmeldingNyNoOrgNr.tom, oppfolgingstilfelle.end)
    }

    @Test
    fun `should not create tilfelleBit or OppfolgingstilfellePerson if SyketilfelleBit is not relevant`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordNotRelevant1,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = sykmeldingNyCronJob.runJob()
            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }

        verify(exactly = 1) {
            mockKafkaConsumerSyketilfelleBit.commitSync()
        }
        verify(exactly = 0) {
            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
        }

        val oppfolgingstilfellePerson = oppfolgingstilfelleRepository.getOppfolgingstilfellePerson(personIdentDefault)
        assertNull(oppfolgingstilfellePerson)
    }

    @Test
    fun `should produce exactly 1 Oppfolgingstilfelle for each relevant SyketilfelleBit`() {
        every { mockKafkaConsumerSyketilfelleBit.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(
                syketilfellebitTopicPartition to listOf(
                    kafkaSyketilfellebitRecordRelevantVirksomhet,
                    kafkaSyketilfellebitRecordRelevantVirksomhetDuplicate,
                )
            )
        )

        kafkaSyketilfellebitService.pollAndProcessRecords(
            consumer = mockKafkaConsumerSyketilfelleBit,
        )
        runBlocking {
            val result = sykmeldingNyCronJob.runJob()
            assertEquals(0, result.failed)
            assertEquals(0, result.updated)
        }
        runBlocking {
            val result = oppfolgingstilfelleCronjob.runJob()
            assertEquals(0, result.failed)
            assertEquals(1, result.updated)
        }

        verify(exactly = 1) {
            mockKafkaConsumerSyketilfelleBit.commitSync()
        }
        verify(exactly = 1) {
            oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any())
        }
    }
}
