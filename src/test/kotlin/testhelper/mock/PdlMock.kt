package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.infrastructure.client.pdl.*
import testhelper.UserConstants
import java.time.LocalDate

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
                PdlIdent(
                    ident = UserConstants.ARBEIDSTAKER_AKTOR_ID,
                    historisk = false,
                    gruppe = IdentType.AKTORID.name,
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

fun generatePdlPersonResponse(
    foedselsdato: LocalDate = LocalDate.now().minusYears(30),
    forenkletStatus: String = ForenkletFolkeregisterStatus.bosattEtterFolkeregisterloven.name,
    statsborgerskap: List<String> = listOf("NOR"),
) = PdlPersonResponse(
    data = PdlHentPerson(
        hentPerson = PdlPerson(
            foedselsdato = listOf(PdlFoedselsdato(foedselsdato = foedselsdato)),
            folkeregisterpersonstatus = listOf(PdlFolkeregisterpersonstatus(forenkletStatus = forenkletStatus)),
            statsborgerskap = statsborgerskap.map { PdlStatsborgerskap(land = it) },
        ),
    ),
    errors = null,
)

private data class PdlMockRequest(
    val query: String,
)

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    if (request.receiveBody<PdlMockRequest>().query.contains("hentPerson")) {
        return pdlHentPersonMockResponse(request)
    }
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

private suspend fun MockRequestHandleScope.pdlHentPersonMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentPersonRequest>()
    return when (PersonIdentNumber(pdlRequest.variables.ident)) {
        UserConstants.ARBEIDSTAKER_UNDER_18 -> respondOk(
            generatePdlPersonResponse(foedselsdato = LocalDate.now().minusYears(18).plusDays(1))
        )
        UserConstants.ARBEIDSTAKER_IKKE_BOSATT -> respondOk(
            generatePdlPersonResponse(forenkletStatus = ForenkletFolkeregisterStatus.ikkeBosatt.name)
        )
        UserConstants.ARBEIDSTAKER_WITH_ERROR -> respondOk(
            generatePdlPersonResponse().copy(errors = generatePdlError(code = "not_found"))
        )
        else -> respondOk(generatePdlPersonResponse())
    }
}
