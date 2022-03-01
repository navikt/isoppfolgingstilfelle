package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfellePersonService
import no.nav.syfo.oppfolgingstilfelle.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.util.*

const val oppfolgingstilfelleApiV1Path = "/api/internad/v1/oppfolgingstilfelle"
const val oppfolgingstilfelleApiPersonIdentPath = "/personident"

fun Route.registerOppfolgingstilfelleApi(
    oppfolgingstilfellePersonService: OppfolgingstilfellePersonService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route(oppfolgingstilfelleApiV1Path) {
        get(oppfolgingstilfelleApiPersonIdentPath) {
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to retrieve OppfolgingstilfelleDTO: No $NAV_PERSONIDENT_HEADER supplied in request header")

            validateVeilederAccess(
                action = "Read OppfolgingstilfelleDTO for Person with PersonIdent",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val oppfolgingstilfellePersonDTO = oppfolgingstilfellePersonService.oppfolgingstilfelleList(
                    callId = getCallId(),
                    personIdent = personIdent,
                ).toOppfolgingstilfellePersonDTO(
                    personIdent = personIdent,
                )
                call.respond(oppfolgingstilfellePersonDTO)
            }
        }
    }
}
