package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.database.DatabaseInterface

const val podLivenessPath = "/internal/is_alive"
const val podReadinessPath = "/internal/is_ready"

fun Routing.registerPodApi(
    applicationState: ApplicationState,
    database: DatabaseInterface,
) {
    get(podLivenessPath) {
        if (applicationState.alive) {
            call.respondText("I'm alive! :)")
        } else {
            call.respondText("I'm dead x_x", status = HttpStatusCode.InternalServerError)
        }
    }
    get(podReadinessPath) {
        val isReady = isReady(
            applicationState = applicationState,
            database = database,
        )
        if (isReady) {
            call.respondText("I'm ready! :)")
        } else {
            call.respondText("Please wait! I'm not ready :(", status = HttpStatusCode.InternalServerError)
        }
    }
}

private fun isReady(
    applicationState: ApplicationState,
    database: DatabaseInterface,
): Boolean {
    return applicationState.ready && database.isReady()
}

private fun DatabaseInterface.isReady(): Boolean {
    return try {
        connection.use {
            it.isValid(1)
        }
    } catch (ex: Exception) {
        false
    }
}
