package testhelper.mock

import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.client.arbeidsforhold.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import testhelper.UserConstants
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import testhelper.getRandomPort
import java.time.*

val arbeidsforhold = AaregArbeidsforhold(
    navArbeidsforholdId = 1,
    arbeidssted = Arbeidssted(
        type = ArbeidsstedType.Person,
        identer = listOf(
            Ident(
                type = IdentType.ORGANISASJONSNUMMER,
                ident = UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
                gjeldende = true,
            ),
        ),
    ),
    opplysningspliktig = Opplysningspliktig(
        identer = listOf(
            Ident(
                type = IdentType.ORGANISASJONSNUMMER,
                ident = UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
                gjeldende = true,
            )
        ),
    ),
    ansettelsesperiode = Ansettelsesperiode(
        startdato = LocalDate.now().minusYears(1),
        sluttdato = null,
    )
)

class ArbeidsforholdMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "arbeidsforhold"
    val server = mockArbeidsforholdServer(port)

    private fun mockArbeidsforholdServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get(ArbeidsforholdClient.ARBEIDSFORHOLD_PATH) {
                    if (call.request.headers[NAV_PERSONIDENT_HEADER] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                        call.respond(emptyList<AaregArbeidsforhold>())
                    } else {
                        call.respond(
                            listOf(
                                arbeidsforhold,
                            )
                        )
                    }
                }
            }
        }
    }
}
