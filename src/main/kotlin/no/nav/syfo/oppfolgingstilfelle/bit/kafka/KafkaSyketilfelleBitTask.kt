package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val SYKETILFELLEBIT_TOPIC = "flex.syketilfellebiter"

fun launchKafkaTaskSyketilfelleBit(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaSyketilfellebitService: KafkaSyketilfellebitService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicSyketilfelleBit(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            kafkaSyketilfellebitService = kafkaSyketilfellebitService,
        )
    }
}

fun blockingApplicationLogicSyketilfelleBit(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaSyketilfellebitService: KafkaSyketilfellebitService,
) {
    log.info("Setting up kafka consumer for KafkaSyketilfelleBit")

    val consumerProperties = kafkaSyketilfelleBitConsumerConfig(kafkaEnvironment)
    val kafkaConsumerSyketilfelleBit = KafkaConsumer<String, KafkaSyketilfellebit>(consumerProperties)

    kafkaConsumerSyketilfelleBit.subscribe(
        listOf(SYKETILFELLEBIT_TOPIC)
    )
    while (applicationState.ready) {
        kafkaSyketilfellebitService.pollAndProcessRecords(
            kafkaConsumerSyketilfelleBit = kafkaConsumerSyketilfelleBit,
        )
    }
}
