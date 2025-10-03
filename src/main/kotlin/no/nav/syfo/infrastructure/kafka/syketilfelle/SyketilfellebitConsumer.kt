package no.nav.syfo.infrastructure.kafka.syketilfelle

import no.nav.syfo.application.OppfolgingstilfelleBitService
import no.nav.syfo.domain.toOppfolgingstilfelleBit
import no.nav.syfo.infrastructure.database.DatabaseInterface
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.time.Duration
import java.util.*

class SyketilfellebitConsumer(
    val database: DatabaseInterface,
    val oppfolgingstilfelleBitService: OppfolgingstilfelleBitService,
) {
    fun pollAndProcessRecords(
        consumer: KafkaConsumer<String, KafkaSyketilfellebit>,
    ) {
        val records = consumer.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processRecords(
                records = records,
            )
            consumer.commitSync()
        }
    }

    private fun processRecords(
        records: ConsumerRecords<String, KafkaSyketilfellebit>,
    ) {
        database.connection.use { connection ->
            COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_READ.increment(records.count().toDouble())

            val (tombstoneRecordList, recordsValid) = records.partition {
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
        private val log = LoggerFactory.getLogger(SyketilfellebitConsumer::class.java)
    }
}
