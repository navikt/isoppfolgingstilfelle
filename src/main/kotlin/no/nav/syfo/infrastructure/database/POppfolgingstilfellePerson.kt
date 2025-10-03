package no.nav.syfo.infrastructure.database

import no.nav.syfo.domain.Oppfolgingstilfelle
import no.nav.syfo.domain.OppfolgingstilfellePerson
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class POppfolgingstilfellePerson(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfeller: List<Oppfolgingstilfelle>,
    val referanseTilfelleBitUUID: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)

fun POppfolgingstilfellePerson.toOppfolgingstilfellePerson(
    dodsdato: LocalDate?,
) = OppfolgingstilfellePerson(
    uuid = this.uuid,
    createdAt = this.createdAt,
    personIdentNumber = this.personIdentNumber,
    oppfolgingstilfelleList = this.oppfolgingstilfeller,
    referanseTilfelleBitUuid = this.referanseTilfelleBitUUID,
    referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet,
    dodsdato = dodsdato,
)
