package no.nav.syfo.api.endpoints

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.personIdent
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.toOppfolgingstilfelleDTOList
import no.nav.syfo.util.getBearerHeader

const val oppfolgingstilfelleArbeidstakerApiV1Path = "/api/v1/arbeidstaker/oppfolgingstilfelle"

fun Route.registerOppfolgingstilfelleArbeidstakerApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    route(oppfolgingstilfelleArbeidstakerApiV1Path) {
        get {
            this.getBearerHeader()
                ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No token supplied in request header")
            val arbeidstakerPersonIdent = call.personIdent()
                ?: throw IllegalArgumentException("Failed to retrieve oppfolgingstilfelle: No pid found in token")
            val oppfolgingstilfelleDTOList = oppfolgingstilfelleService.getOppfolgingstilfeller(
                personIdent = arbeidstakerPersonIdent,
            ).toOppfolgingstilfelleDTOList()
            call.respond(oppfolgingstilfelleDTOList)
        }
    }
}
