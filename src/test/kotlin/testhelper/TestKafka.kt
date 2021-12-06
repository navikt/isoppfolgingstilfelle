package testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.oppfolgingstilfelle.bit.kafka.SYKETILFELLEBIT_TOPIC

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        SYKETILFELLEBIT_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)
