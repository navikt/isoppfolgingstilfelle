package no.nav.syfo.oppfolgingstilfelle.person.api

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.util.*

const val oppfolgingstilfelleApiV1Path = "/api/internad/v1/oppfolgingstilfelle"
const val oppfolgingstilfelleApiPersonIdentPath = "/personident"
const val oppfolgingstilfelleApiPersonsPath = "/persons"

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
                val dodsdato = oppfolgingstilfelleService.getDodsdato(personIdent)
                val oppfolgingstilfellePersonDTO = oppfolgingstilfelleService.getOppfolgingstilfeller(
                    callId = getCallId(),
                    personIdent = personIdent,
                ).toOppfolgingstilfellePersonDTO(
                    personIdent = personIdent,
                    dodsdato = dodsdato,
                )
                call.respond(oppfolgingstilfellePersonDTO)
            }
        }
        post(oppfolgingstilfelleApiPersonsPath) {
            val token = getBearerHeader()!!
            val callId = getCallId()
            val personIdents = call.receive<List<String>>().map { PersonIdentNumber(it) }
            val personIdentsWithVeilederAccess = veilederTilgangskontrollClient.hasAccessToPersons(
                personIdents = personIdents,
                token = token,
                callId = callId,
            )

            val oppfolgingstilfellerPersonsDTOs = personIdentsWithVeilederAccess.map {
                val dodsdato = oppfolgingstilfelleService.getDodsdato(it)
                oppfolgingstilfelleService.getOppfolgingstilfeller(
                    callId = getCallId(),
                    personIdent = it,
                ).toOppfolgingstilfellePersonDTO(
                    personIdent = it,
                    dodsdato = dodsdato,
                )
            }
            call.respond(oppfolgingstilfellerPersonsDTOs)
        }
    }
}
