package no.nav.syfo.api.endpoints

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.access.APIConsumerAccessService
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.hasGjentakendeSykefravar
import no.nav.syfo.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.personIdentHeader

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
            val oppfolgingstilfellePersonDTO =
                oppfolgingstilfelleService.getOppfolgingstilfellePerson(personIdent = personIdent)
                    ?.toOppfolgingstilfellePersonDTO() ?: OppfolgingstilfellePersonDTO(
                    oppfolgingstilfelleList = emptyList(),
                    personIdent = personIdent.value,
                    dodsdato = dodsdato,
                    hasGjentakendeSykefravar = emptyList<Oppfolgingstilfelle>().hasGjentakendeSykefravar(),
                )
            call.respond(oppfolgingstilfellePersonDTO)
        }
    }
}
