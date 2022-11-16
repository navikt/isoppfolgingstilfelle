package no.nav.syfo.oppfolgingstilfelle.person.domain

import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.util.and
import no.nav.syfo.util.or
import java.time.LocalDate

class OppfolgingstilfelleDag(
    val dag: LocalDate,
    val priorityOppfolgingstilfelleBit: OppfolgingstilfelleBit?,
    val virksomhetsnummerPreferred: List<String>,
    val virksomhetsnummerAll: List<String>,
)

fun List<OppfolgingstilfelleDag>.groupOppfolgingstilfelleList(): List<Oppfolgingstilfelle> {
    val oppfolgingstilfelleList = ArrayList<Oppfolgingstilfelle>()
    var oppfolgingstilfelleSykedagList = ArrayList<OppfolgingstilfelleDag>()
    var notSykedagSinceLastSykedagCounter = 0

    this.forEach {
        when {
            it.isArbeidsdag() -> {
                notSykedagSinceLastSykedagCounter++
            }

            it.isFeriedag() -> {
                if (notSykedagSinceLastSykedagCounter > 0) {
                    // Only count Feriedag if at least one Arbeidsdag since last Sykedag
                    notSykedagSinceLastSykedagCounter++
                }
            }
            else -> { // isSykedag
                oppfolgingstilfelleSykedagList.add(it)
                notSykedagSinceLastSykedagCounter = 0
            }
        }

        val noSykedagLast16days = notSykedagSinceLastSykedagCounter >= 16 && oppfolgingstilfelleSykedagList.isNotEmpty()
        if (noSykedagLast16days) {
            val newOppfolgingstilfelle = oppfolgingstilfelleSykedagList.toOppfolgingstilfelle()
            oppfolgingstilfelleList.add(newOppfolgingstilfelle)

            // Reset variables
            oppfolgingstilfelleSykedagList = ArrayList()
            notSykedagSinceLastSykedagCounter = 0
        }
    }

    if (oppfolgingstilfelleSykedagList.isNotEmpty()) {
        val lastOppfolgingstilfelle = oppfolgingstilfelleSykedagList.toOppfolgingstilfelle()
        oppfolgingstilfelleList.add(lastOppfolgingstilfelle)
    }

    return oppfolgingstilfelleList
}

fun List<OppfolgingstilfelleDag>.isArbeidstakerAtTilfelleEnd() =
    this.last {
        it.priorityOppfolgingstilfelleBit != null
    }.priorityOppfolgingstilfelleBit?.isArbeidstakerBit() ?: false

fun List<OppfolgingstilfelleDag>.gradertAtTilfelleEnd() =
    this.last {
        it.priorityOppfolgingstilfelleBit != null
    }.isGradert()

fun List<OppfolgingstilfelleDag>.toOppfolgingstilfelle() =
    Oppfolgingstilfelle(
        arbeidstakerAtTilfelleEnd = this.isArbeidstakerAtTilfelleEnd(),
        start = this.first().dag,
        end = this.last().dag,
        virksomhetsnummerList = this.toVirksomhetsnummerPreferred().ifEmpty { this.toVirksomhetsnummerAll() },
        gradertAtTilfelleEnd = this.gradertAtTilfelleEnd(),
    )

fun List<OppfolgingstilfelleDag>.toVirksomhetsnummerPreferred() =
    this.map {
        it.virksomhetsnummerPreferred
    }.flatten().distinct().map { virksomhetsnummer ->
        Virksomhetsnummer(virksomhetsnummer)
    }

fun List<OppfolgingstilfelleDag>.toVirksomhetsnummerAll() =
    this.map {
        it.virksomhetsnummerAll
    }.flatten().distinct().map { virksomhetsnummer ->
        Virksomhetsnummer(virksomhetsnummer)
    }

fun OppfolgingstilfelleDag.isGradert() = priorityOppfolgingstilfelleBit?.isGradert() ?: false

fun OppfolgingstilfelleDag.isArbeidsdag() =
    priorityOppfolgingstilfelleBit
        ?.tagList
        ?.let { tagList ->
            tagList in (
                (Tag.SYKMELDING and Tag.PERIODE and Tag.FULL_AKTIVITET)
                    or (Tag.SYKEPENGESOKNAD and Tag.ARBEID_GJENNOPPTATT)
                    or (Tag.SYKEPENGESOKNAD and Tag.BEHANDLINGSDAGER)
                )
        }
        ?: true

fun OppfolgingstilfelleDag.isFeriedag() =
    priorityOppfolgingstilfelleBit
        ?.tagList
        ?.let { tagList ->
            tagList in (Tag.SYKEPENGESOKNAD and (Tag.FERIE or Tag.PERMISJON))
        }
        ?: false
