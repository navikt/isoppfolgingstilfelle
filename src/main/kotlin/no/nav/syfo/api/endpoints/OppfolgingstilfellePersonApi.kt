package no.nav.syfo.api.endpoints

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.hasGjentakendeSykefravar
import no.nav.syfo.domain.toOppfolgingstilfellePersonDTO
import no.nav.syfo.infrastructure.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.*
import kotlin.time.measureTimedValue

fun Route.registerOppfolgingstilfelleApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
) {
    route("/api/internad/v1/oppfolgingstilfelle") {
        get("/personident") {
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
                val oppfolgingstilfellePersonDTO =
                    oppfolgingstilfelleService.getOppfolgingstilfellePerson(personIdent = personIdent)
                        ?.toOppfolgingstilfellePersonDTO() ?: OppfolgingstilfellePersonDTO(
                        oppfolgingstilfelleList = emptyList(),
                        personIdent = personIdent.value,
                        dodsdato = dodsdato,
                        hasGjentakendeSykefravar = emptyList<Oppfolgingstilfelle>().hasGjentakendeSykefravar()
                    )
                call.respond(oppfolgingstilfellePersonDTO)
            }
        }
        post("/persons") {
            val token = getBearerHeader()!!
            val callId = getCallId()
            val personIdents = call.receive<List<String>>().map { PersonIdentNumber(it) }
            val personIdentsWithVeilederAccess = veilederTilgangskontrollClient.hasAccessToPersons(
                personIdents = personIdents,
                token = token,
                callId = callId,
            )

            val (oppfolgingstilfellerPersonsDTOs, duration) = measureTimedValue {
                personIdentsWithVeilederAccess.map {
                    val dodsdato = oppfolgingstilfelleService.getDodsdato(it)
                    oppfolgingstilfelleService.getOppfolgingstilfellePerson(personIdent = it)
                        ?.toOppfolgingstilfellePersonDTO() ?: OppfolgingstilfellePersonDTO(
                        oppfolgingstilfelleList = emptyList(),
                        personIdent = it.value,
                        dodsdato = dodsdato,
                        hasGjentakendeSykefravar = emptyList<Oppfolgingstilfelle>().hasGjentakendeSykefravar(),
                    )
                }
            }

            application.log.info("Got oppfolgingstilfeller for ${personIdentsWithVeilederAccess.size} persons in ${duration.inWholeMilliseconds} ms")

            call.respond(oppfolgingstilfellerPersonsDTOs)
        }
    }
}
