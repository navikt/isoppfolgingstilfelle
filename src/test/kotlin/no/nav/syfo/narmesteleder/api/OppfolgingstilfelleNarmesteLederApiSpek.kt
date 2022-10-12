package no.nav.syfo.narmesteleder.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.person.database.createOppfolgingstilfellePerson
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.generator.*

class OppfolgingstilfelleNarmesteLederApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
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
                            val content = objectMapper.readValue<List<OppfolgingstilfelleDTO>>(response.content!!)
                            content.size shouldBeEqualTo 1
                            val oppfolgingstilfelleDTO = content[0]
                            oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfellePerson.oppfolgingstilfelleList[0].start
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
