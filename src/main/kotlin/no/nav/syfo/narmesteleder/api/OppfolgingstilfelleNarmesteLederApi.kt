package no.nav.syfo.narmesteleder.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.personIdent
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.narmesteleder.NarmesteLederAccessService
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.domain.toOppfolgingstilfelleDTOList
import no.nav.syfo.util.*

const val oppfolgingstilfelleApiV1Path = "/api/v1/narmesteleder/oppfolgingstilfelle"

fun Route.registerOppfolgingstilfelleNarmesteLederApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    narmesteLederAccessService: NarmesteLederAccessService
) {
    route(oppfolgingstilfelleApiV1Path) {
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
                oppfolgingstilfelleService.oppfolgingstilfelleList(
                    callId = getCallId(),
                    personIdent = arbeidstakerPersonIdent,
                )

            call.respond(oppfolgingstilfelleList.toOppfolgingstilfelleDTOList())
        }
    }
}
