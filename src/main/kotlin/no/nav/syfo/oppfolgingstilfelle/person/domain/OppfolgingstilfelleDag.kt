package no.nav.syfo.oppfolgingstilfelle.person.domain

import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.bit.domain.*
import no.nav.syfo.oppfolgingstilfelle.person.metric.SYKMELDING_NY_COUNTER
import no.nav.syfo.util.and
import no.nav.syfo.util.or
import org.slf4j.LoggerFactory
import java.time.*

private val log = LoggerFactory.getLogger(OppfolgingstilfelleDag::class.java)

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

fun List<OppfolgingstilfelleDag>.isArbeidstakerAtTilfelleEnd(): Boolean {
    val last = findLastPriorityOppfolgingstilfelleBit()
    return if (this.any { it.priorityOppfolgingstilfelleBit?.tagList?.contains(Tag.BEKREFTET) == true } &&
        this.none { it.priorityOppfolgingstilfelleBit?.tagList?.contains(Tag.SENDT) == true } &&
        this.none { it.priorityOppfolgingstilfelleBit?.tagList?.contains(Tag.INNTEKTSMELDING) == true } &&
        last?.tagList?.contains(Tag.NY) == true) {
        false
    } else {
        last?.isArbeidstakerBit() ?: false
    }
}

private fun List<OppfolgingstilfelleDag>.findLastPriorityOppfolgingstilfelleBit() =
    this.last {
        it.priorityOppfolgingstilfelleBit != null
    }.priorityOppfolgingstilfelleBit

fun List<OppfolgingstilfelleDag>.gradertAtTilfelleEnd() =
    this.last {
        it.priorityOppfolgingstilfelleBit != null
    }.isGradert()

fun List<OppfolgingstilfelleDag>.toOppfolgingstilfelle(): Oppfolgingstilfelle {
    if (this.onlySykmeldingNyOrInntektsmelding() && this.durationDays() > 118) {
        val sampleUUID = this.firstNotNullOfOrNull { dag -> dag.priorityOppfolgingstilfelleBit }?.uuid
        log.info("Created oppfolgingstilfelle with duration>118 days based on only sykmelding-ny, bit sample uuid: $sampleUUID")
        SYKMELDING_NY_COUNTER.increment()
    }
    val arbeidstakerAtTilfelleEnd = this.isArbeidstakerAtTilfelleEnd()
    return Oppfolgingstilfelle(
        arbeidstakerAtTilfelleEnd = arbeidstakerAtTilfelleEnd,
        start = this.first().dag,
        end = this.last().dag,
        virksomhetsnummerList = this.toVirksomhetsnummerPreferred().ifEmpty {
            if (arbeidstakerAtTilfelleEnd) this.toVirksomhetsnummerAll() else emptyList()
        },
        gradertAtTilfelleEnd = this.gradertAtTilfelleEnd(),
    )
}

private fun List<OppfolgingstilfelleDag>.onlySykmeldingNyOrInntektsmelding() =
    this.all { dag ->
        dag.priorityOppfolgingstilfelleBit?.tagList
            ?.let { tagList ->
                tagList in ((Tag.SYKMELDING and Tag.NY) or (Tag.INNTEKTSMELDING and Tag.ARBEIDSGIVERPERIODE))
            }
            ?: false
    }

private fun List<OppfolgingstilfelleDag>.durationDays(): Long {
    val start = this.first().dag.atStartOfDay()
    val end = this.last().dag.atStartOfDay()
    return Duration.between(start, end).toDays()
}

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
                    or (Tag.SYKMELDING and Tag.BEHANDLINGSDAGER)
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
