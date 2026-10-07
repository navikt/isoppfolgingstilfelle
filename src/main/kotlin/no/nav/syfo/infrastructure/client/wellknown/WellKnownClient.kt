package no.nav.syfo.infrastructure.client.wellknown

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking

fun getWellKnown(
    wellKnownUrl: String,
    httpClient: HttpClient,
): WellKnown = runBlocking {
    httpClient.get(wellKnownUrl).body<WellKnownDTO>().toWellKnown()
}
