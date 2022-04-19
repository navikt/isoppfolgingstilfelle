package no.nav.syfo.oppfolgingstilfelle.person.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.util.*

const val oppfolgingstilfelleApiV1Path = "/api/internad/v1/oppfolgingstilfelle"
const val oppfolgingstilfelleApiPersonIdentPath = "/personident"

fun Route.registerOppfolgingstilfelleApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
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
                val oppfolgingstilfellePersonDTO = oppfolgingstilfelleService.oppfolgingstilfelleList(
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
