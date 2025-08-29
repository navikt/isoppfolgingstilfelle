package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.ApplicationState
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.launchBackgroundTask
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"
private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.identhendelse")

fun launchKafkaTaskIdenthendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    kafkaIdenthendelseConsumerService: IdenthendelseConsumerService,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        log.info("Setting up kafka consumer for Identhendelse from PDL-aktor")

        val consumerProperties = kafkaIdenthendelseConsumerConfig(kafkaEnvironment)
        val kafkaConsumer = KafkaConsumer<String, GenericRecord>(consumerProperties)

        kafkaConsumer.subscribe(
            listOf(PDL_AKTOR_TOPIC)
        )
        while (applicationState.ready) {
            if (kafkaConsumer.subscription().isEmpty()) {
                kafkaConsumer.subscribe(listOf(PDL_AKTOR_TOPIC))
            }
            kafkaIdenthendelseConsumerService.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer,
            )
        }
    }
}
