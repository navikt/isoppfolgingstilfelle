package no.nav.syfo.narmesteleder.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.NAV_VIRKSOMHETSNUMMER
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.generator.generateOppfolgingstilfellePerson

class OppfolgingstilfelleNarmesteLederApiSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database
    val oppfolgingstilfelleRepository = externalMockEnvironment.oppfolgingstilfellePersonRepository

    val personIdentDefault = UserConstants.PERSONIDENTNUMBER_DEFAULT

    val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson()
    val narmestelederOppfolgingstilfelleApiPath = "/api/v1/narmesteleder/oppfolgingstilfelle"

    beforeEachTest {
        database.dropData()
        database.connection.use {
            oppfolgingstilfelleRepository.createOppfolgingstilfellePerson(connection = it, true, oppfolgingstilfellePerson)
        }
    }

    describe(OppfolgingstilfelleNarmesteLederApiSpek::class.java.simpleName) {
        describe(narmestelederOppfolgingstilfelleApiPath) {
            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.tokenx.clientId,
                azp = testIsnarmesteLederClientId,
                issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            )

            describe("happy path") {
                it("GET base path") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(narmestelederOppfolgingstilfelleApiPath) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            header(NAV_VIRKSOMHETSNUMMER, UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                        }

                        response.status shouldBeEqualTo HttpStatusCode.OK
                        val content = response.body<List<OppfolgingstilfelleDTO>>()
                        content.size shouldBeEqualTo 1
                        val oppfolgingstilfelleDTO = content[0]
                        oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfellePerson.oppfolgingstilfelleList[0].start
                    }
                }
            }

            describe("unhappy path") {
                it("GET base path with invalid narmeste leder relasjon returns status code forbidden") {
                    testApplication {
                        val client = setupApiAndClient()
                        val response = client.get(narmestelederOppfolgingstilfelleApiPath) {
                            bearerAuth(validToken)
                            header(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                            header(NAV_VIRKSOMHETSNUMMER, "912000000")
                        }

                        response.status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
        }
    }
})
