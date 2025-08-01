package no.nav.syfo.oppfolgingstilfelle.bit.kafka.syketilfelle

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBitService
import no.nav.syfo.oppfolgingstilfelle.bit.domain.toOppfolgingstilfelleBit
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.UUID

class KafkaSyketilfellebitService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
) {
    fun pollAndProcessRecords(
        kafkaConsumerSyketilfelleBit: KafkaConsumer<String, KafkaSyketilfellebit>,
    ) {
        val records = kafkaConsumerSyketilfelleBit.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processRecords(
                consumerRecords = records,
            )
            kafkaConsumerSyketilfelleBit.commitSync()
        }
    }

    private fun processRecords(
        consumerRecords: ConsumerRecords<String, KafkaSyketilfellebit>,
    ) {
        database.connection.use { connection ->
            COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_READ.increment(consumerRecords.count().toDouble())

            val (tombstoneRecordList, recordsValid) = consumerRecords.partition {
                it.value() == null
            }
            processTombstoneRecordList(
                tombstoneRecordList = tombstoneRecordList,
            )

            val (relevantRecordList, notRelevantRecordList) = recordsValid.partition {
                it.value().isRelevantForOppfolgingstilfelle()
            }

            processRelevantRecordList(
                connection = connection,
                relevantRecordList = relevantRecordList
            )
            processNotRelevantRecordList(
                notRelevantRecordList = notRelevantRecordList,
            )
            connection.commit()
        }
    }

    private fun processTombstoneRecordList(
        tombstoneRecordList: List<ConsumerRecord<String, KafkaSyketilfellebit>>,
    ) {
        val idList = try {
            tombstoneRecordList.map { UUID.fromString(it.key()) }
        } catch (exc: IllegalArgumentException) {
            log.warn("Received tombstone record(s) with invalid key(s), not valid uuids")
            emptyList()
        }
        database.connection.use { connection ->
            oppfolgingstilfelleBitService.deleteOppfolgingstilfelleBitList(
                connection = connection,
                oppfolgingstilfelleBitIdList = idList,
            )
            connection.commit()
        }
        COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_TOMBSTONE.increment(tombstoneRecordList.size.toDouble())
    }

    private fun processRelevantRecordList(
        connection: Connection,
        relevantRecordList: List<ConsumerRecord<String, KafkaSyketilfellebit>>,
    ) {
        val relevantOppfolgingstilfelleBitList = relevantRecordList.map {
            it.value().toOppfolgingstilfelleBit()
        }
        oppfolgingstilfelleBitService.createOppfolgingstilfelleBitList(
            connection = connection,
            oppfolgingstilfelleBitList = relevantOppfolgingstilfelleBitList,
        )
    }

    private fun processNotRelevantRecordList(
        notRelevantRecordList: List<ConsumerRecord<String, KafkaSyketilfellebit>>,
    ) {
        if (notRelevantRecordList.isNotEmpty()) {
            COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_NOT_RELEVANT.increment(notRelevantRecordList.size.toDouble())
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaSyketilfellebitService::class.java)
    }
}
