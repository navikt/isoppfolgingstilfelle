package no.nav.syfo.infrastructure.kafka.personhendelse

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.syfo.ApplicationState
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.application.PersonhendelseService
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.OppfolgingstilfellePersonRepository
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.launchBackgroundTask
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val PDL_LEESAH_TOPIC = "pdl.leesah-v1"
private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.pdlpersonhendelse")

fun launchKafkaTaskPersonhendelse(
    applicationState: ApplicationState,
    kafkaEnvironment: KafkaEnvironment,
    database: DatabaseInterface,
    oppfolgingstilfellePersonRepository: OppfolgingstilfellePersonRepository,
) {
    launchBackgroundTask(
        applicationState = applicationState,
    ) {
        log.info("Setting up kafka consumer for Personhendelse from PDL")
        val oppfolgingstilfelleService = OppfolgingstilfelleService(
            oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
        )
        val personhendelseService = PersonhendelseService(
            database = database,
            oppfolgingstilfelleService = oppfolgingstilfelleService,
            oppfolgingstilfellePersonRepository = oppfolgingstilfellePersonRepository,
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
