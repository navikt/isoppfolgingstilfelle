package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.bit.database.getOppfolgingstilfelleBitForUUID
import no.nav.syfo.oppfolgingstilfelle.bit.toOppfolgingstilfelleBit
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.LoggerFactory
import java.sql.Connection
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
        database.connection.use { connection ->
            consumerRecords.forEach { consumerRecord ->
                val callId = kafkaCallId()
                if (consumerRecord.value() == null) {
                    log.error("Value of ConsumerRecord is null, most probably due to a tombstone. Contact the owner of the topic if an error is suspected. key=${consumerRecord.key()} from topic: ${consumerRecord.topic()}, partiion=${consumerRecord.partition()}, offset=${consumerRecord.offset()}")
                    COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_TOMBSTONE.increment()
                    return
                }

                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_READ.increment()
                log.info("Received ${KafkaSyketilfellebit::class.java.simpleName}, ready to process. id=${consumerRecord.key()}, timestamp=${consumerRecord.timestamp()}, callId=$callId")

                receiveKafkaSyketilfellebit(
                    connection = connection,
                    kafkaSyketilfellebit = consumerRecord.value(),
                )
            }
            connection.commit()
        }
    }

    private fun receiveKafkaSyketilfellebit(
        connection: Connection,
        kafkaSyketilfellebit: KafkaSyketilfellebit,
    ) {
        if (kafkaSyketilfellebit.isRelevantForOppfolgingstilfelle()) {
            val oppfolgingstilfelleBit = kafkaSyketilfellebit.toOppfolgingstilfelleBit()

            val isOppfolgingstilfelleBitDuplicate =
                connection.getOppfolgingstilfelleBitForUUID(oppfolgingstilfelleBit.uuid) != null
            if (isOppfolgingstilfelleBitDuplicate) {
                log.warn(
                    "No ${KafkaSyketilfellebit::class.java.simpleName} was inserted into database, attempted to insert a duplicate"
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE.increment()
            } else {
                oppfolgingstilfelleService.createOppfolgingstilfellePerson(
                    connection = connection,
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED.increment()
            }
        } else {
            COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_NOT_RELEVANT.increment()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaSyketilfellebitService::class.java)
    }
}
