package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.util.and
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.*

val STATUS_ENDRING_CUTOFF = OffsetDateTime.of(
    LocalDate.of(2023, 1, 1),
    LocalTime.MIN,
    ZoneOffset.UTC,
)

class KafkaStatusendringService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
) {
    fun pollAndProcessRecords(
        kafkaConsumerStatusendring: KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>,
    ) {
        val records = kafkaConsumerStatusendring.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumerStatusendring.commitSync()
        }
    }

    private fun processRecords(
        consumerRecords: ConsumerRecords<String, SykmeldingStatusKafkaMessageDTO>,
    ) {
        database.connection.use { connection ->
            COUNT_KAFKA_CONSUMER_STATUSENDRING_READ.increment(consumerRecords.count().toDouble())

            val (tombstoneRecords, relevantRecords) = consumerRecords.partition {
                it.value() == null
            }
            processTombstoneRecords(
                tombstoneRecordList = tombstoneRecords,
            )

            processRelevantRecords(
                connection = connection,
                relevantRecordList = relevantRecords
            )
            connection.commit()
        }
    }

    private fun processTombstoneRecords(
        tombstoneRecordList: List<ConsumerRecord<String, SykmeldingStatusKafkaMessageDTO>>,
    ) {
        COUNT_KAFKA_CONSUMER_STATUSENDRING_TOMBSTONE.increment(tombstoneRecordList.size.toDouble())
    }

    private fun processRelevantRecords(
        connection: Connection,
        relevantRecordList: List<ConsumerRecord<String, SykmeldingStatusKafkaMessageDTO>>,
    ) {
        relevantRecordList.forEach { consumerRecord ->
            val kafkaSykmeldingStatus = consumerRecord.value()
            val inntruffet = kafkaSykmeldingStatus.event.timestamp
            if (inntruffet.isAfter(STATUS_ENDRING_CUTOFF) && kafkaSykmeldingStatus.event.statusEvent == StatusEndring.STATUS_AVBRUTT.value) {
                val oppfolgingstilfelleBitList = connection.getOppfolgingstilfelleBitForRessursId(
                    ressursId = kafkaSykmeldingStatus.event.sykmeldingId,
                )
                oppfolgingstilfelleBitList.forEach { pOppfolgingstilfelleBit ->
                    if (pOppfolgingstilfelleBit.tagList in (Tag.SYKMELDING and Tag.NY)) {
                        connection.createOppfolgingstilfelleBitAvbrutt(
                            pOppfolgingstilfelleBit = pOppfolgingstilfelleBit,
                            inntruffet = inntruffet,
                            avbrutt = true,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaStatusendringService::class.java)
    }
}
