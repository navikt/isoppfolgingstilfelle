package no.nav.syfo.client.pdl

import io.micrometer.core.instrument.Counter
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_PDL_IDENTER_BASE = "${METRICS_NS}_call_pdl_identer"
const val CALL_PDL_IDENTER_SUCCESS = "${CALL_PDL_IDENTER_BASE}_success_count"
const val CALL_PDL_IDENTER_FAIL = "${CALL_PDL_IDENTER_BASE}_fail_count"

val COUNT_CALL_PDL_IDENTER_SUCCESS: Counter = Counter.builder(CALL_PDL_IDENTER_SUCCESS)
    .description("Counts the number of successful calls to persondatalosning - identer")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_IDENTER_FAIL: Counter = Counter.builder(CALL_PDL_IDENTER_FAIL)
    .description("Counts the number of failed calls to persondatalosning - identer")
    .register(METRICS_REGISTRY)

const val CALL_PDL_IDENTER_CACHE_HIT = "${CALL_PDL_IDENTER_BASE}_cache_hit_count"
const val CALL_PDL_IDENTER_CACHE_MISS = "${CALL_PDL_IDENTER_BASE}_cache_miss_count"

val COUNT_CALL_PDL_IDENTER_CACHE_HIT: Counter = Counter.builder(CALL_PDL_IDENTER_CACHE_HIT)
    .description("Counts the number of cache hits for calls to persondatalosning - identer")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_IDENTER_CACHE_MISS: Counter = Counter.builder(CALL_PDL_IDENTER_CACHE_MISS)
    .description("Counts the number of cache misses for calls to persondatalosning - identer")
    .register(METRICS_REGISTRY)
