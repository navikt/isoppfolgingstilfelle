package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.client.pdl.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import testhelper.UserConstants

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

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentIdenterRequest>()
    return when (val personIdentNumber = PersonIdentNumber(pdlRequest.variables.ident)) {
        UserConstants.ARBEIDSTAKER_3_FNR -> {
            respondOk(generatePdlIdenterResponse(PersonIdentNumber("11111111111")))
        }
        UserConstants.ARBEIDSTAKER_WITH_ERROR -> {
            respondOk(generatePdlIdenterResponse(personIdentNumber).copy(errors = generatePdlError(code = "not_found")))
        }
        else -> {
            respondOk(generatePdlIdenterResponse(personIdentNumber))
        }
    }
}
