package no.nav.syfo.oppfolgingstilfelle.bit.kafka.statusendring

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_STATUSENDRING_BASE = "${METRICS_NS}_kafka_consumer_statusendring"
const val KAFKA_CONSUMER_STATUSENDRING_READ = "${KAFKA_CONSUMER_STATUSENDRING_BASE}_read"
const val KAFKA_CONSUMER_STATUSENDRING_TOMBSTONE = "${KAFKA_CONSUMER_STATUSENDRING_BASE}_tombstone"

val COUNT_KAFKA_CONSUMER_STATUSENDRING_READ: Counter = Counter.builder(KAFKA_CONSUMER_STATUSENDRING_READ)
    .description("Counts the number of reads from topic - sykmeldingstatus-leesah")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_STATUSENDRING_TOMBSTONE: Counter = Counter.builder(KAFKA_CONSUMER_STATUSENDRING_TOMBSTONE)
    .description("Counts the number of tombstones received from topic - sykmeldingstatus-leesah")
    .register(METRICS_REGISTRY)
