package no.nav.syfo.client.pdl

import java.io.Serializable

data class PdlPersonidentIdenterCache(
    val personIdentList: List<String>,
) : Serializable
