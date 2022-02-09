package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.OppfolgingstilfelleService
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.domain.toOppfolgingstilfellePersonDTO
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
            val personIdent = personIdentHeader()?.let { personIdent ->
                PersonIdentNumber(personIdent)
            }
                ?: throw IllegalArgumentException("Failed to retrieve OppfolgingstilfelleDTO: No $NAV_PERSONIDENT_HEADER supplied in request header")

            validateVeilederAccess(
                action = "Read OppfolgingstilfelleDTO for Person with PersonIdent",
                personIdentToAccess = personIdent,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
            ) {
                val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                    oppfolgingstilfelleService.oppfolgingstilfellePerson(
                        personIdent = personIdent,
                    ).toOppfolgingstilfellePersonDTO(
                        personIdent = personIdent,
                    )

                call.respond(oppfolgingstilfellePersonDTO)
            }
        }
    }
}
