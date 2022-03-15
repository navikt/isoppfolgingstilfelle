package testhelper.generator

import no.nav.syfo.oppfolgingstilfelle.bit.domain.OppfolgingstilfelleBit
import no.nav.syfo.oppfolgingstilfelle.bit.domain.Tag
import no.nav.syfo.util.defaultZoneOffset
import testhelper.UserConstants.PERSONIDENTNUMBER_DEFAULT
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun generateOppfolgingstilfelleBit() = OppfolgingstilfelleBit(
    uuid = UUID.randomUUID(),
    personIdentNumber = PERSONIDENTNUMBER_DEFAULT,
    virksomhetsnummer = "987654321",
    createdAt = OffsetDateTime.now(defaultZoneOffset),
    inntruffet = OffsetDateTime.now(defaultZoneOffset).minusDays(1),
    fom = LocalDate.now().minusDays(1),
    tom = LocalDate.now().plusDays(1),
    tagList = listOf(
        Tag.SYKEPENGESOKNAD,
        Tag.SENDT,
    ),
    ressursId = UUID.randomUUID().toString(),
)
