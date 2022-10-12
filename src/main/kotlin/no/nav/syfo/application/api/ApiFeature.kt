package no.nav.syfo.application.api

import com.auth0.jwt.JWT
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import no.nav.syfo.application.metric.METRICS_REGISTRY
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.*
import java.time.Duration
import java.util.*

fun Application.installMetrics() {
    install(MicrometerMetrics) {
        registry = METRICS_REGISTRY
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
            .build()
    }
}

fun Application.installCallId() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { UUID.randomUUID().toString() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson { configure() }
    }
}

fun ApplicationCall.personIdent(): PersonIdentNumber? {
    val token = this.getBearerHeader()
    val decodedJWT = JWT.decode(token)
    val pid = decodedJWT.claims["pid"]

    return pid?.asString()?.let { PersonIdentNumber(it) }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val callId = call.getCallId()
            val consumerClientId = call.getConsumerClientId()
            val logExceptionMessage = "Caught exception, callId=$callId, consumerClientId=$consumerClientId"
            when (cause) {
                is ForbiddenAccessVeilederException -> {
                    call.application.log.warn(logExceptionMessage, cause)
                }

                else -> {
                    call.application.log.error(logExceptionMessage, cause)
                }
            }

            var isUnexpectedException = false

            val responseStatus: HttpStatusCode = when (cause) {
                is ResponseException -> {
                    cause.response.status
                }

                is IllegalArgumentException -> {
                    HttpStatusCode.BadRequest
                }

                is ForbiddenAccessVeilederException -> {
                    HttpStatusCode.Forbidden
                }

                else -> {
                    isUnexpectedException = true
                    HttpStatusCode.InternalServerError
                }
            }
            val message = if (isUnexpectedException) {
                "The server reported an unexpected error and cannot complete the request."
            } else {
                cause.message ?: "Unknown error"
            }
            call.respond(responseStatus, message)
        }
    }
}
