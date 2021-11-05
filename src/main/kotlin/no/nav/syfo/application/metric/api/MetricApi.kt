package no.nav.syfo.application.metric.api

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.metric.METRICS_REGISTRY

fun Routing.registerMetricApi() {
    get("/internal/metrics") {
        call.respondText(METRICS_REGISTRY.scrape())
    }
}
