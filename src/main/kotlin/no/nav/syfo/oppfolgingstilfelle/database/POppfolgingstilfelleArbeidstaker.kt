package no.nav.syfo.oppfolgingstilfelle.database

import no.nav.syfo.domain.PersonIdentNumber
import no.nav.syfo.oppfolgingstilfelle.domain.Oppfolgingstilfelle
import java.time.OffsetDateTime
import java.util.*

data class POppfolgingstilfelleArbeidstaker(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val personIdentNumber: PersonIdentNumber,
    val oppfolgingstilfeller: List<Oppfolgingstilfelle>,
    val referanseTilfelleBitUUID: UUID,
    val referanseTilfelleBitInntruffet: OffsetDateTime,
)
