package no.nav.syfo.infrastructure.client.pdl

data class PdlHentIdenterRequest(
    val query: String,
    val variables: PdlHentIdenterRequestVariables,
)

data class PdlHentIdenterRequestVariables(
    val ident: String,
    val historikk: Boolean,
    val grupper: List<String>,
)
