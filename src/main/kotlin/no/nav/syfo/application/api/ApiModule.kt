package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metric.api.registerMetricApi

fun Application.apiModule(
    applicationState: ApplicationState,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
        )
        registerMetricApi()
    }
}
