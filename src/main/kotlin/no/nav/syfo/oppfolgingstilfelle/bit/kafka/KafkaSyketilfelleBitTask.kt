package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.toOppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.database.NoElementInsertedException
import no.nav.syfo.util.kafkaCallId
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val SYKETILFELLEBIT_TOPIC = "flex.syketilfellebiter"

fun launchKafkaTaskSyketilfelleBit(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
    database: DatabaseInterface,
) {
    launchBackgroundTask(applicationState = applicationState) {
        blockingApplicationLogicSyketilfelleBit(
            applicationState = applicationState,
            applicationEnvironmentKafka = applicationEnvironmentKafka,
            database = database,
        )
    }
}

fun blockingApplicationLogicSyketilfelleBit(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
    database: DatabaseInterface,
) {
    log.info("Setting up kafka consumer SyketilfelleBit")

    val consumerProperties = kafkaSyketilfelleBitConsumerConfig(applicationEnvironmentKafka)
    val kafkaConsumerSyketilfelleBit = KafkaConsumer<String, KafkaSyketilfellebit>(consumerProperties)

    kafkaConsumerSyketilfelleBit.subscribe(
        listOf(SYKETILFELLEBIT_TOPIC)
    )
    while (applicationState.ready) {
        pollAndProcessSyketilfelleBit(
            database = database,
            kafkaConsumerSyketilfelleBit = kafkaConsumerSyketilfelleBit,
        )
    }
}

fun pollAndProcessSyketilfelleBit(
    database: DatabaseInterface,
    kafkaConsumerSyketilfelleBit: KafkaConsumer<String, KafkaSyketilfellebit>,
) {
    val records = kafkaConsumerSyketilfelleBit.poll(Duration.ofMillis(1000))
    if (records.count() > 0) {
        createAndStoreFromSyketilfelleBitRecords(
            consumerRecords = records,
            database = database,
        )
        kafkaConsumerSyketilfelleBit.commitSync()
    }
}

fun createAndStoreFromSyketilfelleBitRecords(
    consumerRecords: ConsumerRecords<String, KafkaSyketilfellebit>,
    database: DatabaseInterface,
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
            log.info("Received SyketilfelleBit, ready to process. id=${consumerRecord.key()}, timestamp=${consumerRecord.timestamp()}, callId=$callId")

            val oppfolgingstilfelleBit = consumerRecord.value().toOppfolgingstilfelleBit()

            try {
                connection.createOppfolgingstilfelleBit(
                    commit = false,
                    oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                )
            } catch (noElementInsertedException: NoElementInsertedException) {
                log.warn(
                    "No ${KafkaSyketilfellebit::class.java.simpleName} was inserted into database, probably due to an attempt to insert a duplicate",
                    noElementInsertedException
                )
                COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE.increment()
            }
        }
        connection.commit()
    }
}
