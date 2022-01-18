package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleArbeidstakerDTO
import no.nav.syfo.oppfolgingstilfelle.domain.toOppfolgingstilfelleArbeidstakerDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.personIdentHeader

const val oppfolgingstilfelleApiV1Path = "/api/internad/v1/oppfolgingstilfelle"
const val oppfolgingstilfelleApiPersonIdentPath = "/personident"

fun Route.registerOppfolgingstilfelleApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
) {
    route(oppfolgingstilfelleApiV1Path) {
        get(oppfolgingstilfelleApiPersonIdentPath) {
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Could not retrieve OppfolgingstilfelleDTO: No $NAV_PERSONIDENT_HEADER supplied in request header")

            val oppfolgingstilfelleArbeidstakerDTO: OppfolgingstilfelleArbeidstakerDTO =
                oppfolgingstilfelleService.oppfolgingstilfelleArbeidstaker(
                    arbeidstakerPersonIdent = personIdent,
                ).toOppfolgingstilfelleArbeidstakerDTO(arbeidstakerPersonIdent = personIdent)

            call.respond(oppfolgingstilfelleArbeidstakerDTO)
        }
    }
}
