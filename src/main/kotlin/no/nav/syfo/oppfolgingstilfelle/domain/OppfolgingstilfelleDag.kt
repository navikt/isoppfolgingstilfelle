package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag
import no.nav.syfo.util.and
import no.nav.syfo.util.or
import java.time.OffsetDateTime

class OppfolgingstilfelleDag(
    val dag: OffsetDateTime,
    val priorityOppfolgingstilfelleBit: OppfolgingstilfelleBit?,
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
            val newOppfolgingstilfelle = Oppfolgingstilfelle(
                personIdentNumber = this.mapNotNull { dag ->
                    dag.priorityOppfolgingstilfelleBit?.personIdentNumber
                }.first(),
                start = oppfolgingstilfelleSykedagList.first().dag,
                end = oppfolgingstilfelleSykedagList.last().dag,
                virksomhetsnummerList = emptyList(),
            )
            oppfolgingstilfelleList.add(newOppfolgingstilfelle)

            // Reset variables
            oppfolgingstilfelleSykedagList = ArrayList()
            notSykedagSinceLastSykedagCounter = 0
        }
    }

    if (oppfolgingstilfelleSykedagList.isNotEmpty()) {
        val virksomhetsnummerList: List<Virksomhetsnummer> = oppfolgingstilfelleSykedagList.mapNotNull {
            it.priorityOppfolgingstilfelleBit?.virksomhetsnummer
        }.distinct().map { virksomhetsnummer ->
            Virksomhetsnummer(virksomhetsnummer)
        }
        val lastOppfolgingstilfelle = Oppfolgingstilfelle(
            personIdentNumber = this.mapNotNull { dag -> dag.priorityOppfolgingstilfelleBit?.personIdentNumber }
                .first(),
            start = oppfolgingstilfelleSykedagList.first().dag,
            end = oppfolgingstilfelleSykedagList.last().dag,
            virksomhetsnummerList = virksomhetsnummerList,
        )
        oppfolgingstilfelleList.add(lastOppfolgingstilfelle)
    }

    return oppfolgingstilfelleList
}

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
