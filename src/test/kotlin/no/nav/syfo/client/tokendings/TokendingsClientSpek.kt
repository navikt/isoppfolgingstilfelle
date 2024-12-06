package no.nav.syfo.client.tokendings

import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment

class TokendingsClientSpek : Spek({
    val anyToken = "anyToken"
    val anyClientId = "anyClientId"

    describe(TokendingsClientSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val client = TokendingsClient(
            tokenxClientId = anyClientId,
            tokenxEndpoint = externalMockEnvironment.environment.tokenx.endpoint,
            tokenxPrivateJWK = externalMockEnvironment.environment.tokenx.privateJWK,
            httpClient = externalMockEnvironment.mockHttpClient,
        )

        it("get OBO token should fetch token and cache it") {
            runBlocking {
                val res1 = client.getOnBehalfOfToken(
                    scopeClientId = anyClientId,
                    token = anyToken,
                )

                val res2 = client.getOnBehalfOfToken(
                    scopeClientId = anyClientId,
                    token = anyToken,
                )

                res2 shouldBeEqualTo res1
            }
        }
    }
})
