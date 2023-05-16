package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val STATUSENDRING_TOPIC = "teamsykmelding.sykmeldingstatus-leesah"

fun launchKafkaTaskStatusendring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaStatusendringService: KafkaStatusendringService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        blockingApplicationLogicStatusendring(
            applicationState = applicationState,
            kafkaEnvironment = kafkaEnvironment,
            kafkaStatusendringService = kafkaStatusendringService,
        )
    }
}

fun blockingApplicationLogicStatusendring(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaStatusendringService: KafkaStatusendringService,
) {
    log.info("Setting up kafka consumer for KafkaStatusendring")

    val consumerProperties = kafkaStatusendringConsumerConfig(kafkaEnvironment)
    val kafkaConsumerStatusendring = KafkaConsumer<String, SykmeldingStatusKafkaMessageDTO>(consumerProperties)

    kafkaConsumerStatusendring.subscribe(
        listOf(STATUSENDRING_TOPIC)
    )
    while (applicationState.ready) {
        kafkaStatusendringService.pollAndProcessRecords(
            kafkaConsumerStatusendring = kafkaConsumerStatusendring,
        )
    }
}
