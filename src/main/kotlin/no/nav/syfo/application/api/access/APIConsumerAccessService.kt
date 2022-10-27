package no.nav.syfo.application.api.access

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.api.authentication.getConsumerClientId
import no.nav.syfo.util.configuredJacksonMapper

class APIConsumerAccessService(
    azureAppPreAuthorizedApps: String
) {
    private val preAuthorizedClients: List<PreAuthorizedClient> = configuredJacksonMapper()
        .readValue(azureAppPreAuthorizedApps)

    fun validateConsumerApplicationAZP(
        token: String,
        authorizedApplicationNames: List<String>,
    ) {
        val consumerClientIdAzp: String = getConsumerClientId(token = token)
        val clientIds = preAuthorizedClients
            .filter {
                authorizedApplicationNames.contains(
                    it.toNamespaceAndApplicationName().applicationName
                )
            }
            .map { it.clientId }
        if (!clientIds.contains(consumerClientIdAzp)) {
            throw ForbiddenAccessSystemConsumer(consumerClientIdAzp = consumerClientIdAzp)
        }
    }
}
