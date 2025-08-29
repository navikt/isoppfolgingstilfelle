package no.nav.syfo.api.access

data class PreAuthorizedClient(
    val name: String,
    val clientId: String,
)

fun PreAuthorizedClient.toNamespaceAndApplicationName(): NamespaceAndApplicationName {
    val split = name.split(":")
    return NamespaceAndApplicationName(
        namespace = split[1],
        applicationName = split[2]
    )
}

data class NamespaceAndApplicationName(
    val namespace: String,
    val applicationName: String,
)
