package no.nav.syfo.client.arbeidsforhold

import java.time.LocalDate

data class AaregArbeidsforhold(
    val navArbeidsforholdId: Int,
    val arbeidssted: Arbeidssted,
    val opplysningspliktig: Opplysningspliktig,
    val ansettelsesperiode: Ansettelsesperiode
)

data class Arbeidssted(
    val type: ArbeidsstedType,
    val identer: List<Ident>
) {
    fun getOrgnummer(): String {
        return identer.first {
            it.type == IdentType.ORGANISASJONSNUMMER
        }.ident
    }
}

data class Opplysningspliktig(
    val identer: List<Ident>
) {
    fun getJuridiskOrgnummer(): String {
        return identer.first {
            it.type == IdentType.ORGANISASJONSNUMMER
        }.ident
    }
}

data class Ident(
    val type: IdentType,
    val ident: String,
    val gjeldende: Boolean
)

data class Ansettelsesperiode(
    val startdato: LocalDate,
    val sluttdato: LocalDate?
)

enum class ArbeidsstedType {
    Underenhet, Person
}

enum class IdentType {
    AKTORID, FOLKEREGISTERIDENT, ORGANISASJONSNUMMER
}
