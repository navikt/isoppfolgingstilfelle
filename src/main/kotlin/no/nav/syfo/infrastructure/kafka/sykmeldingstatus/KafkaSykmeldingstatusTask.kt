package no.nav.syfo.infrastructure.kafka.sykmeldingstatus

import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val STATUSENDRING_TOPIC = "teamsykmelding.sykmeldingstatus-leesah"

fun launchKafkaTaskStatusendring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaSykmeldingstatusService: KafkaSykmeldingstatusService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicStatusendring(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            kafkaSykmeldingstatusService = kafkaSykmeldingstatusService,
        )
    }
}

fun blockingApplicationLogicStatusendring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaSykmeldingstatusService: KafkaSykmeldingstatusService,
) {
    log.info("Setting up kafka consumer for KafkaStatusendring")

    val consumerProperties = kafkaStatusendringConsumerConfig(kafkaEnvironment)
    val kafkaConsumerStatusendring = KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>(consumerProperties)

    kafkaConsumerStatusendring.subscribe(
        listOf(STATUSENDRING_TOPIC)
    )
    while (applicationState.ready) {
        kafkaSykmeldingstatusService.pollAndProcessRecords(
            kafkaConsumerStatusendring = kafkaConsumerStatusendring,
        )
    }
}
