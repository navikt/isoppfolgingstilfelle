package no.nav.syfo.infrastructure.kafka.syketilfelle

import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val SYKETILFELLEBIT_TOPIC = "flex.syketilfellebiter"

fun launchKafkaTaskSyketilfelleBit(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    syketilfellebitConsumer: SyketilfellebitConsumer,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicSyketilfelleBit(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            syketilfellebitConsumer = syketilfellebitConsumer,
        )
    }
}

fun blockingApplicationLogicSyketilfelleBit(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    syketilfellebitConsumer: SyketilfellebitConsumer,
) {
    log.info("Setting up kafka consumer for KafkaSyketilfelleBit")

    val consumerProperties = kafkaSyketilfelleBitConsumerConfig(kafkaEnvironment)
    val consumer = KafkaConsumer<String, KafkaSyketilfellebit>(consumerProperties)

    consumer.subscribe(
        listOf(SYKETILFELLEBIT_TOPIC)
    )
    while (applicationState.ready) {
        syketilfellebitConsumer.pollAndProcessRecords(
            consumer = consumer,
        )
    }
}
