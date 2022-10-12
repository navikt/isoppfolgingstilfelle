package no.nav.syfo.narmesteleder.api

import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.oppfolgingstilfelle.person.database.createOppfolgingstilfellePerson
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.generator.*
import java.util.*

class OppfolgingstilfelleNarmesteLederApiSpek : Spek({
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val personIdentDefault = UserConstants.PERSONIDENTNUMBER_DEFAULT

        val oppfolgingstilfellePerson = generateOppfolgingstilfellePerson()

        beforeEachTest {
            database.dropData()
            database.connection.createOppfolgingstilfellePerson(true, oppfolgingstilfellePerson)
        }

        describe(OppfolgingstilfelleNarmesteLederApiSpek::class.java.simpleName) {
            describe("$oppfolgingstilfelleApiV1Path") {
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.tokenx.clientId,
                    issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
                )

                describe("happy path") {
                    it("GET base path") {
                        with(
                            handleRequest(HttpMethod.Get, oppfolgingstilfelleApiV1Path) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                                addHeader(NAV_VIRKSOMHETSNUMMER, UserConstants.VIRKSOMHETSNUMMER_DEFAULT.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                        }
                    }
                }

                describe("unhappy path") {
                    it("GET base path with invalid narmeste leder relasjon returns status code forbidden") {
                        with(
                            handleRequest(HttpMethod.Get, oppfolgingstilfelleApiV1Path) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdentDefault.value)
                                addHeader(
                                    NAV_VIRKSOMHETSNUMMER, "912000000"
                                )
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Forbidden
                        }
                    }
                }
            }
        }
    }
})
