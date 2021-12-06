package no.nav.syfo.oppfolgingstilfelle.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag
import no.nav.syfo.oppfolgingstilfelle.bit.database.createOppfolgingstilfelleBit
import no.nav.syfo.util.*
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import testhelper.*
import testhelper.UserConstants.ARBEIDSTAKER_PERSONIDENTNUMBER
import testhelper.UserConstants.VIRKSOMHETSNUMMER_DEFAULT
import java.time.OffsetDateTime
import java.util.*

class OppfolgingstilfelleApiSpek : Spek({
    val objectMapper: ObjectMapper = configuredJacksonMapper()

    with(TestApplicationEngine()) {
        start()

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        application.testApiModule(
            externalMockEnvironment = externalMockEnvironment,
        )

        val oppfolgingstilfelleBit = OppfolgingstilfelleBit(
            uuid = UUID.randomUUID(),
            personIdentNumber = ARBEIDSTAKER_PERSONIDENTNUMBER,
            virksomhetsnummer = VIRKSOMHETSNUMMER_DEFAULT.value,
            createdAt = OffsetDateTime.now(),
            inntruffet = OffsetDateTime.now().minusDays(1),
            fom = OffsetDateTime.now().minusDays(1),
            tom = OffsetDateTime.now().plusDays(1),
            tagList = listOf(
                Tag.SYKEPENGESOKNAD,
                Tag.SENDT,
            ),
            ressursId = UUID.randomUUID().toString(),
        )

        describe(OppfolgingstilfelleApiSpek::class.java.simpleName) {
            describe("Get OppfolgingstilfellePersonDTO for PersonIdent") {
                val url = "$oppfolgingstilfelleApiV1Path$oppfolgingstilfelleApiPersonIdentPath"
                val validToken = generateJWT(
                    audience = externalMockEnvironment.environment.azureAppClientId,
                    issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                )

                describe("Happy path") {

                    it("should return list of OppfolgingstilfelleDTO if request is successful") {
                        database.connection.use {
                            it.createOppfolgingstilfelleBit(
                                commit = true,
                                oppfolgingstilfelleBit = oppfolgingstilfelleBit,
                            )
                        }

                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val oppfolgingstilfellePersonDTO: OppfolgingstilfellePersonDTO =
                                objectMapper.readValue(response.content!!)

                            oppfolgingstilfellePersonDTO.personIdent shouldBeEqualTo oppfolgingstilfelleBit.personIdentNumber.value

                            val oppfolgingstilfelleDTO = oppfolgingstilfellePersonDTO.oppfolgingstilfelleList.first()

                            oppfolgingstilfelleDTO.virksomhetsnummerList.size shouldBeEqualTo 1
                            oppfolgingstilfelleDTO.virksomhetsnummerList.first() shouldBeEqualTo oppfolgingstilfelleBit.virksomhetsnummer

                            oppfolgingstilfelleDTO.start shouldBeEqualTo oppfolgingstilfelleBit.fom.toLocalDateOslo()
                            oppfolgingstilfelleDTO.end shouldBeEqualTo oppfolgingstilfelleBit.tom.toLocalDateOslo()
                        }
                    }
                }

                describe("Unhappy paths") {
                    it("should return status Unauthorized if no token is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {}
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Unauthorized
                        }
                    }

                    it("should return status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }

                    it("should return status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        with(
                            handleRequest(HttpMethod.Get, url) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, ARBEIDSTAKER_PERSONIDENTNUMBER.value.drop(1))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                }
            }
        }
    }
})
