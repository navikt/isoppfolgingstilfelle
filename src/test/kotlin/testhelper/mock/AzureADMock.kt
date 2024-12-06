package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.azuread.AzureAdTokenResponse
import no.nav.syfo.client.wellknown.WellKnown
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

fun MockRequestHandleScope.azureAdMockResponse(): HttpResponseData = respondOk(
    AzureAdTokenResponse(
        access_token = "token",
        expires_in = 3600,
        token_type = "type",
    )
)
