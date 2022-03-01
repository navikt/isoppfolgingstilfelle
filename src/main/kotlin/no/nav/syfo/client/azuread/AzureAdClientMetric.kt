package no.nav.syfo.client.azuread

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Counter.builder
import no.nav.syfo.application.metric.METRICS_NS
import no.nav.syfo.application.metric.METRICS_REGISTRY

const val CALL_AZUREAD_BASE = "${METRICS_NS}_call_azuread"

const val CALL_AZUREAD_TOKEN_SYSTEM_BASE = "${CALL_AZUREAD_BASE}_token_system"
const val CALL_AZUREAD_TOKEN_SYSTEM_CACHE_HIT = "${CALL_AZUREAD_TOKEN_SYSTEM_BASE}_cache_hit_count"
const val CALL_AZUREAD_TOKEN_SYSTEM_CACHE_MISS = "${CALL_AZUREAD_TOKEN_SYSTEM_BASE}_cache_miss_count"

val COUNT_CALL_AZUREAD_TOKEN_SYSTEM_CACHE_HIT: Counter = builder(CALL_AZUREAD_TOKEN_SYSTEM_CACHE_HIT)
    .description("Counts the number of cache hits for calls to AzureAd - System token")
    .register(METRICS_REGISTRY)

val COUNT_CALL_AZUREAD_TOKEN_SYSTEM_CACHE_MISS: Counter = builder(CALL_AZUREAD_TOKEN_SYSTEM_CACHE_MISS)
    .description("Counts the number of cache misses for calls to AzureAd - System token")
    .register(METRICS_REGISTRY)
