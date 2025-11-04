package no.nav.syfo.client.tokendings

import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.tokendings.TokendingsClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment

class TokendingsClientTest {

    private val anyToken = "anyToken"
    private val anyClientId = "anyClientId"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val client = TokendingsClient(
        tokenxClientId = anyClientId,
        tokenxEndpoint = externalMockEnvironment.environment.tokenx.endpoint,
        tokenxPrivateJWK = externalMockEnvironment.environment.tokenx.privateJWK,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    @Test
    fun `get OBO token should fetch token and cache it`() {
        runBlocking {
            val res1 = client.getOnBehalfOfToken(
                scopeClientId = anyClientId,
                token = anyToken,
            )

            val res2 = client.getOnBehalfOfToken(
                scopeClientId = anyClientId,
                token = anyToken,
            )

            assertEquals(res1, res2)
        }
    }
}
