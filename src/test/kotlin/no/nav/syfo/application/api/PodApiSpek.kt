package no.nav.syfo.application.api

import io.ktor.http.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.application.ApplicationState
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object PodApiSpek : Spek({

    describe("Successful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(
                    ApplicationState(
                        alive = true,
                        ready = true,
                    ),
                )
            }

            it("Returns true if pod is alive") {
                with(handleRequest(HttpMethod.Get, podLivenessPath)) {
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
            it("Returns true if pod is ready") {
                with(handleRequest(HttpMethod.Get, podReadinessPath)) {
                    println(response.status())
                    response.status()?.isSuccess() shouldBeEqualTo true
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }

    describe("Unsuccessful liveness and readiness checks") {
        with(TestApplicationEngine()) {
            start()
            application.routing {
                registerPodApi(
                    ApplicationState(
                        alive = false,
                        ready = false,
                    ),
                )
            }

            it("Returns internal server error when liveness check fails") {
                with(handleRequest(HttpMethod.Get, podLivenessPath)) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }

            it("Returns internal server error when readiness check fails") {
                with(handleRequest(HttpMethod.Get, podReadinessPath)) {
                    response.status() shouldBeEqualTo HttpStatusCode.InternalServerError
                    response.content shouldNotBeEqualTo null
                }
            }
        }
    }
})
