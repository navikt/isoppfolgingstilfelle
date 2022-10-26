package no.nav.syfo.application.api.access

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.application.api.authentication.getConsumerClientId
import no.nav.syfo.util.configuredJacksonMapper

class APIConsumerAccessService(
    azureAppPreAuthorizedApps: String
) {
    private val preAuthorizedClientList: List<PreAuthorizedClient> = configuredJacksonMapper()
        .readValue(azureAppPreAuthorizedApps)

    fun validateConsumerApplicationAZP(
        token: String,
        authorizedApplicationNameList: List<String>,
    ) {
        val consumerClientIdAzp: String = getConsumerClientId(token = token)
        val clientIdList = preAuthorizedClientList
            .filter {
                authorizedApplicationNameList.contains(
                    it.toNamespaceAndApplicationName().applicationName
                )
            }
            .map { it.clientId }
        if (!clientIdList.contains(consumerClientIdAzp)) {
            throw ForbiddenAccessSystemConsumer(consumerClientIdAzp = consumerClientIdAzp)
        }
    }
}
