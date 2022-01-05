package no.nav.syfo.oppfolgingstilfelle.bit.kafka

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_SYKETILFELLEBIT_BASE = "${METRICS_NS}_kafka_consumer_nlr"
const val KAFKA_CONSUMER_SYKETILFELLEBIT_READ = "${KAFKA_CONSUMER_SYKETILFELLEBIT_BASE}_read"
const val KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED = "${KAFKA_CONSUMER_SYKETILFELLEBIT_BASE}_created"
const val KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE = "${KAFKA_CONSUMER_SYKETILFELLEBIT_BASE}_duplicate"
const val KAFKA_CONSUMER_SYKETILFELLEBIT_TOMBSTONE = "${KAFKA_CONSUMER_SYKETILFELLEBIT_BASE}_tombstone"

val COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_READ: Counter = Counter.builder(KAFKA_CONSUMER_SYKETILFELLEBIT_READ)
    .description("Counts the number of reads from topic - syketilfellebit")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED: Counter = Counter.builder(KAFKA_CONSUMER_SYKETILFELLEBIT_CREATED)
    .description("Counts the number of created from topic - syketilfellebit")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE: Counter = Counter.builder(KAFKA_CONSUMER_SYKETILFELLEBIT_DUPLICATE)
    .description("Counts the number of duplicates received from topic - syketilfellebit")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_SYKETILFELLEBIT_TOMBSTONE: Counter = Counter.builder(KAFKA_CONSUMER_SYKETILFELLEBIT_TOMBSTONE)
    .description("Counts the number of tombstones received from topic - syketilfellebit")
    .register(METRICS_REGISTRY)
