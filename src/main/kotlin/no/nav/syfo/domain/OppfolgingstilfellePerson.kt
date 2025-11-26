package no.nav.syfo.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.syfo.api.endpoints.OppfolgingstilfelleDTO
import no.nav.syfo.api.endpoints.OppfolgingstilfellePersonDTO
import no.nav.syfo.infrastructure.kafka.KafkaOppfolgingstilfelle
import no.nav.syfo.infrastructure.kafka.KafkaOppfolgingstilfellePerson
import no.nav.syfo.util.dagerMellomDatoer
import no.nav.syfo.util.isAfterOrEqual
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max

private const val DAYS_IN_WEEK = 7
private const val THREE_YEARS_IN_MONTHS: Long = 36
private const val MIN_DAYS_IN_LONG_TILFELLE = 3

data class OppfolgingstilfellePerson(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfelleList: List<Oppfolgingstilfelle>,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
    val dodsdato: LocalDate?,
    val hasGjentakendeSykefravar: Boolean?,
)

data class Oppfolgingstilfelle(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val gradertAtTilfelleEnd: Boolean, // True hvis alle biter på siste dag i tilfellet er gradert
    val start: LocalDate,
    val end: LocalDate,
    val antallSykedager: Int?, // må tillate null siden tidligere persisterte oppfolgingstilfeller vil mangle dette feltet
    val virksomhetsnummerList: List<Virksomhetsnummer>,
) {
    @JsonIgnore
    fun daysInTilfelle(): Int = antallSykedager ?: dagerMellomDatoer(start, end)

    @JsonIgnore
    fun isLongTilfelle(): Boolean = daysInTilfelle() >= MIN_DAYS_IN_LONG_TILFELLE

    @JsonIgnore
    fun isRecentTilfelle(): Boolean {
        val threeYearsAgo = LocalDate.now().minusMonths(THREE_YEARS_IN_MONTHS)
        return end.isAfterOrEqual(threeYearsAgo)
    }
}

fun OppfolgingstilfellePerson.toOppfolgingstilfellePersonDTO() = OppfolgingstilfellePersonDTO(
    oppfolgingstilfelleList = this.oppfolgingstilfelleList.toOppfolgingstilfelleDTOList(),
    personIdent = this.personIdentNumber.value,
    dodsdato = this.dodsdato,
    hasGjentakendeSykefravar = this.hasGjentakendeSykefravar,
)

fun List<Oppfolgingstilfelle>.toOppfolgingstilfelleDTOList() =
    this.map { oppfolgingstilfelle ->
        OppfolgingstilfelleDTO(
            arbeidstakerAtTilfelleEnd = oppfolgingstilfelle.arbeidstakerAtTilfelleEnd,
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            antallSykedager = oppfolgingstilfelle.antallSykedager,
            varighetUker = oppfolgingstilfelle.calculateCurrentVarighetUker(),
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { it.value },
        )
    }

fun List<Oppfolgingstilfelle>.hasGjentakendeSykefravar(): Boolean {
    val relevantTilfeller = this
        .filter { it.isLongTilfelle() }
        .filter { it.isRecentTilfelle() }

    val tilfelleCount = relevantTilfeller.size
    val antallSykedager = relevantTilfeller.sumOf { it.daysInTilfelle() }

    return hasManySykefravar(tilfelleCount, antallSykedager) || hasLongSykefravar(
        tilfelleCount,
        antallSykedager
    )
}

private fun hasManySykefravar(tilfeller: Int, sykedager: Int): Boolean {
    return tilfeller >= 5 && sykedager >= 100
}

private fun hasLongSykefravar(tilfeller: Int, sykedager: Int): Boolean {
    return tilfeller >= 2 && sykedager >= 300
}

fun OppfolgingstilfellePerson.toKafkaOppfolgingstilfellePerson() = KafkaOppfolgingstilfellePerson(
    uuid = this.uuid.toString(),
    createdAt = this.createdAt,
    personIdentNumber = this.personIdentNumber.value,
    oppfolgingstilfelleList = this.oppfolgingstilfelleList.map { oppfolgingstilfelle ->
        KafkaOppfolgingstilfelle(
            gradertAtTilfelleEnd = oppfolgingstilfelle.gradertAtTilfelleEnd,
            arbeidstakerAtTilfelleEnd = oppfolgingstilfelle.arbeidstakerAtTilfelleEnd,
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            antallSykedager = oppfolgingstilfelle.antallSykedager,
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { virksomhetsnummer -> virksomhetsnummer.value },
        )
    },
    referanseTilfelleBitUuid = this.referanseTilfelleBitUuid.toString(),
    referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet,
    dodsdato = this.dodsdato,
)

fun Oppfolgingstilfelle.calculateCurrentVarighetUker(): Int {
    val currentVarighetDaysBrutto = ChronoUnit.DAYS.between(start, minOf(LocalDate.now(), end)) + 1
    val currentVarighetDays = if (antallSykedager == null) {
        currentVarighetDaysBrutto
    } else {
        val totalVarighetDays = ChronoUnit.DAYS.between(start, end) + 1
        val ikkeSykedager = totalVarighetDays - antallSykedager
        val sykedagerTilIDag = currentVarighetDaysBrutto - ikkeSykedager
        max(0, sykedagerTilIDag)
    }
    return currentVarighetDays.toInt() / DAYS_IN_WEEK
}
