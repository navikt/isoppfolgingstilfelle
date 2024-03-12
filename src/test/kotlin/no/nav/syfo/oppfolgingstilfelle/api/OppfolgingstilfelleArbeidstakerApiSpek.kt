package no.nav.syfo.oppfolgingstilfelle.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.oppfolgingstilfelleArbeidstakerApiV1Path
import no.nav.syfo.oppfolgingstilfelle.person.database.createOppfolgingstilfellePerson
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.generator.*

class OppfolgingstilfelleArbeidstakerApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()
    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

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
        val validTokenOtherAzp = generateJWT(
            audience = externalMockEnvironment.environment.tokenx.clientId,
            azp = testIsnarmesteLederClientId,
            issuer = externalMockEnvironment.wellKnownSelvbetjening.issuer,
            pid = personIdent.value,
        )

        beforeEachTest {
            database.dropData()
            database.connection.use {
                it.createOppfolgingstilfellePerson(true, oppfolgingstilfellePerson)
            }
        }

        describe(OppfolgingstilfelleArbeidstakerApiSpek::class.java.simpleName) {
            describe("$oppfolgingstilfelleArbeidstakerApiV1Path") {

                describe("happy path") {
                    it("GET base path") {
                        with(
                            handleRequest(HttpMethod.Get, oppfolgingstilfelleArbeidstakerApiV1Path) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val content = objectMapper.readValue<List<OppfolgingstilfelleDTO>>(response.content!!)
                            content.size shouldBeEqualTo 1
                            val oppfolgingstilfelleDTO = content[0]
                            oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfellePerson.oppfolgingstilfelleList[0].start
                        }
                    }
                    it("GET base path, no oppfolgingstilfelle") {
                        with(
                            handleRequest(HttpMethod.Get, oppfolgingstilfelleArbeidstakerApiV1Path) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenOther))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK
                            val content = objectMapper.readValue<List<OppfolgingstilfelleDTO>>(response.content!!)
                            content.size shouldBeEqualTo 0
                        }
                    }
                }
                describe("unhappy path") {
                    it("wrong azp") {
                        with(
                            handleRequest(HttpMethod.Get, oppfolgingstilfelleArbeidstakerApiV1Path) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validTokenOtherAzp))
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
