package testhelper.mock

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
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
                            Tilgang(false, "Ingen tilgang")
                        )
                        else -> call.respond(Tilgang(true, ""))
                    }
                }
            }
        }
    }
}
