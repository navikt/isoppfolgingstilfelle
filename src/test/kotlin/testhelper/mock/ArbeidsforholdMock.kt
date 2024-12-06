package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.client.arbeidsforhold.*
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import testhelper.UserConstants
import testhelper.UserConstants.ARBEIDSTAKER_UNKNOWN_AAREG
import testhelper.UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER
import java.time.LocalDate

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

fun MockRequestHandleScope.arbeidsforholdMockResponse(request: HttpRequestData): HttpResponseData = when {
    request.headers[NAV_PERSONIDENT_HEADER] == ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER.value -> {
        respondOk(emptyList<AaregArbeidsforhold>())
    }
    request.headers[NAV_PERSONIDENT_HEADER] == ARBEIDSTAKER_UNKNOWN_AAREG.value -> {
        respondError(HttpStatusCode.NotFound)
    }
    else -> {
        respondOk(
            listOf(
                arbeidsforhold,
            )
        )
    }
}
