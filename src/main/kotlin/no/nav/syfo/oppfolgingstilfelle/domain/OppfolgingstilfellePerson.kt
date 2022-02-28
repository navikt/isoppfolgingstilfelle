package no.nav.syfo.oppfolgingstilfelle.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.domain.Virksomhetsnummer
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfelleDTO
import no.nav.syfo.oppfolgingstilfelle.api.domain.OppfolgingstilfellePersonDTO
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.kafka.KafkaOppfolgingstilfellePerson
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class OppfolgingstilfellePerson(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfelleList: List<Oppfolgingstilfelle>,
    val referanseTilfelleBitUuid: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

data class Oppfolgingstilfelle(
    val arbeidstakerAtTilfelleEnd: Boolean,
    val start: LocalDate,
    val end: LocalDate,
    val virksomhetsnummerList: List<Virksomhetsnummer>,
)

fun List<Oppfolgingstilfelle>?.toOppfolgingstilfellePersonDTO(
    personIdent: PersonIdentNumber,
) = OppfolgingstilfellePersonDTO(
    oppfolgingstilfelleList = this?.toOppfolgingstilfelleDTOList() ?: emptyList(),
    personIdent = personIdent.value,
)

private fun List<Oppfolgingstilfelle>.toOppfolgingstilfelleDTOList() =
    this.map { oppfolgingstilfelle ->
        OppfolgingstilfelleDTO(
            arbeidstakerAtTilfelleEnd = oppfolgingstilfelle.arbeidstakerAtTilfelleEnd,
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { it.value },
        )
    }

fun OppfolgingstilfellePerson.toKafkaOppfolgingstilfellePerson() = KafkaOppfolgingstilfellePerson(
    uuid = this.uuid.toString(),
    createdAt = this.createdAt,
    personIdentNumber = this.personIdentNumber.value,
    oppfolgingstilfelleList = this.oppfolgingstilfelleList.map { oppfolgingstilfelle ->
        KafkaOppfolgingstilfelle(
            arbeidstakerAtTilfelleEnd = oppfolgingstilfelle.arbeidstakerAtTilfelleEnd,
            start = oppfolgingstilfelle.start,
            end = oppfolgingstilfelle.end,
            virksomhetsnummerList = oppfolgingstilfelle.virksomhetsnummerList.map { virksomhetsnummer -> virksomhetsnummer.value },
        )
    },
    referanseTilfelleBitUuid = this.referanseTilfelleBitUuid.toString(),
    referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet,
)
