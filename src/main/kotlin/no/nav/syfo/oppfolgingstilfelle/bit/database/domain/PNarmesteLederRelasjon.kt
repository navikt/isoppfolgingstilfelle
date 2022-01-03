package no.nav.syfo.oppfolgingstilfelle.bit.database.domain

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.bit.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.Tag
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class POppfolgingstilfelleBit(
    val id: Int,
    val uuid: UUID,
    val personIdentNumber: PersonIdentNumber,
    val virksomhetsnummer: String? = null,
    val createdAt: OffsetDateTime,
    val inntruffet: OffsetDateTime,
    val tagList: List<Tag>,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
)
fun List<POppfolgingstilfelleBit>.toOppfolgingstilfelleBitList() = this.map { it.toOppfolgingstilfelleBit() }

fun POppfolgingstilfelleBit.toOppfolgingstilfelleBit() = OppfolgingstilfelleBit(
    uuid = this.uuid,
    personIdentNumber = this.personIdentNumber,
    virksomhetsnummer = this.virksomhetsnummer,
    createdAt = this.createdAt,
    inntruffet = this.inntruffet,
    tagList = this.tagList,
    ressursId = this.ressursId,
    fom = this.fom,
    tom = this.tom,
)
