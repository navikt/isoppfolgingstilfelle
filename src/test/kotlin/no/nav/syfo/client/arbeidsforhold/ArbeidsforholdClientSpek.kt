package no.nav.syfo.client.arbeidsforhold

import io.ktor.server.testing.TestApplicationEngine
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.azuread.AzureAdClient
import no.nav.syfo.client.azuread.AzureAdToken
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import java.time.LocalDateTime

class ArbeidsforholdClientSpek : Spek({

    val anyToken = "token"

    describe(ArbeidsforholdClientSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val azureAdClientMock = mockk<AzureAdClient>()
            val client = ArbeidsforholdClient(
                azureAdClient = azureAdClientMock,
                clientEnvironment = externalMockEnvironment.environment.clients.arbeidsforhold,
            )
            coEvery {
                azureAdClientMock.getSystemToken(
                    externalMockEnvironment.environment.clients.arbeidsforhold.clientId,
                )
            } returns AzureAdToken(
                accessToken = anyToken,
                expires = LocalDateTime.now().plusDays(1)
            )
            it("arbeidsforhold") {
                runBlocking {
                    val response = client.getArbeidsforhold(
                        personIdent = UserConstants.ARBEIDSTAKER_FNR,
                    )
                    response.size shouldBeEqualTo 1
                    response[0].arbeidssted.getOrgnummer() shouldBeEqualTo
                        UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value
                }
            }
            it("no arbeidsforhold") {
                runBlocking {
                    val response = client.getArbeidsforhold(
                        personIdent = UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER,
                    )
                    response.size shouldBeEqualTo 0
                }
            }
            it("unknown arbeidstaker") {
                runBlocking {
                    val response = client.getArbeidsforhold(
                        personIdent = UserConstants.ARBEIDSTAKER_UNKNOWN_AAREG,
                    )
                    response.size shouldBeEqualTo 0
                }
            }
        }
    }
})
