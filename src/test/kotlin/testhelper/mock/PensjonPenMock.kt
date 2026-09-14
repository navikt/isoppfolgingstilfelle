package testhelper.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.client.pensjonpen.PensjonPenClient.Companion.FNR_HEADER
import no.nav.syfo.infrastructure.client.pensjonpen.Uforegrad
import testhelper.UserConstants.ARBEIDSTAKER_UFOR

fun MockRequestHandleScope.pensjonPenMockResponse(request: HttpRequestData): HttpResponseData = when {
    request.headers[FNR_HEADER] == ARBEIDSTAKER_UFOR.value -> {
        respondOk(Uforegrad(uforegrad = 100))
    }
    else -> {
        respondOk(Uforegrad(uforegrad = null))
    }
}
