package no.nav.syfo.util

import io.ktor.server.routing.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient

suspend fun RoutingContext.validateVeilederAccess(
    action: String,
    personIdentToAccess: PersonIdentNumber,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    requestBlock: suspend () -> Unit,
) {
    val callId = getCallId()

    val token = getBearerHeader()
        ?: throw IllegalArgumentException("Failed to complete the following action: $action. No Authorization header supplied")

    val hasVeilederAccess = veilederTilgangskontrollClient.hasAccess(
        callId = callId,
        personIdent = personIdentToAccess,
        token = token,
    )
    if (hasVeilederAccess) {
        requestBlock()
    } else {
        throw ForbiddenAccessVeilederException(
            action = action,
        )
    }
}

class ForbiddenAccessVeilederException(
    action: String,
    message: String = "Denied NAVIdent access to personIdent: $action",
) : RuntimeException(message)
