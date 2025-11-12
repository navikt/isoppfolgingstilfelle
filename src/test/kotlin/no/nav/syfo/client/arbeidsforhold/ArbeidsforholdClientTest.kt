package no.nav.syfo.client.arbeidsforhold

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.infrastructure.client.ArbeidsforholdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdClient
import no.nav.syfo.infrastructure.client.azuread.AzureAdToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants
import java.time.LocalDateTime

class ArbeidsforholdClientTest {

    private val anyToken = "token"
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val azureAdClientMock = mockk<AzureAdClient>()
    private val client = ArbeidsforholdClient(
        azureAdClient = azureAdClientMock,
        clientEnvironment = externalMockEnvironment.environment.clients.arbeidsforhold,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    @BeforeEach
    fun beforeEach() {
        coEvery {
            azureAdClientMock.getSystemToken(
                externalMockEnvironment.environment.clients.arbeidsforhold.clientId,
            )
        } returns AzureAdToken(
            accessToken = anyToken,
            expires = LocalDateTime.now().plusDays(1)
        )
    }

    @Test
    fun arbeidsforhold() {
        runBlocking {
            val response = client.getArbeidsforhold(
                personIdent = UserConstants.ARBEIDSTAKER_FNR,
            )
            assertEquals(1, response.size)
            assertEquals(
                UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
                response[0].arbeidssted.getOrgnummer()
            )
        }
    }

    @Test
    fun `no arbeidsforhold`() {
        runBlocking {
            val response = client.getArbeidsforhold(
                personIdent = UserConstants.ARBEIDSTAKER_VIRKSOMHET_NO_NARMESTELEDER,
            )
            assertEquals(0, response.size)
        }
    }

    @Test
    fun `unknown arbeidstaker`() {
        runBlocking {
            val response = client.getArbeidsforhold(
                personIdent = UserConstants.ARBEIDSTAKER_UNKNOWN_AAREG,
            )
            assertEquals(0, response.size)
        }
    }
}
