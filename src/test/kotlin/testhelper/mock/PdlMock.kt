package testhelper.mock

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.application.api.installContentNegotiation
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import testhelper.UserConstants
import testhelper.getRandomPort

fun PersonIdentNumber.toHistoricalPersonIdentNumber(): PersonIdentNumber {
    val firstDigit = this.value[0].digitToInt()
    val newDigit = firstDigit + 4
    val dNummer = this.value.replace(
        firstDigit.toString(),
        newDigit.toString(),
    )
    return PersonIdentNumber(dNummer)
}

fun generatePdlIdenterResponse(
    personIdentNumber: PersonIdentNumber,
) = PdlIdenterResponse(
    data = PdlHentIdenter(
        hentIdenter = PdlIdenter(
            identer = listOf(
                PdlIdent(
                    ident = personIdentNumber.value,
                    historisk = false,
                    gruppe = IdentType.FOLKEREGISTERIDENT.name,
                ),
                PdlIdent(
                    ident = personIdentNumber.toHistoricalPersonIdentNumber().value,
                    historisk = true,
                    gruppe = IdentType.FOLKEREGISTERIDENT.name,
                ),
            ),
        ),
    ),
    errors = null,
)

fun generatePdlError(code: String? = null) = listOf(
    PdlError(
        message = "Error",
        locations = emptyList(),
        path = emptyList(),
        extensions = PdlErrorExtension(
            code = code,
            classification = "Classification",
        )
    )
)

class PdlMock {
    private val port = getRandomPort()
    val url = "http://localhost:$port"
    val name = "pdl"
    val server = embeddedServer(
        factory = Netty,
        port = port
    ) {
        installContentNegotiation()
        routing {
            post {
                val pdlRequest = call.receive<PdlHentIdenterRequest>()
                when (val personIdentNumber = PersonIdentNumber(pdlRequest.variables.ident)) {
                    UserConstants.ARBEIDSTAKER_3_FNR -> {
                        call.respond(generatePdlIdenterResponse(PersonIdentNumber("11111111111")))
                    }
                    UserConstants.ARBEIDSTAKER_WITH_ERROR -> {
                        call.respond(generatePdlIdenterResponse(personIdentNumber).copy(errors = generatePdlError(code = "not_found")))
                    }
                    else -> {
                        call.respond(generatePdlIdenterResponse(personIdentNumber))
                    }
                }
            }
        }
    }
}
