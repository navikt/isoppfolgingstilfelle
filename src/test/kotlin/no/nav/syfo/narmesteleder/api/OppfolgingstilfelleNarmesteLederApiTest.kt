package no.nav.syfo.narmesteleder.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.NAV_VIRKSOMHETSNUMMER
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import testhelper.*
import testhelper.generator.generateOppfolgingstilfellePerson

class OppfolgingstilfelleNarmesteLederApiTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository

    private val personIdentDefault = UserConstants.PERSONIDENTNUMBER_DEFAULT

    private val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson()
    private val narmestelederOppfolgingstilfelleApiPath = "/api/v1/narmesteleder/oppfolgingstilfelle"

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        database.connection.use {
            oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(
                connection = it,
                true,
                oppfolgingstilfellePerson
            )
        }
    }

    @Nested
    @DisplayName("Happy path")
    inner class HappyPath {
        @Test
        fun `GET base path`() {
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.tokenx.clientId,
                azp = testIsnarmesteLederClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            )

            testApplication {
                val client = setupApiAndClient()
                val response = client.get(narmestelederOppfolgingstilfelleApiPath) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                    header(NAV_VIRKSOMHETSNUMMER, UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                }

                assertEquals(HttpStatusCode.OK, response.status)
                val content = response.body<List<OppfolgingstilfelleDTO>>()
                assertEquals(1, content.size)
                val oppfolgingstilfelleDTO = content[0]
                assertEquals(oppfolgingstilfellePerson.oppfolgingstilfelleList[0].start, oppfolgingstilfelleDTO.start)
            }
        }
    }

    @Nested
    @DisplayName("Unhappy path")
    inner class UnhappyPath {
        @Test
        fun `GET base path with invalid narmeste leder relasjon returns status code forbidden`() {
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.tokenx.clientId,
                azp = testIsnarmesteLederClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            )

            testApplication {
                val client = setupApiAndClient()
                val response = client.get(narmestelederOppfolgingstilfelleApiPath) {
                    bearerAuth(validToken)
                    header(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                    header(NAV_VIRKSOMHETSNUMMER, "912000000")
                }

                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }
    }
}
