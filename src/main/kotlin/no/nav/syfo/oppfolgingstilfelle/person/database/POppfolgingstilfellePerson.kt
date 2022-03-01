package no.nav.syfo.oppfolgingstilfelle.person.database

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.person.domain.Oppfolgingstilfelle
import no.nav.syfo.oppfolgingstilfelle.person.domain.OppfolgingstilfellePerson
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

fun POppfolgingstilfellePerson.toOppfolgingstilfellePerson() = OppfolgingstilfellePerson(
    uuid = this.uuid,
    createdAt = this.createdAt,
    personIdentNumber = this.personIdentNumber,
    oppfolgingstilfelleList = this.oppfolgingstilfeller,
    referanseTilfelleBitUuid = this.referanseTilfelleBitUUID,
    referanseTilfelleBitInntruffet = this.referanseTilfelleBitInntruffet,
)
