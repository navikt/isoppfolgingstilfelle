package testhelper.mock

import io.ktor.server.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.client.narmesteLeder.NarmesteLederClient
import no.nav.syfo.client.narmesteLeder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.narmesteLeder.NarmesteLederRelasjonStatus

import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import testhelper.UserConstants.ARBEIDSTAKER_FNR
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import testhelper.UserConstants.NARMESTELEDER_FNR
import testhelper.UserConstants.NARMESTELEDER_FNR_2
import testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import testhelper.UserConstants.PERSON_TLF
import testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import testhelper.getRandomPort
import java.time.*
import java.util.UUID

val narmesteLeder = NarmesteLederRelasjonDTO(
    uuid = UUID.randomUUID().toString(),
    arbeidstakerPersonIdentNumber = ARBEIDSTAKER_FNR.value,
    narmesteLederPersonIdentNumber = NARMESTELEDER_FNR.value,
    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
    virksomhetsnavn = "Virksomhetsnavn",
    narmesteLederEpost = "narmesteLederNavn@gmail.com",
    narmesteLederTelefonnummer = PERSON_TLF,
    aktivFom = LocalDate.now(),
    aktivTom = null,
    timestamp = LocalDateTime.now(),
    arbeidsgiverForskutterer = true,
    narmesteLederNavn = "narmesteLederNavn",
    status = NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name,
)

class NarmesteLederMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "narmesteleder"
    val server = mockNarmesteLederServer(port)

    private fun mockNarmesteLederServer(port: Int): NettyApplicationEngine {
        return embeddedServer(
            factory = Netty,
            port = port
        ) {
            installContentNegotiation()
            routing {
                get(NarmesteLederClient.CURRENT_NARMESTELEDER_PATH) {
                    if (call.request.headers[NAV_PERSONIDENT_HEADER] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(
                            listOf(
                                narmesteLeder,
                                narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                            )
                        )
                    }
                }
                get(NarmesteLederClient.NARMESTELEDERE_SELVBETJENING_PATH) {
                    if (call.request.headers[NAV_PERSONIDENT_HEADER] == NARMESTELEDER_FNR_2.value) {
                        call.respond(emptyList<NarmesteLederRelasjonDTO>())
                    } else {
                        call.respond(
                            listOf(
                                narmesteLeder,
                                narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                            )
                        )
                    }
                }
            }
        }
    }
}
