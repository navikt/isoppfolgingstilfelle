package no.nav.syfo.client.narmesteleder

import io.ktor.server.testing.TestApplicationEngine
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.narmesteLeder.NarmesteLederClient
import no.nav.syfo.client.narmesteLeder.NarmesteLederRelasjonDTO
import no.nav.syfo.client.narmesteLeder.NarmesteLederRelasjonStatus
import no.nav.syfo.client.tokendings.TokendingsClient
import no.nav.syfo.client.tokendings.TokenendingsToken
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.ExternalMockEnvironment
import testhelper.UserConstants.ARBEIDSTAKER_FNR
import testhelper.UserConstants.NARMESTELEDER_FNR
import testhelper.UserConstants.VIRKSOMHETSNUMMER_HAS_NARMESTELEDER
import java.time.*
import java.util.UUID

class NarmesteLederClientSpek : Spek({

    val mapper = configuredJacksonMapper()
    val anyToken = "token"
    val anyCallId = "callId"

    describe(NarmesteLederClientSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val tokendingsClientMock = mockk<TokendingsClient>()
            val cacheMock = mockk<RedisStore>()
            val client = NarmesteLederClient(
                narmesteLederBaseUrl = externalMockEnvironment.environment.clients.narmesteLeder.baseUrl,
                narmestelederClientId = externalMockEnvironment.environment.clients.narmesteLeder.clientId,
                tokendingsClient = tokendingsClientMock,
                redisStore = cacheMock,
            )
            val cacheKey =
                "${NarmesteLederClient.CACHE_NARMESTE_LEDER_AKTIVE_ANSATTE_KEY_PREFIX}${NARMESTELEDER_FNR.value}"
            val cachedValue = listOf(
                NarmesteLederRelasjonDTO(
                    uuid = UUID.randomUUID().toString(),
                    arbeidstakerPersonIdentNumber = ARBEIDSTAKER_FNR.value,
                    virksomhetsnummer = VIRKSOMHETSNUMMER_HAS_NARMESTELEDER.value,
                    virksomhetsnavn = "",
                    narmesteLederPersonIdentNumber = NARMESTELEDER_FNR.value,
                    narmesteLederTelefonnummer = "",
                    narmesteLederEpost = "",
                    aktivFom = LocalDate.now(),
                    aktivTom = null,
                    timestamp = LocalDateTime.now(),
                    narmesteLederNavn = "",
                    arbeidsgiverForskutterer = true,
                    status = NarmesteLederRelasjonStatus.INNMELDT_AKTIV.name,
                )
            )

            coEvery {
                tokendingsClientMock.getOnBehalfOfToken(
                    externalMockEnvironment.environment.clients.narmesteLeder.clientId,
                    anyToken
                )
            } returns TokenendingsToken(
                accessToken = anyToken,
                expires = LocalDateTime.now().plusDays(1)
            )

            beforeEachTest {
                clearMocks(cacheMock)
            }

            it("aktive ledere returns cached value") {
                every { cacheMock.objectMapper } returns mapper
                every { cacheMock.get(cacheKey) } returns mapper.writeValueAsString(cachedValue)

                runBlocking {
                    client.getAktiveAnsatte(
                        narmesteLederIdent = NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId,
                    ).size shouldBeEqualTo 1
                }
                verify(exactly = 1) { cacheMock.get(cacheKey) }
                verify(exactly = 0) { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>?, any()) }
            }

            it("aktive ledere when no cached value") {
                every { cacheMock.objectMapper } returns mapper
                every { cacheMock.get(cacheKey) } returns null
                justRun { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>, any()) }

                runBlocking {
                    client.getAktiveAnsatte(
                        narmesteLederIdent = NARMESTELEDER_FNR,
                        tokenx = anyToken,
                        callId = anyCallId,
                    ).size shouldBeEqualTo 2
                }
                verify(exactly = 1) { cacheMock.get(cacheKey) }
                verify(exactly = 1) { cacheMock.setObject(any(), any() as List<NarmesteLederRelasjonDTO>?, any()) }
            }
        }
    }
})
