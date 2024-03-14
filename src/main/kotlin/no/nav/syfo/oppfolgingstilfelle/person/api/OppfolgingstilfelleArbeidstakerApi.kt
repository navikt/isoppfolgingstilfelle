package no.nav.syfo.oppfolgingstilfelle.person.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.access.APIConsumerAccessService
import no.nav.syfo.application.api.personIdent
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.person.domain.toOppfolgingstilfelleDTOList
import no.nav.syfo.util.*

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
