package no.nav.syfo.oppfolgingstilfelle.person.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.util.*

const val oppfolgingstilfelleSystemApiV1Path = "/api/system/v1/oppfolgingstilfelle"
const val oppfolgingstilfelleSystemApiPersonIdentPath = "/personident"

fun Route.registerOppfolgingstilfelleSystemApi(
    apiConsumerAccessService: APIConsumerAccessService,
    authorizedApplicationNames: List<String>,
    oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    route(oppfolgingstilfelleSystemApiV1Path) {
        get(oppfolgingstilfelleSystemApiPersonIdentPath) {
            val token = this.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No token supplied in request header")
            apiConsumerAccessService.validateConsumerApplicationAZP(
                authorizedApplicationNames = authorizedApplicationNames,
                token = token,
            )
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to retrieve OppfolgingstilfelleDTO: No $NAV_PERSONIDENT_HEADER supplied in request header")

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
}
