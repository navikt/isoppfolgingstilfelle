package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.api.endpoints.OppfolgingstilfelleDTO
import no.nav.syfo.api.endpoints.oppfolgingstilfelleArbeidstakerApiV1Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelper.*
import testhelper.generator.generateOppfolgingstilfellePerson

class OppfolgingstilfelleArbeidstakerApiTest {

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    private val personIdent = UserConstants.ARBEIDSTAKER_FNR

    private val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
        personIdent = personIdent,
    )
    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.tokenx.clientId,
        azp = testMeroppfolgingBackendClientId,
        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
        pid = personIdent.value,
    )
    private val validTokenOther = generateJWT(
        audience = externalMockEnvironment.environment.tokenx.clientId,
        azp = testMeroppfolgingBackendClientId,
        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
        pid = UserConstants.ARBEIDSTAKER_2_FNR.value,
    )

    @BeforeEach
    fun beforeEach() {
        database.dropData()
        oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(oppfolgingstilfellePerson)
    }

    @Test
    fun `GET base path`() {
        testApplication {
            val client = setupApiAndClient()
            val response = client.get(oppfolgingstilfelleArbeidstakerApiV1Path) {
                bearerAuth(validToken)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.body<List<OppfolgingstilfelleDTO>>()
            assertEquals(1, content.size)
            val oppfolgingstilfelleDTO = content[0]
            assertEquals(oppfolgingstilfellePerson.oppfolgingstilfelleList[0].start, oppfolgingstilfelleDTO.start)
        }
    }

    @Test
    fun `GET base path, no oppfolgingstilfelle`() {
        testApplication {
            val client = setupApiAndClient()
            val response = client.get(oppfolgingstilfelleArbeidstakerApiV1Path) {
                bearerAuth(validTokenOther)
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val content = response.body<List<OppfolgingstilfelleDTO>>()
            assertEquals(0, content.size)
        }
    }
}
