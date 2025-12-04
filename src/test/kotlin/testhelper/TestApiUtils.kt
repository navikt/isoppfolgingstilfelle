package testhelper

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import no.nav.syfo.api.endpoints.OppfolgingstilfellePersonDTO
import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.util.NAV_PERSONIDENT_HEADER
import no.nav.syfo.util.configure

fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
    application {
        testApiModule(
            externalMockEnvironment = ExternalMockEnvironment.instance,
        )
    }
    val client = createClient {
        install(ContentNegotiation) {
            jackson { configure() }
        }
    }

    return client
}

suspend fun HttpClient.getOppfolgingstilfellePerson(
    url: String,
    token: String,
    personIdent: PersonIdentNumber,
): OppfolgingstilfellePersonDTO {
    val response = this.get(url) {
        bearerAuth(token)
        header(NAV_PERSONIDENT_HEADER, personIdent.value)
    }
    return response.body<OppfolgingstilfellePersonDTO>()
}
