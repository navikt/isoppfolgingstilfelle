package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.personIdent
import no.nav.syfo.application.NarmesteLederAccessService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.domain.toOppfolgingstilfelleDTOList
import no.nav.syfo.util.*

fun Route.registerOppfolgingstilfelleNarmesteLederApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    narmesteLederAccessService: NarmesteLederAccessService,
) {
    route("/api/v1/narmesteleder/oppfolgingstilfelle") {
        get {
            val callId = getCallId()
            val token =
                this.getBearerHeader()
                    ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No token supplied in request header")

            val narmesteLederPersonIdent = call.personIdent()
                ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No pid found in token")

            val arbeidstakerPersonIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No $NAV_PERSONIDENT_HEADER supplied in request header")

            val virksomhetsnummer = virksomhetsnummerHeader()?.let { virksomhetsnummer ->
                Virksomhetsnummer(virksomhetsnummer)
            }
                ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No $NAV_VIRKSOMHETSNUMMER supplied in request header")

            val narmesteLederHasAccessToArbeidstaker =
                narmesteLederAccessService.isNarmesteLederForArbeidstakerInVirksomhet(
                    arbeidstakerPersonIdentNumber = arbeidstakerPersonIdent,
                    callId = callId,
                    narmesteLederPersonIdentNumber = narmesteLederPersonIdent,
                    tokenx = token,
                    virksomhetsnummer = virksomhetsnummer
                )

            if (!narmesteLederHasAccessToArbeidstaker) call.respond(
                HttpStatusCode.Forbidden,
                "Access denied: pid is not narmeste leder for $NAV_PERSONIDENT_HEADER"
            )

            val oppfolgingstilfelleList =
                oppfolgingstilfelleService.getOppfolgingstilfeller(
                    personIdent = arbeidstakerPersonIdent,
                )

            call.respond(oppfolgingstilfelleList.toOppfolgingstilfelleDTOList())
        }
    }
}
