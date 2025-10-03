package no.nav.syfo.domain

import io.micrometer.core.instrument.Counter
import no.nav.syfo.api.metric.METRICS_NS
import no.nav.syfo.api.metric.METRICS_REGISTRY

const val SYKMELDING_NY_COUNTER_NAME = "${METRICS_NS}_oppfolgingstilfelle_sykmelding_ny"

val SYKMELDING_NY_COUNTER: Counter = Counter.builder(SYKMELDING_NY_COUNTER_NAME)
    .description("Counts the number of oppfolgingstilfeller > 118 days created only from sykmelding-ny")
    .register(METRICS_REGISTRY)
