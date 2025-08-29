package no.nav.syfo.infrastructure.kafka.sykmeldingstatus

import io.micrometer.core.instrument.Counter
import no.nav.syfo.api.metric.METRICS_NS
import no.nav.syfo.api.metric.METRICS_REGISTRY

const val KAFKA_CONSUMER_SYKMELDINGSTATUS_BASE = "${METRICS_NS}_kafka_consumer_sykmeldingstatus"
const val KAFKA_CONSUMER_SYKMELDINGSTATUS_READ = "${KAFKA_CONSUMER_SYKMELDINGSTATUS_BASE}_read"
const val KAFKA_CONSUMER_SYKMELDINGSTATUS_TOMBSTONE = "${KAFKA_CONSUMER_SYKMELDINGSTATUS_BASE}_tombstone"

val COUNT_KAFKA_CONSUMER_SYKMELDINGSTATUS_READ: Counter = Counter.builder(KAFKA_CONSUMER_SYKMELDINGSTATUS_READ)
    .description("Counts the number of reads from topic - sykmeldingstatus-leesah")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_SYKMELDINGSTATUS_TOMBSTONE: Counter = Counter.builder(KAFKA_CONSUMER_SYKMELDINGSTATUS_TOMBSTONE)
    .description("Counts the number of tombstones received from topic - sykmeldingstatus-leesah")
    .register(METRICS_REGISTRY)
