package no.nav.syfo.application.api

import io.ktor.application.*
import io.ktor.client.features.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.metrics.micrometer.*
import io.ktor.response.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import no.nav.syfo.application.metric.METRICS_REGISTRY
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
        jackson(block = configureJacksonMapper())
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { cause ->
            val callId = getCallId()
            val consumerClientId = getConsumerClientId()
            val logExceptionMessage = "Caught exception, callId=$callId, consumerClientId=$consumerClientId"
            when (cause) {
                is ForbiddenAccessVeilederException -> {
                    log.warn(logExceptionMessage, cause)
                }
                else -> {
                    log.error(logExceptionMessage, cause)
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
