package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleArbeidstakerDTO
import no.nav.syfo.oppfolgingstilfelle.domain.toOppfolgingstilfelleArbeidstakerDTO
import no.nav.syfo.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo")

const val oppfolgingstilfelleApiV1Path = "/api/internad/v1/oppfolgingstilfelle"
const val oppfolgingstilfelleApiPersonIdentPath = "/personident"

fun Route.registerOppfolgingstilfelleApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(oppfolgingstilfelleApiV1Path) {
        get(oppfolgingstilfelleApiPersonIdentPath) {
            val callId = getCallId()
            val token = getBearerHeader()
                ?: throw IllegalArgumentException("Could not retrieve OppfolgingstilfelleDTO: No Authorization header supplied")
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Could not retrieve OppfolgingstilfelleDTO: No $NAV_PERSONIDENT_HEADER supplied in request header")

            val harTilgang = veilederTilgangskontrollClient.harTilgang(
                callId = callId,
                personIdent = personIdent,
                token = token,
            )

            if (harTilgang) {
                val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfelleArbeidstakerDTO =
                    oppfolgingstilfelleService.oppfolgingstilfelleArbeidstaker(
                        arbeidstakerPersonIdent = personIdent,
                    ).toOppfolgingstilfelleArbeidstakerDTO(
                        arbeidstakerPersonIdent = personIdent,
                    )

                call.respond(oppfolgingstilfelleArbeidstakerDTO)
            } else {
                val accessDeniedMessage = "Denied Veileder access to PersonIdent"
                log.warn("$accessDeniedMessage, {}", callId)
                call.respond(HttpStatusCode.Forbidden, accessDeniedMessage)
            }
        }
    }
}
