package no.nav.syfo.oppfolgingstilfelle.person.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.person.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.person.kafka.KafkaOppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.person.kafka.KafkaOppfolgingstilfellePerson
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*

private const val DAYS_IN_WEEK = 7

data class OppfolgingstilfellePerson(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfelleList: List<Oppfolgingstilfelle>,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
    val dodsdato: LocalDate?,
)

data class Oppfolgingstilfelle(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val gradertAtTilfelleEnd: Boolean, // True hvis alle biter på siste dag i tilfellet er gradert
    val start: LocalDate,
    val end: LocalDate,
    val antallSykedager: Int?, // må tillate null siden tidligere persisterte oppfolgingstilfeller vil mangle dette feltet
    val virksomhetsnummerList: List<Virksomhetsnummer>,
)

fun List<Oppfolgingstilfelle>?.toOppfolgingstilfellePersonDTO(
    personIdent: PersonIdentNumber,
    dodsdato: LocalDate?,
) = OppfolgingstilfellePersonDTO(
    oppfolgingstilfelleList = this?.toOppfolgingstilfelleDTOList() ?: emptyList(),
    personIdent = personIdent.value,
    dodsdato = dodsdato,
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
        currentVarighetDaysBrutto - ikkeSykedager
    }
    return currentVarighetDays.toInt() / DAYS_IN_WEEK
}
