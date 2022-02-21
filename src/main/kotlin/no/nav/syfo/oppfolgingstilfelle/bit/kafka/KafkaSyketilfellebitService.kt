package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.bit.toOppfolgingstilfelleBit
import org.apache.kafka.clients.consumer.*
import org.slf4j.LoggerFactory
import java.time.Duration

class KafkaSyketilfellebitService(
    val database: DatabaseInterface,
    val oppfolgingstilfelleService: OppfolgingstilfelleService,
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
        COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_READ.increment()

        val (tombstoneRecordList, recordsValid) = consumerRecords.partition {
            it.value() == null
        }
        processTombstoneRecordList(
            tombstoneRecordList = tombstoneRecordList,
        )

        val (relevantRecordList, notRelevantRecordList) = recordsValid.partition {
            it.value().isRelevantForOppfolgingstilfelle()
        }

        processNotRelevantRecordList(
            notRelevantRecordList = notRelevantRecordList,
        )

        processRelevantRecordList(
            relevantRecordList = relevantRecordList
        )
    }

    private fun processTombstoneRecordList(
        tombstoneRecordList: List<ConsumerRecord<String, KafkaSyketilfellebit>>,
    ) {
        if (tombstoneRecordList.isNotEmpty()) {
            log.error("Value of ${tombstoneRecordList.size} ConsumerRecord are null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected")
            COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_TOMBSTONE.increment(tombstoneRecordList.size.toDouble())
        }
    }

    private fun processRelevantRecordList(
        relevantRecordList: List<ConsumerRecord<String, KafkaSyketilfellebit>>,
    ) {
        val relevantOppfolgingstilfelleBitList = relevantRecordList.map {
            it.value().toOppfolgingstilfelleBit()
        }
        relevantOppfolgingstilfelleBitList.forEach { oppfolgingstilfelleBit ->
            database.connection.use { connection ->
                oppfolgingstilfelleService.createOppfolgingstilfelleBitList(
                    connection = connection,
                    oppfolgingstilfelleBitList = listOf(oppfolgingstilfelleBit),
                )
                connection.commit()
            }
        }
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
