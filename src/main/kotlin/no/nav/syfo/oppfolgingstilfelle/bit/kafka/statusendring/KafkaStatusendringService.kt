package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.database.*
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

            val (tombstoneRecordList, relevantRecordList) = consumerRecords.partition {
                it.value() == null
            }
            processTombstoneRecordList(
                tombstoneRecordList = tombstoneRecordList,
            )

            processRelevantRecordList(
                connection = connection,
                relevantRecordList = relevantRecordList
            )
            connection.commit()
        }
    }

    private fun processTombstoneRecordList(
        tombstoneRecordList: List<ConsumerRecord<String, SykmeldingStatusKafkaMessageDTO>>,
    ) {
        COUNT_KAFKA_CONSUMER_STATUSENDRING_TOMBSTONE.increment(tombstoneRecordList.size.toDouble())
    }

    private fun processRelevantRecordList(
        connection: Connection,
        relevantRecordList: List<ConsumerRecord<String, SykmeldingStatusKafkaMessageDTO>>,
    ) {
        relevantRecordList.forEach { consumerRecord ->
            val kafkaSykmeldingStatus = consumerRecord.value()
            val inntruffet = kafkaSykmeldingStatus.event.timestamp
            if (inntruffet.isAfter(STATUS_ENDRING_CUTOFF) && kafkaSykmeldingStatus.event.statusEvent == STATUS_AVBRUTT) {
                val oppfolgingstilfelleBitList = connection.getOppfolgingstilfelleBitForRessursId(
                    ressursId = kafkaSykmeldingStatus.event.sykmeldingId,
                )
                oppfolgingstilfelleBitList.forEach { pOppfolgingstilfelleBit ->
                    connection.createOppfolgingstilfelleBitAvbrutt(
                        pOppfolgingstilfelleBit = pOppfolgingstilfelleBit,
                        inntruffet = inntruffet,
                        avbrutt = true,
                    )
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaStatusendringService::class.java)
    }
}
