package no.nav.syfo.client.pdl.domain

import no.nav.syfo.domain.PersonIdentNumber

data class PdlIdenterResponse(
    val data: PdlHentIdenter?,
    val errors: List<PdlError>?
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?,
)

data class PdlIdenter(
    val identer: List<PdlIdent>,
) {
    val aktivIdent: String? = identer.firstOrNull {
        it.gruppe == IdentType.FOLKEREGISTERIDENT.name && !it.historisk
    }?.ident

    fun identhendelseIsNotHistorisk(newIdent: String): Boolean {
        return identer.none { it.ident == newIdent && it.historisk }
    }
}

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
}

fun PdlIdenter.toPersonIdentNumberList(): List<PersonIdentNumber> =
    this.identer.filter {
        it.gruppe == IdentType.FOLKEREGISTERIDENT.name
    }.let { pdlIdentList ->
        pdlIdentList.map {
            PersonIdentNumber(it.ident)
        }
    }
