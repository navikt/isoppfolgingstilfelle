package no.nav.syfo.api.endpoints

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.OppfolgingstilfelleService
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.tilgangskontroll.filterPersonsUserHasAccessTo
import no.nav.syfo.common.types.ident.PersonIdent
import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.hasGjentakendeSykefravar
import no.nav.syfo.domain.toOppfolgingstilfellePersonDTO
import kotlin.time.measureTimedValue

fun Route.registerOppfolgingstilfelleApi(
    oppfolgingstilfelleService: OppfolgingstilfelleService,
    tilgangskontrollClient: TilgangskontrollClient,
) {
    route("/api/internad/v1/oppfolgingstilfelle") {
        get("/personident") {
            checkPersonAndSyfoTilgang(
                action = "Read OppfolgingstilfelleDTO for Person with PersonIdent",
                tilgangskontrollClient = tilgangskontrollClient,
            ) { _, targetPersonIdent, _ ->
                val personIdent = PersonIdentNumber(targetPersonIdent.value)
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
            val personIdents = call.receive<List<String>>().map { PersonIdent(it) }

            val personsUserHasAccessTo = filterPersonsUserHasAccessTo(
                action = "Filter persons for OppfolgingstilfelleDTO",
                personIdenter = personIdents,
                tilgangskontrollClient = tilgangskontrollClient,
            ) ?: emptyList()

            val (oppfolgingstilfellerPersonsDTOs, duration) = measureTimedValue {
                personsUserHasAccessTo.map {
                    val personIdent = PersonIdentNumber(it.value)
                    val dodsdato = oppfolgingstilfelleService.getDodsdato(personIdent)

                    oppfolgingstilfelleService.getOppfolgingstilfellePerson(personIdent)
                        ?.toOppfolgingstilfellePersonDTO() ?: OppfolgingstilfellePersonDTO(
                        oppfolgingstilfelleList = emptyList(),
                        personIdent = it.value,
                        dodsdato = dodsdato,
                        hasGjentakendeSykefravar = emptyList<Oppfolgingstilfelle>().hasGjentakendeSykefravar(),
                    )
                }
            }

            application.log.info("Got oppfolgingstilfeller for ${personsUserHasAccessTo.size} persons in ${duration.inWholeMilliseconds} ms")

            call.respond(oppfolgingstilfellerPersonsDTOs)
        }
    }
}
