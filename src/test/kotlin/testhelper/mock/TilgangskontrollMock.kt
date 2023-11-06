package testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.client.veiledertilgang.Tilgang
import no.nav.syfo.client.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.util.personIdentHeader
import testhelper.UserConstants.PERSONIDENTNUMBER_VEILEDER_NO_ACCESS
import testhelper.getRandomPort

class TilgangskontrollMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val name = "istilgangskontroll"
    val server = mockTilgangskontroll(
        port
    )

    private fun mockTilgangskontroll(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port,
        ) {
            installContentNegotiation()
            routing {
                get(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_PATH) {
                    when (personIdentHeader()) {
                        PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value -> call.respond(
                            Tilgang(erGodkjent = false)
                        )

                        else -> call.respond(
                            Tilgang(erGodkjent = true)
                        )
                    }
                }
                post(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_LIST_PATH) {
                    val personidenter = call.receive<List<String>>()
                    call.respond(personidenter.filter { it != PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value })
                }
            }
        }
    }
}
