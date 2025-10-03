package no.nav.syfo.oppfolgingstilfelle.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleArbeidstakerApiV1Path
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.generator.generateOppfolgingstilfellePerson

class OppfolgingstilfelleArbeidstakerApiSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository
    val personIdent = UserConstants.ARBEIDSTAKER_FNR

    val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson(
        personIdent = personIdent,
    )
    val validToken = generateJWT(
        audience = externalMockEnvironment.environment.tokenx.clientId,
        azp = testMeroppfolgingBackendClientId,
        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
        pid = personIdent.value,
    )
    val validTokenOther = generateJWT(
        audience = externalMockEnvironment.environment.tokenx.clientId,
        azp = testMeroppfolgingBackendClientId,
        issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
        pid = UserConstants.ARBEIDSTAKER_2_FNR.value,
    )

    beforeEachTest {
        database.dropData()
        database.connection.use {
            oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(it, true, oppfolgingstilfellePerson)
        }
    }

    describe(OppfolgingstilfelleArbeidstakerApiSpek::class.java.simpleName) {
        describe("$oppfolgingstilfelleArbeidstakerApiV1Path") {

            describe("happy path") {
                it("GET base path") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(oppfolgingstilfelleArbeidstakerApiV1Path) {
                            bearerAuth(validToken)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val content = response.body<List<OppfolgingstilfelleDTO>>()
                        content.size shouldBeEqualTo 1
                        val oppfolgingstilfelleDTO = content[0]
                        oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfellePerson.oppfolgingstilfelleList[0].start
                    }
                }
                it("GET base path, no oppfolgingstilfelle") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(oppfolgingstilfelleArbeidstakerApiV1Path) {
                            bearerAuth(validTokenOther)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val content = response.body<List<OppfolgingstilfelleDTO>>()
                        content.size shouldBeEqualTo 0
                    }
                }
            }
        }
    }
})
