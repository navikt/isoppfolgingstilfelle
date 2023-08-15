package no.nav.syfo.personhendelse.kafka

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.backgroundtask.launchBackgroundTask
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.kafka.KafkaEnvironment
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.personhendelse.PersonhendelseService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val PDL_LEESAH_TOPIC = "pdl.leesah-v1"
private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.pdlpersonhendelse")

fun launchKafkaTaskPersonhendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        log.info("Setting up kafka consumer for Personhendelse from PDL")
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            database = database,
        )
        val personhendelseService = PersonhendelseService(
            database = database,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
        )
        val kafkaPersonhendelseConsumerService = KafkaPersonhendelseConsumerService(
            personhendelseService = personhendelseService,
        )
        val consumerProperties = kafkaPersonhendelseConsumerConfig(
            kafkaEnvironment = kafkaEnvironment,
        )
        val kafkaConsumer = KafkaConsumer<String, Personhendelse>(consumerProperties)

        kafkaConsumer.subscribe(
            listOf(PDL_LEESAH_TOPIC)
        )

        while (applicationState.ready) {
            kafkaPersonhendelseConsumerService.pollAndProcessRecords(
                kafkaConsumer = kafkaConsumer,
            )
        }
    }
}
