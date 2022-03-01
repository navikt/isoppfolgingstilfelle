package no.nav.syfo.client.pdl

import java.io.Serializable

data class PdlPersonidentNameCache(
    val name: String,
    val personIdent: String,
) : Serializable
