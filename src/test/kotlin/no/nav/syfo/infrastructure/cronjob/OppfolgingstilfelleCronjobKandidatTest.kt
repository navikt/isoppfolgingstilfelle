package no.nav.syfo.infrastructure.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.application.OppfolgingstilfellePersonService
import no.nav.syfo.domain.Tag
import no.nav.syfo.infrastructure.kafka.OppfolgingstilfellePersonProducer
import no.nav.syfo.infrastructure.kafka.syketilfelle.KafkaSyketilfellebit
import no.nav.syfo.infrastructure.kafka.syketilfelle.SYKETILFELLEBIT_TOPIC
import no.nav.syfo.infrastructure.kafka.syketilfelle.SyketilfellebitConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import testhelper.countKandidater
import testhelper.dropData
import testhelper.generator.generateKafkaSyketilfellebitRelevantSykmeldingBekreftet
import testhelper.mock.toHistoricalPersonIdentNumber
import java.time.Duration
import java.time.LocalDate

class OppfolgingstilfelleCronjobKandidatTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val tilfellebitRepository = externalMockEnvironment.tilfellebitRepository
    private val kandidatRepository = externalMockEnvironment.kandidatRepository

    private val personIdentDefault = PERSONIDENTNUMBER_DEFAULT.toHistoricalPersonIdentNumber()
    private val topicPartition = TopicPartition(SYKETILFELLEBIT_TOPIC, 0)

    private val mockKafkaConsumer = mockk<KafkaConsumer<String, KafkaSyketilfellebit>>()
    private val oppfolgingstilfellePersonProducer = mockk<OppfolgingstilfellePersonProducer>()

    private val kafkaSyketilfellebitService = SyketilfellebitConsumer(
        oppfolgingstilfelleBitService = OppfolgingstilfelleBitService(tilfellebitRepository),
    )
    private val oppfolgingstilfelleCronjob = OppfolgingstilfelleCronjob(
        oppfolgingstilfellePersonService = OppfolgingstilfellePersonService(
            oppfolgingstilfellePersonRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository,
            oppfolgingstilfellePersonProducer = oppfolgingstilfellePersonProducer,
        ),
        tilfellebitRepository = tilfellebitRepository,
        pdlClient = externalMockEnvironment.pdlClient,
        kandidatRepository = kandidatRepository,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        clearMocks(mockKafkaConsumer)
        every { mockKafkaConsumer.commitSync() } returns Unit
        clearMocks(oppfolgingstilfellePersonProducer)
        justRun { oppfolgingstilfellePersonProducer.sendOppfolgingstilfellePerson(any()) }
    }

    private fun pollAndRun(biter: List<KafkaSyketilfellebit>) {
        val records = biter.mapIndexed { i, bit ->
            ConsumerRecord(SYKETILFELLEBIT_TOPIC, 0, i.toLong(), bit.id, bit)
        }
        every { mockKafkaConsumer.poll(any<Duration>()) } returns ConsumerRecords(
            mapOf(topicPartition to records)
        )
        kafkaSyketilfellebitService.pollAndProcessRecords(consumer = mockKafkaConsumer)
        runBlocking { oppfolgingstilfelleCronjob.runJob() }
    }

    @Test
    fun `stores kandidat when BEKREFTET bit has active tilfelle at least 28 days old`() {
        val bit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit))
        assertEquals(1, database.countKandidater())
    }

    @Test
    fun `stores kandidat with future nextProcessingAt when tilfelle is under 28 days`() {
        val bit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(10),
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit))
        assertEquals(1, database.countKandidater())
    }

    @Test
    fun `does not store duplicate kandidat for same person`() {
        val bit1 = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit1))
        assertEquals(1, database.countKandidater())

        val bit2 = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit2))
        assertEquals(1, database.countKandidater())
    }

    @Test
    fun `does not store kandidat for non-BEKREFTET bit`() {
        val bit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        ).copy(tags = listOf(Tag.SYKMELDING, Tag.SENDT, Tag.PERIODE, Tag.INGEN_AKTIVITET).map { it.name })

        pollAndRun(listOf(bit))
        assertEquals(0, database.countKandidater())
    }

    @Test
    fun `does not store kandidat when tilfelle has ended`() {
        val bit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(40),
            tom = LocalDate.now().minusDays(5),
        )
        pollAndRun(listOf(bit))
        assertEquals(0, database.countKandidater())
    }

    @Test
    fun `does not store kandidat when incoming bit is not at the end of the tilfelle`() {
        // Establish a tilfelle ending today via a non-BEKREFTET bit
        val sendtBit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        ).copy(tags = listOf(Tag.SYKMELDING, Tag.SENDT, Tag.PERIODE, Tag.INGEN_AKTIVITET).map { it.name })
        pollAndRun(listOf(sendtBit))
        assertEquals(0, database.countKandidater())

        // Incoming BEKREFTET bit ending before the tilfelle end → not at end → skip
        val bekreftetBit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now().minusDays(5),
        )
        pollAndRun(listOf(bekreftetBit))
        assertEquals(0, database.countKandidater())
    }

    @Test
    fun `does not store kandidat when person has employer at tilfelle end`() {
        // A SENDT bit with virksomhetsnummer marks the tilfelle as arbeidstaker
        val sendtBitWithEmployer = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        ).copy(
            orgnummer = VIRKSOMHETSNUMMER_DEFAULT.value,
            tags = listOf(Tag.SYKMELDING, Tag.SENDT, Tag.PERIODE, Tag.INGEN_AKTIVITET).map { it.name },
        )
        pollAndRun(listOf(sendtBitWithEmployer))
        assertEquals(0, database.countKandidater())

        // BEKREFTET bit for same period: tilfelle has arbeidstakerAtTilfelleEnd=true → skip
        val bekreftetBit = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bekreftetBit))
        assertEquals(0, database.countKandidater())
    }

    @Test
    fun `does not store new kandidat for same tilfelle when previous kandidat is FERDIG`() {
        val fom = LocalDate.now().minusDays(30)
        val bit1 = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = fom,
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit1))
        assertEquals(1, database.countKandidater())

        database.setKandidatFerdig()

        val bit2 = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = fom,
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit2))
        assertEquals(1, database.countKandidater())
    }

    @Test
    fun `stores new kandidat for new tilfelle when previous kandidat is FERDIG`() {
        val bit1 = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(30),
            tom = LocalDate.now().minusDays(2),
        )
        pollAndRun(listOf(bit1))
        assertEquals(1, database.countKandidater())

        database.setKandidatFerdig()

        val bit2 = generateKafkaSyketilfellebitRelevantSykmeldingBekreftet(
            personIdentNumber = personIdentDefault,
            fom = LocalDate.now().minusDays(1),
            tom = LocalDate.now(),
        )
        pollAndRun(listOf(bit2))
        assertEquals(2, database.countKandidater())
    }
}
