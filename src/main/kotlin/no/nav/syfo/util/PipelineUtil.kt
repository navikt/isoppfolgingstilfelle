package no.nav.syfo.util

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

const val JWT_CLAIM_AZP = "azp"

fun RoutingContext.getBearerHeader(): String? {
    return this.call.getBearerHeader()
}

fun RoutingContext.getCallId(): String {
    return this.call.getCallId()
}

fun ApplicationCall.getCallId(): String {
    return this.request.headers[NAV_CALL_ID_HEADER].toString()
}

fun ApplicationCall.getBearerHeader(): String? {
    return this.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
}

fun ApplicationCall.getConsumerClientId(): String? =
    getBearerHeader()?.let {
        JWT.decode(it).claims[JWT_CLAIM_AZP]?.asString()
    }

fun RoutingContext.personIdentHeader(): String? {
    return this.call.request.headers[NAV_PERSONIDENT_HEADER]
}

fun RoutingContext.virksomhetsnummerHeader(): String? {
    return this.call.request.headers[NAV_VIRKSOMHETSNUMMER]
}
