package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.metric.api.registerMetricApi

fun Application.apiModule(
    applicationState: ApplicationState,
    database: DatabaseInterface,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()

    routing {
        registerPodApi(
            applicationState = applicationState,
            database = database,
        )
        registerMetricApi()
    }
}
