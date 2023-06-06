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

class SyfoTilgangskontrollMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"

    val name = "syfotilgangskontroll"
    val server = mockSyfotilgangskontrollServer(
        port
    )

    private fun mockSyfotilgangskontrollServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port,
        ) {
            installContentNegotiation()
            routing {
                get(VeilederTilgangskontrollClient.TILGANGSKONTROLL_PERSON_PATH) {
                    when (personIdentHeader()) {
                        PERSONIDENTNUMBER_VEILEDER_NO_ACCESS.value -> call.respond(
                            Tilgang(harTilgang = false)
                        )

                        else -> call.respond(
                            Tilgang(harTilgang = true)
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
