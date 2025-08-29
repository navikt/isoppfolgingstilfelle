package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.tokendings.TokenendingsTokenDTO
import no.nav.syfo.infrastructure.client.wellknown.WellKnown
import java.nio.file.Paths
import java.util.*

fun wellKnownSelvbetjeningMock(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        jwksUri = uri.toString(),
        issuer = "https://sts.issuer.net/myid"
    )
}

fun MockRequestHandleScope.tokendingsMockResponse(): HttpResponseData = respondOk(
    TokenendingsTokenDTO(
        access_token = UUID.randomUUID().toString(),
        issued_token_type = "issued_token_type",
        token_type = "token_type",
        expires_in = 3600,
    )
)
