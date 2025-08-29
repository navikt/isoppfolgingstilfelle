package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederClient
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonDTO
import no.nav.syfo.infrastructure.client.narmesteleder.NarmesteLederRelasjonStatus
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import testhelper.UserConstants.ARBEIDSTAKER_FNR
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import testhelper.UserConstants.NARMESTELEDER_FNR
import testhelper.UserConstants.NARMESTELEDER_FNR_2
import testhelper.UserConstants.OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import testhelper.UserConstants.PERSON_TLF
import testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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

fun MockRequestHandleScope.narmesteLederMockResponse(request: HttpRequestData): HttpResponseData {
    val requestUrl = request.url.encodedPath

    return when {
        requestUrl.endsWith(NarmesteLederClient.CURRENT_NARMESTELEDER_PATH) -> {
            if (request.headers[NAV_PERSONIDENT_HEADER] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value) {
                respondError(HttpStatusCode.NotFound)
            } else {
                respondOk(
                    listOf(
                        narmesteLeder,
                        narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                    )
                )
            }
        }
        requestUrl.endsWith(NarmesteLederClient.NARMESTELEDERE_SELVBETJENING_PATH) -> {
            if (request.headers[NAV_PERSONIDENT_HEADER] == NARMESTELEDER_FNR_2.value) {
                respondOk(emptyList<NarmesteLederRelasjonDTO>())
            } else {
                respondOk(
                    listOf(
                        narmesteLeder,
                        narmesteLeder.copy(virksomhetsnummer = OTHER_VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value),
                    )
                )
            }
        }
        else -> error("Unhandled path $requestUrl")
    }
}
