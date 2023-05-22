package no.nav.syfo.oppfolgingstilfelle.bit.kafka.sykmeldingstatus

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.database.*
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.util.and
import org.apache.kafka.clients.consumer.*
import java.sql.Connection
import java.time.*

val STATUS_ENDRING_CUTOFF = OffsetDateTime.of(
    LocalDate.of(2023, 1, 1),
    LocalTime.MIN,
    ZoneOffset.UTC,
)

class KafkaSykmeldingstatusService(
    val database: DatabaseInterface,
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
            COUNT_KAFKA_CONSUMER_SYKMELDINGSTATUS_READ.increment(consumerRecords.count().toDouble())

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
        COUNT_KAFKA_CONSUMER_SYKMELDINGSTATUS_TOMBSTONE.increment(tombstoneRecordList.size.toDouble())
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
                oppfolgingstilfelleBitList.filter {
                    it.tagList in (Tag.SYKMELDING and Tag.NY)
                }.forEach { pOppfolgingstilfelleBit ->
                    connection.createOppfolgingstilfelleBitAvbrutt(
                        pOppfolgingstilfelleBit = pOppfolgingstilfelleBit,
                        inntruffet = inntruffet,
                        avbrutt = true,
                    )
                    val latestProcessedTilfelleBit =
                        connection.getProcessedOppfolgingstilfelleBitList(pOppfolgingstilfelleBit.personIdentNumber)
                            .firstOrNull()
                    // Set the newest tilfelleBit to unprocessed so that oppfolgingstilfelle is updated by cronjob
                    connection.setProcessedOppfolgingstilfelleBit(
                        uuid = latestProcessedTilfelleBit?.uuid ?: pOppfolgingstilfelleBit.uuid,
                        processed = false,
                    )
                }
            }
        }
    }
}
