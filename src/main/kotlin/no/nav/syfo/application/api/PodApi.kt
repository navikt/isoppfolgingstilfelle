package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState

const val podLivenessPath = "/internal/is_alive"
const val podReadinessPath = "/internal/is_ready"

fun Routing.registerPodApi(
    applicationState: ApplicationState,
) {
    get(podLivenessPath) {
        if (applicationState.alive) {
            call.respondText(
                text = "I'm alive! :)",
            )
        } else {
            call.respondText(
                status = HttpStatusCode.InternalServerError,
                text = "I'm dead x_x",
            )
        }
    }
    get(podReadinessPath) {
        val isReady = applicationState.ready
        if (isReady) {
            call.respondText(
                text = "I'm ready! :)",
            )
        } else {
            call.respondText(
                status = HttpStatusCode.InternalServerError,
                text = "Please wait! I'm not ready :(",
            )
        }
    }
}
